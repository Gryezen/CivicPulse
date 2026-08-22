"""
Officer dashboard API.

    GET  /api/officer/summary                            counts for the top-of-page stat strip + broad-category breakdown
    GET  /api/officer/queue                               the triage queue — audit-tier/threat/corruption first, paginated
    POST /api/officer/bulk                                assign / escalate / resolve one or more complaints at once
    POST /api/officer/complaints/<id>/resolve-with-photo   resolve with after-photo evidence — see uploads.py
    GET  /api/officer/complaints/<id>/trail                classification + auto-resolution audit trail
    POST /api/officer/policies/sync                        trigger policy_ingest.py — see that file's docstring

All routes require login AND current_user.is_official — see
_official_required below. This is a prototype-grade role check (a real
deployment would gate this at SSO/department-directory level, see
models.User's own comments on `role`/`is_verified`) but it's enforced
server-side, not just hidden in the frontend, same principle app.py
already applies to PROTECTED_PAGES.

Note that the bulk 'resolve' action and resolve-with-photo do NOT set
`stage == "resolved"` directly — they set `pending_confirmation`, and only
the citizen's own confirm/dispute (see complaints.py) actually closes or
reopens the ticket. This is the two-party closure mechanism from the
ideation doc's gap #6 — see complaints.py's /confirm docstring for why an
officer's assertion alone was deliberately not made sufficient.

Why a separate blueprint from complaints.py: the citizen-facing queue
(GET /api/complaints) is deliberately unauthenticated-as-to-role — any
logged-in citizen can see the public queue, filtered/sorted for browsing.
This one assumes the reader's job is to clear volume, not browse, so the
default sort/shape is different (triage order, not priority order) and it
exposes bulk mutation, which citizens should never get.
"""

from functools import wraps

from flask import Blueprint, request, jsonify
from flask_login import login_required, current_user

from extensions import db
from models import Complaint, ClassificationLog, AutoResolutionLog
from taxonomy import BROAD_CATEGORIES

officer_bp = Blueprint("officer", __name__)

PAGE_SIZE_DEFAULT = 50
PAGE_SIZE_MAX = 200


def _official_required(fn):
    @wraps(fn)
    @login_required
    def wrapper(*args, **kwargs):
        if not current_user.is_official:
            return jsonify({"error": "Officials only."}), 403
        return fn(*args, **kwargs)
    return wrapper


def _err(message, status=400):
    return jsonify({"error": message}), status


@officer_bp.get("/api/officer/summary")
@_official_required
def summary():
    total = Complaint.query.count()
    by_stage = {
        stage: Complaint.query.filter_by(stage=stage).count()
        for stage in ("received", "processing", "assigned", "pending_confirmation", "resolved")
    }
    needs_review = Complaint.query.filter_by(needs_review=True).count()
    corruption = Complaint.query.filter_by(corruption_flag=True).count()
    threat = Complaint.query.filter_by(threat_flag=True).count()
    audit_tier = Complaint.query.filter_by(audit_tier=True).count()
    wellbeing_risk = Complaint.query.filter_by(wellbeing_risk=True).count()
    auto_resolved = Complaint.query.filter_by(auto_resolved=True).count()
    unresolved = total - by_stage["resolved"]

    by_broad = {
        label: Complaint.query.filter_by(broad_category=label).count()
        for label in BROAD_CATEGORIES
    }

    systemic_alerts = _compute_systemic_alerts()

    return jsonify({
        "total": total,
        "byStage": by_stage,
        "unresolved": unresolved,
        "needsReview": needs_review,
        "corruptionFlag": corruption,
        "threatFlag": threat,
        "auditTier": audit_tier,
        "wellbeingRisk": wellbeing_risk,
        "autoResolved": auto_resolved,
        # what the agent handled without a human, out of everything that's
        # not still sitting in "received" — a rough "load actually taken off
        # your queue today" number for the dashboard's headline stat.
        "autoResolvedShareOfHandled": (
            round(auto_resolved / max(1, total - by_stage["received"]), 3)
        ),
        "byBroadCategory": by_broad,
        "systemicAlerts": systemic_alerts,
    })


