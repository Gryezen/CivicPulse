"""
Complaints API.

    POST /api/complaints                 file a new complaint (classified server-side;
                                          may split into multiple sub-issues — see splitting.py)
    GET  /api/complaints/mine            the logged-in citizen's own complaints
    GET  /api/complaints                 the public/admin queue — ?q=, ?category=, ?status=
    POST /api/complaints/<id>/dispute    citizen reopens a "resolved" complaint they say wasn't
                                          actually fixed — see DISPUTE_ESCALATION_THRESHOLD below

All routes require login (this whole app is behind the account system —
see app.py's PROTECTED_PAGES). No docket-number lookup: complaints are
found by NLP-style keyword search on `q`, matching the "Complaints &
Policies" page's design.

POST /api/complaints always returns the SAME shape citizens' code already
expects (a single complaint dict — see complaint.html's submit handler),
plus two additive fields that only matter when a submission got split:
`splitInto` (the OTHER complaints created alongside the one returned,
empty list if no split happened) and `wasSplit` (bool). The returned
complaint is always the first/primary sub-issue.
"""

from flask import Blueprint, request, jsonify
from flask_login import login_required, current_user

from extensions import db
from models import Complaint, ClassificationLog, AutoResolutionLog
from classify import classify
from auto_resolve import attempt_auto_resolve
from clustering import assign_cluster, check_filer_pattern
from splitting import split_complaint
from search import extract_terms, score_complaint
from uploads import save_upload, UploadError

complaints_bp = Blueprint("complaints", __name__)

DISPUTE_ESCALATION_THRESHOLD = 2  # reopen a "resolved" complaint this many times -> forced audit-tier escalation


def _err(message, status=400):
    return jsonify({"error": message}), status


