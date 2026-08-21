"""
Admin review for officials stuck in the `pending_review` verification path
(see auth.py's register() and models.py's User.verification_status).

    GET  /api/admin/pending-officials        list accounts awaiting review
    POST /api/admin/officials/<id>/approve   sets is_verified=True
    POST /api/admin/officials/<id>/reject    marks rejected, stays unverified

Gated by `User.is_admin` (role == "admin") — a THIRD tier above official,
never self-registerable (no form anywhere sets role="admin"; see
seed_data.py's DEMO_ADMIN_ACCOUNT for the only way one exists in this
repo). A real deployment would seed/manage admin accounts the same way it
manages any other privileged internal tooling access — directly, not
through a public signup form.

**What this honestly is:** human review of a self-reported employee ID +
department + an uploaded photo of *something* the registrant claims is
their ID. The admin looking at it can visually sanity-check that a photo
was actually provided and looks plausible — this code does not verify the
document against any government database, run OCR, or check for forgery.
That's real KYC, and this repo has no path to a government identity API
to build that against. Say so plainly if asked — this is a stronger gate
than the shared-code path alone (a rejected reviewer catches an obviously
fake attempt a shared code can't), but it is still fundamentally a human
looking at a photo, not verified identity.
"""

from functools import wraps

from flask import Blueprint, jsonify
from flask_login import login_required, current_user

from extensions import db
from models import User

admin_bp = Blueprint("admin", __name__)


def _admin_required(fn):
    @wraps(fn)
    @login_required
    def wrapper(*args, **kwargs):
        if not current_user.is_admin:
            return jsonify({"error": "Admins only."}), 403
        return fn(*args, **kwargs)
    return wrapper


def _err(message, status=400):
    return jsonify({"error": message}), status


@admin_bp.get("/api/admin/pending-officials")
@_admin_required
def pending_officials():
    users = (
        User.query.filter_by(role="official", verification_status="pending_review")
        .order_by(User.created_at.asc())
        .all()
    )
    return jsonify([u.to_dict() for u in users])


@admin_bp.post("/api/admin/officials/<string:user_id>/approve")
@_admin_required
def approve_official(user_id):
    user = User.query.get(user_id)
    if not user or user.role != "official":
        return _err("Official account not found.", 404)

    user.is_verified = True
    user.verification_status = "approved"
    db.session.commit()
    return jsonify(user.to_dict())


@admin_bp.post("/api/admin/officials/<string:user_id>/reject")
@_admin_required
def reject_official(user_id):
    user = User.query.get(user_id)
    if not user or user.role != "official":
        return _err("Official account not found.", 404)

    user.is_verified = False
    user.verification_status = "rejected"
    db.session.commit()
    return jsonify(user.to_dict())