def _compute_systemic_alerts(recent_days=30, baseline_days=90, min_recent=5, deviation_ratio=1.5):
    """Cross-grievance pattern memory (ideation doc gap #7): a department
    getting a lot MORE complaints in the last `recent_days` than its own
    recent-history average suggests a systemic problem, not independent
    unlucky incidents — worth an official's attention even if no single
    complaint in the spike looks unusual on its own.

    Deliberately simple (a department's own trailing average, not a
    cross-department statistical model) — same honest-simplification
    posture as the rest of this codebase. Returns departments whose last
    `recent_days` count is at least `deviation_ratio`x their per-`recent_days`-
    window average over the preceding `baseline_days`, with at least
    `min_recent` complaints so a department going from 1 to 2 doesn't
    trigger a false alarm.
    """
    from datetime import datetime, timedelta, timezone
    from sqlalchemy import func

    now = datetime.now(timezone.utc)
    recent_start = now - timedelta(days=recent_days)
    baseline_start = now - timedelta(days=baseline_days)

    recent_counts = dict(
        db.session.query(Complaint.department, func.count(Complaint.id))
        .filter(Complaint.filed_at >= recent_start)
        .group_by(Complaint.department).all()
    )
    baseline_counts = dict(
        db.session.query(Complaint.department, func.count(Complaint.id))
        .filter(Complaint.filed_at >= baseline_start, Complaint.filed_at < recent_start)
        .group_by(Complaint.department).all()
    )

    baseline_windows = max(1, (baseline_days - recent_days) / recent_days)

    alerts = []
    for dept, recent_n in recent_counts.items():
        if recent_n < min_recent:
            continue
        baseline_avg = (baseline_counts.get(dept, 0) / baseline_windows) or 0.5  # avoid div-by-zero, treat "no history" as a low baseline
        ratio = recent_n / baseline_avg
        if ratio >= deviation_ratio:
            alerts.append({
                "department": dept,
                "recentCount": recent_n,
                "baselineAverage": round(baseline_avg, 1),
                "deviationRatio": round(ratio, 2),
            })

    alerts.sort(key=lambda a: a["deviationRatio"], reverse=True)
    return alerts[:10]


@officer_bp.get("/api/officer/queue")
@_official_required
def queue():
    """Triage-ordered queue: audit-tier and threat-flagged cases first
    (these bypass normal SLA entirely), then corruption-flagged, then
    everything else by priority. Auto-resolved items are excluded by
    default — an official's queue should be what still needs *them*,
    not a log of what the agent already closed (that's a separate tab
    the frontend can request with include_auto_resolved=1)."""
    broad_category = request.args.get("broad_category", "all")
    status = request.args.get("status", "all")
    only_flagged = request.args.get("only_flagged", "") == "1"
    include_auto_resolved = request.args.get("include_auto_resolved", "") == "1"
    page = max(1, int(request.args.get("page", 1) or 1))
    page_size = min(PAGE_SIZE_MAX, max(1, int(request.args.get("page_size", PAGE_SIZE_DEFAULT) or PAGE_SIZE_DEFAULT)))

    query = Complaint.query
    if broad_category != "all":
        query = query.filter_by(broad_category=broad_category)
    if status != "all":
        query = query.filter_by(stage=status)
    if not include_auto_resolved:
        query = query.filter_by(auto_resolved=False)
    if only_flagged:
        query = query.filter(
            (Complaint.corruption_flag.is_(True))
            | (Complaint.threat_flag.is_(True))
            | (Complaint.audit_tier.is_(True))
            | (Complaint.wellbeing_risk.is_(True))
            | (Complaint.needs_review.is_(True))
        )

    complaints = query.all()

    def triage_key(c):
        return (
            0 if c.wellbeing_risk else 1,
            0 if c.audit_tier else 1,
            0 if c.threat_flag else 1,
            0 if c.corruption_flag else 1,
            0 if c.needs_review else 1,
            -c.priority,
            -c.filed_at.timestamp() if c.filed_at else 0,
        )

    complaints.sort(key=triage_key)
    total = len(complaints)
    start = (page - 1) * page_size
    page_items = complaints[start:start + page_size]

    return jsonify({
        "total": total,
        "page": page,
        "pageSize": page_size,
        "items": [c.to_dict() for c in page_items],
    })