def _file_one(title, body, language, files_count, unverified_allegation=False, user_id=None):
    """Classifies, clusters, self-resolution-checks, and persists ONE
    complaint row (plus its ClassificationLog/AutoResolutionLog rows).
    Shared by both the single-issue and the split-into-N-issues paths in
    create_complaint() below, so a bundled sub-issue gets exactly the same
    treatment a standalone complaint would.

    `user_id` defaults to the logged-in citizen (current_user.id) — pass it
    explicitly for a non-session caller, e.g. cpgrams_integration.py's
    ingest endpoint, which files complaints under a system bridge account
    rather than a browser session."""
    uid = user_id or current_user.id
    result = classify(title, body)

    note = "Filed — queued for AI triage. This usually takes under a minute."
    if unverified_allegation:
        note = (
            "This part of your submission reads as an unverified allegation about a named "
            "individual rather than a directly witnessed/factual complaint. It's held for "
            "independent review and will NOT be automatically attached to that person's official "
            "record — a factual, verifiable complaint gets acted on much faster."
        )
    elif result.get("wellbeing_risk"):
        # Deliberately calm, non-alarmist wording — this is a routing note
        # on a civic complaint, not a crisis-service response. See
        # classify.py's _detect_wellbeing_risk() for what this flag is and
        # isn't.
        note = (
            "Your complaint will be looked at, and a team member will also reach out directly "
            "given what you've shared here. If you need to talk to someone sooner, please contact "
            "a local helpline or emergency services."
        )
    elif result.get("needs_review"):
        note = "The classifier wasn't confident enough to auto-route this — a human officer will assign it shortly."
    elif result.get("corruption_flag"):
        note = "Flagged for the Vigilance / Anti-Corruption channel. Your identity is not shared with the named office."
    elif result.get("threat_flag"):
        note = "Flagged for urgent safety review — routed outside the standard SLA queue."

    complaint = Complaint(
        user_id=uid,
        title=title,
        body=body,
        language=language,
        category=result["category"],
        broad_category=result.get("broad_category", "General Governance"),
        department=result["department"],
        authority=result["authority"],
        priority=result["priority"],
        confidence=result.get("confidence"),
        needs_review=bool(result.get("needs_review")),
        corruption_flag=bool(result.get("corruption_flag")) and not unverified_allegation,
        threat_flag=bool(result.get("threat_flag")),
        audit_tier=bool(result.get("audit_tier")),
        wellbeing_risk=bool(result.get("wellbeing_risk")),
        ai_brief=result.get("ai_brief"),
        modeled_severity=result.get("modeled_severity"),
        stated_urgency=result.get("stated_urgency"),
        unverified_allegation=unverified_allegation,
        stage="received",
        files_count=files_count,
        note=note,
    )
    db.session.add(complaint)
    db.session.flush()  # get complaint.id before the log row / clustering reference it

    # Corroboration/duplicate/astroturf clustering (see clustering.py) —
    # skipped for unverified-allegation sub-issues: those are held for
    # review on their own merits, and "5 other people also made this exact
    # unverified allegation in the last 90 minutes" is itself closer to
    # the astroturf case than to genuine corroboration, so it shouldn't
    # get an automatic priority boost either way without a human looking.
    cluster_result = {
        "cluster_id": complaint.id, "corroboration_count": 1,
        "is_repeat_filing": False, "bump_cluster_ids": [], "suspected_coordinated": False,
    }
    if not unverified_allegation:
        cluster_result = assign_cluster(
            new_complaint_id=complaint.id, user_id=uid, title=title, body=body,
            department=complaint.department, filed_at=complaint.filed_at,
        )
    complaint.cluster_id = cluster_result["cluster_id"]
    complaint.corroboration_count = cluster_result["corroboration_count"]
    complaint.is_repeat_filing = cluster_result["is_repeat_filing"]
    complaint.suspected_coordinated = cluster_result["suspected_coordinated"]

    if cluster_result["suspected_coordinated"]:
        # Doc's explicit distinguishing case: near-identical phrasing +
        # compressed time window + volume. Held for human review instead
        # of the normal corroboration boost — priority is NOT raised on
        # this signal alone.
        complaint.needs_review = True
        complaint.note = (
            "This matches a burst of near-identical submissions in a short window — held for "
            "human review to confirm this is genuine grassroots corroboration rather than "
            "coordinated/templated submissions, before any priority escalation is applied. " + complaint.note
        )
    elif cluster_result["is_repeat_filing"]:
        complaint.note = (
            "This looks similar to a complaint you filed recently that's still open — linked as "
            "a repeat filing rather than a fresh ticket. " + complaint.note
        )
    elif cluster_result["corroboration_count"] > 1:
        complaint.note = (
            f"Matches {cluster_result['corroboration_count'] - 1} other open complaint(s) about "
            "the same issue — treated as corroborated and escalated. " + complaint.note
        )

    # Same-filer repeated-targeting pattern (ideation doc's "shopkeeper
    # files fake complaints against a rival every festival season" gaming
    # case) — independent of the cluster checks above, since this is about
    # this filer's OWN history in this category, not this one complaint's
    # similarity to others filed around the same time.
    filer_pattern = check_filer_pattern(user_id=uid, category=result["category"], exclude_complaint_id=complaint.id)
    complaint.suspected_targeting = filer_pattern["suspected_targeting"]
    if filer_pattern["suspected_targeting"]:
        complaint.needs_review = True
        complaint.note = (
            "This filer has a repeated pattern of uncorroborated complaints in this category — held for "
            "human review to rule out targeted/malicious filing before any action is taken. " + complaint.note
        )

    if cluster_result["bump_cluster_ids"] and not cluster_result["suspected_coordinated"]:
        # Keep corroboration_count in sync across every member of the
        # cluster, and give the whole cluster the same priority bump — a
        # corroborated issue should outrank an identical uncorroborated
        # one, on every ticket in the cluster, not just the newest.
        boost = min(20, (cluster_result["corroboration_count"] - 1) * 6)
        members = Complaint.query.filter(Complaint.id.in_(cluster_result["bump_cluster_ids"])).all()
        for m in members:
            m.corroboration_count = cluster_result["corroboration_count"]
            m.priority = min(99, m.priority + boost)
        complaint.priority = min(99, complaint.priority + boost)

    db.session.add(ClassificationLog(
        complaint_id=complaint.id,
        category=result["category"],
        department=result["department"],
        priority=result["priority"],
        confidence=result.get("confidence"),
        corruption_flag=bool(complaint.corruption_flag),
        threat_flag=bool(result.get("threat_flag")),
        model_source=result.get("source", "rules"),
    ))

    # Self-resolution agent — see auto_resolve.py. Skipped for corroborated/
    # repeat-filed/suspected-coordinated/unverified-allegation complaints —
    # all four have already been routed to a human's attention above, and
    # auto-closing any of them would defeat the point of surfacing them.
    skip_reasons = []
    if unverified_allegation:
        skip_reasons.append("unverified allegation")
    if complaint.wellbeing_risk:
        skip_reasons.append("wellbeing check-in flagged")
    if complaint.suspected_targeting:
        skip_reasons.append("suspected repeated-targeting pattern")
    if complaint.suspected_coordinated:
        skip_reasons.append("suspected coordinated submission")
    if complaint.corroboration_count > 1:
        skip_reasons.append("corroborated by other citizens")
    if complaint.is_repeat_filing:
        skip_reasons.append("repeat filing")

    if skip_reasons:
        acted, log_fields = False, {
            "action_taken": False,
            "reason": f"Skipped — {', '.join(skip_reasons)}; routed to a human instead.",
        }
    else:
        acted, auto_note, log_fields = attempt_auto_resolve(
            complaint_id=complaint.id, title=title, body=body,
            category=result["category"], confidence=result.get("confidence"),
            corruption_flag=bool(complaint.corruption_flag),
            threat_flag=bool(result.get("threat_flag")),
            audit_tier=bool(result.get("audit_tier")),
            needs_review=bool(complaint.needs_review),
        )
        if acted:
            complaint.stage = "resolved"
            complaint.auto_resolved = True
            complaint.note = auto_note
    db.session.add(AutoResolutionLog(complaint_id=complaint.id, **log_fields))

    return complaint


