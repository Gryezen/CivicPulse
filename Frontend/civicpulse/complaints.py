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
from models import Complaint
from classify import classify
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

    complaint = Complaint(
        user_id=current_user.id,
        title=title,
        body=body,
        language=language,
        category=result["category"],
        department=result["department"],
        authority=result["authority"],
        priority=result["priority"],
        stage="received",
        files_count=files_count,
        note="Filed — queued for AI triage. This usually takes under a minute.",
    )
    db.session.add(complaint)
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