@officer_bp.post("/api/officer/bulk")
@_official_required
def bulk_action():
    """Body: {"ids": [...], "action": "assign"|"escalate"|"resolve", "officer": "..." (for assign)}"""
    data = request.get_json(silent=True) or {}
    ids = data.get("ids") or []
    action = data.get("action")
    if not ids or not isinstance(ids, list):
        return _err("ids must be a non-empty list.")
    if action not in ("assign", "escalate", "resolve"):
        return _err("action must be one of: assign, escalate, resolve.")

    complaints = Complaint.query.filter(Complaint.id.in_(ids)).all()
    if not complaints:
        return _err("No matching complaints.", 404)

    for c in complaints:
        if action == "assign":
            officer_name = (data.get("officer") or current_user.name or "").strip()
            c.assigned_officer = officer_name or c.assigned_officer
            c.stage = "assigned"
        elif action == "escalate":
            c.priority = min(99, c.priority + 15)
            c.note = (c.note + " " if c.note else "") + "[Escalated by an officer.]"
        elif action == "resolve":
            # Two-party closure (ideation doc gap #6) — an official
            # asserting "resolved" doesn't itself close the ticket; it
            # sets pending_confirmation and waits on the CITIZEN's own
            # confirm/dispute (see complaints.py's /confirm, /dispute).
            # See resolve_with_photo() below for the photo-evidence
            # variant of this same transition.
            c.stage = "pending_confirmation"
            c.pending_confirmation = True
            c.note = "An officer marked this resolved — awaiting the citizen's confirmation before final closure."

    db.session.commit()
    return jsonify({"updated": len(complaints)})


@officer_bp.post("/api/officer/complaints/<string:complaint_id>/resolve-with-photo")
@_official_required
def resolve_with_photo(complaint_id):
    """Same transition as the bulk 'resolve' action, but attaches an
    after-photo and computes a lightweight similarity score against the
    complaint's before-photo (if one exists) — see uploads.py's docstring
    for exactly what that score does and doesn't mean. Body:
    {"after_photo": "data:image/jpeg;base64,..."}"""
    from uploads import save_upload, similarity_from_hashes, UploadError

    complaint = Complaint.query.get(complaint_id)
    if not complaint:
        return _err("Complaint not found.", 404)

    data = request.get_json(silent=True) or {}
    after_photo = data.get("after_photo")
    if not after_photo:
        return _err("after_photo (base64 data URL) is required — use the bulk 'resolve' action if you have no photo.")

    try:
        path, ahash = save_upload(complaint.id, after_photo, "after")
    except UploadError as e:
        return _err(str(e))

    complaint.after_photo_path = path
    complaint.after_photo_hash = ahash
    if complaint.before_photo_hash is not None:
        complaint.photo_similarity = similarity_from_hashes(complaint.before_photo_hash, ahash)

    complaint.stage = "pending_confirmation"
    complaint.pending_confirmation = True
    similarity_note = ""
    if complaint.photo_similarity is not None and complaint.photo_similarity > 0.92:
        similarity_note = (
            " Note: the after-photo looks very similar to the before-photo (low visual change detected) — "
            "worth double-checking before the citizen confirms."
        )
    complaint.note = (
        "An officer marked this resolved with photo evidence — awaiting the citizen's confirmation "
        "before final closure." + similarity_note
    )

    db.session.commit()
    return jsonify(complaint.to_dict())


@officer_bp.post("/api/officer/policies/sync")
@_official_required
def sync_policies():
    """Manually trigger a policy-table refresh (see policy_ingest.py).
    Body: {"source": "https://... or a server-local file path"} — if
    omitted, falls back to the POLICY_SOURCE_URL env var, and if that's
    also unset, fails with a clear 400 rather than silently no-op'ing.
    Untested against a live URL source in this sandbox — see
    policy_ingest.py's module docstring."""
    import os
    from policy_ingest import run_sync

    data = request.get_json(silent=True) or {}
    source = data.get("source") or os.environ.get("POLICY_SOURCE_URL")
    if not source:
        return _err("No source provided and POLICY_SOURCE_URL is not set.")

    try:
        result = run_sync(source)
    except Exception as e:
        return _err(f"Sync failed: {e}", 502)

    return jsonify(result)


@officer_bp.get("/api/officer/complaints/<string:complaint_id>/trail")
@_official_required
def audit_trail(complaint_id):
    """Full explainability trail for one complaint — every classification
    decision plus every auto-resolution consideration, newest first."""
    classification_logs = (
        ClassificationLog.query.filter_by(complaint_id=complaint_id)
        .order_by(ClassificationLog.created_at.desc()).all()
    )
    resolution_logs = (
        AutoResolutionLog.query.filter_by(complaint_id=complaint_id)
        .order_by(AutoResolutionLog.created_at.desc()).all()
    )
    return jsonify({
        "classificationLogs": [c.to_dict() for c in classification_logs],
        "autoResolutionLogs": [r.to_dict() for r in resolution_logs],
    })