@complaints_bp.post("/api/complaints")
@login_required
def create_complaint():
    data = request.get_json(silent=True) or {}
    title = (data.get("title") or "").strip()
    body = (data.get("body") or "").strip()
    language = data.get("language") or "English"
    files_count = int(data.get("files_count") or 0)
    before_photo = data.get("before_photo")  # optional base64 data URL — see uploads.py

    if not title:
        return _err("Title is required.")
    if not body or len(body) < 10:
        return _err("Please describe the complaint in a bit more detail.")

    # Multi-issue splitting (see splitting.py) — bundled complaints and
    # factual-vs-unverified-allegation splitting both happen here, before
    # anything gets classified/persisted.
    sub_issues = split_complaint(title, body)

    created = []
    for issue in sub_issues:
        created.append(_file_one(
            title=title, body=issue["text"], language=language,
            files_count=files_count if not created else 0,  # attach file count to the first sub-issue only
            unverified_allegation=(issue["kind"] == "unverified_allegation"),
        ))

    if len(created) > 1:
        bundle_root_id = created[0].id
        for c in created:
            c.bundle_id = bundle_root_id

    # Before-photo (see uploads.py) attaches to the primary complaint only
    # — a single photo doesn't map cleanly onto N split sub-issues, and
    # the primary is what the citizen sees/tracks first.
    if before_photo:
        try:
            path, ahash = save_upload(created[0].id, before_photo, "before")
            created[0].before_photo_path = path
            created[0].before_photo_hash = ahash
        except UploadError as e:
            # Don't fail the whole filing over a bad photo — the complaint
            # itself is still valid and already classified/clustered above.
            created[0].note = (created[0].note or "") + f" (Photo not saved: {e})"

    db.session.commit()

    primary = created[0]
    payload = primary.to_dict()
    payload["wasSplit"] = len(created) > 1
    payload["splitInto"] = [c.to_dict() for c in created[1:]]
    return jsonify(payload), 201


