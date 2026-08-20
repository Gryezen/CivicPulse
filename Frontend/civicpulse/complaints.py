"""
Complaints API.

    POST /api/complaints          file a new complaint (classified server-side)
    GET  /api/complaints/mine     the logged-in citizen's own complaints
    GET  /api/complaints          the public/admin queue — ?q=, ?category=, ?status=

All routes require login (this whole app is behind the account system —
see app.py's PROTECTED_PAGES). No docket-number lookup: complaints are
found by NLP-style keyword search on `q`, matching the "Complaints &
Policies" page's design.
"""

from flask import Blueprint, request, jsonify
from flask_login import login_required, current_user

from extensions import db
from models import Complaint, ClassificationLog, AutoResolutionLog
from classify import classify
from auto_resolve import attempt_auto_resolve
from search import extract_terms, score_complaint

complaints_bp = Blueprint("complaints", __name__)


def _err(message, status=400):
    return jsonify({"error": message}), status


@complaints_bp.post("/api/complaints")
@login_required
def create_complaint():
    data = request.get_json(silent=True) or {}
    title = (data.get("title") or "").strip()
    body = (data.get("body") or "").strip()
    language = data.get("language") or "English"
    files_count = int(data.get("files_count") or 0)

    if not title:
        return _err("Title is required.")
    if not body or len(body) < 10:
        return _err("Please describe the complaint in a bit more detail.")

    result = classify(title, body)

    note = "Filed — queued for AI triage. This usually takes under a minute."
    if result.get("needs_review"):
        note = "The classifier wasn't confident enough to auto-route this — a human officer will assign it shortly."
    elif result.get("corruption_flag"):
        note = "Flagged for the Vigilance / Anti-Corruption channel. Your identity is not shared with the named office."
    elif result.get("threat_flag"):
        note = "Flagged for urgent safety review — routed outside the standard SLA queue."

    complaint = Complaint(
        user_id=current_user.id,
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
        corruption_flag=bool(result.get("corruption_flag")),
        threat_flag=bool(result.get("threat_flag")),
        audit_tier=bool(result.get("audit_tier")),
        ai_brief=result.get("ai_brief"),
        stage="received",
        files_count=files_count,
        note=note,
    )
    db.session.add(complaint)
    db.session.flush()  # get complaint.id before the log row references it

    db.session.add(ClassificationLog(
        complaint_id=complaint.id,
        category=result["category"],
        department=result["department"],
        priority=result["priority"],
        confidence=result.get("confidence"),
        corruption_flag=bool(result.get("corruption_flag")),
        threat_flag=bool(result.get("threat_flag")),
        model_source=result.get("source", "rules"),
    ))

    # Self-resolution agent — see auto_resolve.py. Runs after the complaint
    # row exists (needs complaint.id for the log FK) but before commit, so
    # the auto-resolved stage/note land in the same transaction.
    acted, auto_note, log_fields = attempt_auto_resolve(
        complaint_id=complaint.id, title=title, body=body,
        category=result["category"], confidence=result.get("confidence"),
        corruption_flag=bool(result.get("corruption_flag")),
        threat_flag=bool(result.get("threat_flag")),
        audit_tier=bool(result.get("audit_tier")),
        needs_review=bool(result.get("needs_review")),
    )
    if acted:
        complaint.stage = "resolved"
        complaint.auto_resolved = True
        complaint.note = auto_note
    db.session.add(AutoResolutionLog(complaint_id=complaint.id, **log_fields))

    db.session.commit()

    return jsonify(complaint.to_dict()), 201


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
