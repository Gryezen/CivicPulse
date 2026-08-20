"""
Officer dashboard API.

    GET  /api/officer/summary   counts for the top-of-page stat strip + broad-category breakdown
    GET  /api/officer/queue     the triage queue — audit-tier/threat/corruption first, paginated
    POST /api/officer/bulk      assign / escalate / resolve one or more complaints at once

All routes require login AND current_user.is_official — see
_official_required below. This is a prototype-grade role check (a real
deployment would gate this at SSO/department-directory level, see
models.User.role's own comment) but it's enforced server-side, not just
hidden in the frontend, same principle app.py already applies to
PROTECTED_PAGES.

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
        for stage in ("received", "processing", "assigned", "resolved")
    }
    needs_review = Complaint.query.filter_by(needs_review=True).count()
    corruption = Complaint.query.filter_by(corruption_flag=True).count()
    threat = Complaint.query.filter_by(threat_flag=True).count()
    audit_tier = Complaint.query.filter_by(audit_tier=True).count()
    auto_resolved = Complaint.query.filter_by(auto_resolved=True).count()
    unresolved = total - by_stage["resolved"]

    by_broad = {
        label: Complaint.query.filter_by(broad_category=label).count()
        for label in BROAD_CATEGORIES
    }

    return jsonify({
        "total": total,
        "byStage": by_stage,
        "unresolved": unresolved,
        "needsReview": needs_review,
        "corruptionFlag": corruption,
        "threatFlag": threat,
        "auditTier": audit_tier,
        "autoResolved": auto_resolved,
        # what the agent handled without a human, out of everything that's
        # not still sitting in "received" — a rough "load actually taken off
        # your queue today" number for the dashboard's headline stat.
        "autoResolvedShareOfHandled": (
            round(auto_resolved / max(1, total - by_stage["received"]), 3)
        ),
        "byBroadCategory": by_broad,
    })


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
            | (Complaint.needs_review.is_(True))
        )

    complaints = query.all()

    def triage_key(c):
        return (
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
            c.stage = "resolved"
            c.note = "Marked resolved by an officer."

    db.session.commit()
    return jsonify({"updated": len(complaints)})


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