@complaints_bp.post("/api/complaints/<string:complaint_id>/confirm")
@login_required
def confirm_complaint(complaint_id):
    """Two-party closure (ideation doc gap #6) — the CITIZEN side. An
    official asserting "resolved" only ever sets `pending_confirmation`
    (see officer.py's bulk 'resolve' action and resolve-with-photo
    endpoint); this is what actually moves a complaint to `stage ==
    "resolved"`. Nothing about the photo-hash similarity score gates this
    — it's shown to the citizen as context, but their own judgment is
    what closes the ticket, on purpose (see uploads.py's docstring for why
    an automated pixel-comparison should never be the sole gate here).
    """
    complaint = Complaint.query.get(complaint_id)
    if not complaint:
        return _err("Complaint not found.", 404)
    if complaint.user_id != current_user.id:
        return _err("You can only confirm your own complaints.", 403)
    if not complaint.pending_confirmation:
        return _err("This complaint isn't awaiting your confirmation.")

    from datetime import datetime, timezone
    complaint.stage = "resolved"
    complaint.pending_confirmation = False
    complaint.citizen_confirmed_at = datetime.now(timezone.utc)
    complaint.note = "Confirmed fixed by the citizen — closed."
    db.session.commit()
    return jsonify(complaint.to_dict())


@complaints_bp.post("/api/complaints/<string:complaint_id>/dispute")
@login_required
def dispute_complaint(complaint_id):
    """A citizen disputes a complaint an official marked/asserted resolved
    — 'nothing actually changed', or, from `pending_confirmation`, 'that
    after-photo doesn't actually show it fixed'. Ties to ideation doc gap
    #6 (breaking self-graded closure): rather than looping the same
    resolve/dispute cycle indefinitely, after DISPUTE_ESCALATION_THRESHOLD
    disputes the complaint is forced up a tier (audit_tier) so it lands in
    front of an official through the officer dashboard's highest-priority
    lane, not just re-queued at the same level that already failed it once.
    """
    complaint = Complaint.query.get(complaint_id)
    if not complaint:
        return _err("Complaint not found.", 404)
    if complaint.user_id != current_user.id:
        return _err("You can only dispute your own complaints.", 403)
    if complaint.stage != "resolved" and not complaint.pending_confirmation:
        return _err("Only a resolved (or pending-confirmation) complaint can be disputed.")

    complaint.dispute_count += 1
    complaint.stage = "processing"
    complaint.auto_resolved = False
    complaint.pending_confirmation = False

    if complaint.dispute_count >= DISPUTE_ESCALATION_THRESHOLD:
        complaint.audit_tier = True
        complaint.priority = max(complaint.priority, 90)
        complaint.note = (
            f"Reopened {complaint.dispute_count} time(s) after being marked resolved — "
            "escalated to priority review instead of being re-queued at the same level."
        )
    else:
        complaint.note = "Reopened by the citizen — marked as not actually resolved. Back in the active queue."

    db.session.commit()
    return jsonify(complaint.to_dict())


@complaints_bp.get("/api/complaints/mine")
@login_required
def my_complaints():
    complaints = (
        Complaint.query.filter_by(user_id=current_user.id)
        .order_by(Complaint.filed_at.desc())
        .all()
    )
    return jsonify([c.to_dict() for c in complaints])


@complaints_bp.get("/api/complaints")
@login_required
def queue():
    q = request.args.get("q", "").strip()
    category = request.args.get("category", "all")
    status = request.args.get("status", "all")
    sort = request.args.get("sort", "priority-desc")

    query = Complaint.query
    if category != "all":
        query = query.filter_by(category=category)
    if status != "all":
        query = query.filter_by(stage=status)

    complaints = query.all()

    scored = None
    if q:
        terms = extract_terms(q)
        scored = {c.id: score_complaint(terms, c) for c in complaints}
        complaints = [c for c in complaints if scored[c.id] > 0]

    def sort_key(c):
        primary = -scored[c.id] if scored else 0
        if sort == "newest":
            secondary = c.filed_at.timestamp() * -1 if c.filed_at else 0
        elif sort == "oldest":
            secondary = c.filed_at.timestamp() if c.filed_at else 0
        else:  # priority-desc
            secondary = -c.priority
        return (primary, secondary)

    complaints.sort(key=sort_key)

    max_score = max(scored.values()) if scored and complaints else None
    out = []
    for c in complaints:
        d = c.to_dict()
        if scored is not None:
            s = scored[c.id]
            ratio = (s / max_score) if max_score else 0
            d["matchLabel"] = "Strong match" if ratio >= 0.75 else ("Good match" if ratio >= 0.4 else "Partial match")
        out.append(d)

    return jsonify(out)
