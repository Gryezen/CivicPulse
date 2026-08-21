"""
Auth + account API.

Session-based auth (Flask-Login's signed cookie) — no separate token to
manage on the frontend, `fetch(..., {credentials: 'same-origin'})` (the
default) just works. Endpoints:

    POST  /api/auth/register    { name, email, password, region, education,
                                   employed, occupation, language, role,
                                   employee_id, department, verification_code,
                                   id_document }
    POST  /api/auth/login       { email, password }
    POST  /api/auth/logout
    GET   /api/user/me
    PATCH /api/user/me          { any subset of the profile fields above,
                                   plus `phone`, except password, role, employee_id, department }
    POST  /api/user/me/password { current_password, new_password }

All responses are JSON. Errors are `{"error": "message"}` with a 4xx/5xx
status — the frontend's fetch wrappers (see main.js) surface `error` in a
toast.

Officer self-registration: role="official" additionally requires
employee_id and department, plus ONE of:
  - a verification_code matching OFFICIAL_VERIFICATION_CODE (see app.py)
    — a shared code meant to be distributed to real officials out-of-band
    (department circular, onboarding email) — verifies instantly
    (verification_status="auto_verified")
  - an id_document (base64 image) — queued for an admin to manually
    review via admin.py (verification_status="pending_review";
    is_verified stays False, so /officer stays inaccessible, until an
    admin approves)
Neither path is real identity verification/KYC — say so if asked; see
admin.py's own docstring for exactly what an admin review does and
doesn't check. role/employee_id/department are deliberately NOT patchable
via PATCH /api/user/me — no upgrading a citizen account to official after
the fact through the account-settings form.
"""

import os
import re

from flask import Blueprint, request, jsonify
from flask_login import login_user, logout_user, login_required, current_user

from extensions import db
from models import User

auth_bp = Blueprint("auth", __name__)

EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
VALID_LANGUAGES = {
    "English", "Hindi", "Tamil", "Telugu", "Kannada", "Malayalam",
    "Bengali", "Marathi", "Gujarati", "Punjabi", "Urdu",
}


def _err(message, status=400):
    return jsonify({"error": message}), status


def _body():
    return request.get_json(silent=True) or {}


# ---------------------------------------------------------------- register
@auth_bp.post("/api/auth/register")
def register():
    data = _body()
    name = (data.get("name") or "").strip()
    email = (data.get("email") or "").strip().lower()
    password = data.get("password") or ""
    region = (data.get("region") or "").strip()
    education = (data.get("education") or "").strip()
    employed = bool(data.get("employed", True))
    occupation = (data.get("occupation") or "").strip()
    language = data.get("language") or "English"
    role = (data.get("role") or "citizen").strip().lower()

    if not name:
        return _err("Full name is required.")
    if not EMAIL_RE.match(email):
        return _err("Enter a valid email address.")
    if len(password) < 6:
        return _err("Password must be at least 6 characters.")
    if language not in VALID_LANGUAGES:
        return _err("Unsupported language.")
    if role not in ("citizen", "official"):
        return _err("Invalid account type.")
    if User.query.filter_by(email=email).first():
        return _err("An account with this email already exists.", 409)

    employee_id = ""
    department = ""
    is_verified = False
    verification_status = "none"
    id_document = data.get("id_document")  # optional base64 image — see uploads.py

    if role == "official":
        employee_id = (data.get("employee_id") or "").strip()
        department = (data.get("department") or "").strip()
        verification_code = (data.get("verification_code") or "").strip()
        expected_code = os.environ.get("OFFICIAL_VERIFICATION_CODE", "")

        if not employee_id:
            return _err("Employee ID is required for an official account.")
        if not department:
            return _err("Department is required for an official account.")

        code_matches = bool(expected_code) and verification_code.upper() == expected_code.upper()

        if code_matches:
            # Fast track: a correct shared department code verifies
            # instantly. Same prototype-grade caveat as before — this is
            # not government-database identity verification.
            is_verified = True
            verification_status = "auto_verified"
        elif id_document:
            # No/wrong code, but an ID document was attached — queue for
            # an admin to manually review and approve/reject (see
            # admin.py) instead of hard-rejecting the registration.
            is_verified = False
            verification_status = "pending_review"
        else:
            # Neither a valid code nor a document to review — nothing for
            # an admin to check, so this registration can't be queued.
            if not expected_code:
                return _err(
                    "Official registration needs either a department verification code (not configured on "
                    "this deployment) or an ID document for manual review. Attach a document to continue.", 403
                )
            return _err(
                "Invalid verification code. Either check the code your department issued you, or attach an "
                "ID document photo instead — an admin will manually review it.", 403
            )

    user = User(
        name=name, email=email, region=region, education=education,
        employed=employed, occupation=occupation, language=language,
        role=role, is_verified=is_verified, verification_status=verification_status,
        employee_id=employee_id or None, department=department or None,
    )
    user.set_password(password)
    db.session.add(user)
    db.session.flush()  # need user.id before saving an ID document under it

    if role == "official" and id_document:
        try:
            from uploads import save_upload
            path, _ = save_upload(f"user-{user.id}", id_document, "id-document")
            user.id_document_path = path
        except Exception:
            pass  # a failed document upload shouldn't block registration — the pending_review queue still needs a human either way

    db.session.commit()

    login_user(user)
    return jsonify(user.to_dict()), 201


# ------------------------------------------------------------------- login
@auth_bp.post("/api/auth/login")
def login():
    data = _body()
    email = (data.get("email") or "").strip().lower()
    password = data.get("password") or ""

    user = User.query.filter_by(email=email).first()
    if not user or not user.check_password(password):
        return _err("Incorrect email or password.", 401)

    login_user(user)
    return jsonify(user.to_dict())


# ------------------------------------------------------------------ logout
@auth_bp.post("/api/auth/logout")
@login_required
def logout():
    logout_user()
    return jsonify({"ok": True})


# --------------------------------------------------------------- get user
@auth_bp.get("/api/user/me")
@login_required
def me():
    return jsonify(current_user.to_dict())


# ------------------------------------------------------------- patch user
@auth_bp.patch("/api/user/me")
@login_required
def update_me():
    data = _body()

    if "name" in data:
        name = (data.get("name") or "").strip()
        if not name:
            return _err("Full name can't be empty.")
        current_user.name = name

    if "email" in data:
        email = (data.get("email") or "").strip().lower()
        if not EMAIL_RE.match(email):
            return _err("Enter a valid email address.")
        existing = User.query.filter_by(email=email).first()
        if existing and existing.id != current_user.id:
            return _err("Another account already uses this email.", 409)
        current_user.email = email

    if "region" in data:
        current_user.region = (data.get("region") or "").strip()
    if "education" in data:
        current_user.education = (data.get("education") or "").strip()
    if "employed" in data:
        current_user.employed = bool(data.get("employed"))
    if "occupation" in data:
        current_user.occupation = (data.get("occupation") or "").strip()
    if "language" in data:
        language = data.get("language")
        if language not in VALID_LANGUAGES:
            return _err("Unsupported language.")
        current_user.language = language

    if "phone" in data:
        phone = re.sub(r"[^\d+]", "", data.get("phone") or "")
        if phone and not re.match(r"^\+?\d{8,15}$", phone):
            return _err("Enter a valid phone number (8-15 digits, optional leading +).")
        if phone:
            existing = User.query.filter_by(phone=phone).first()
            if existing and existing.id != current_user.id:
                return _err("Another account already uses this phone number.", 409)
        current_user.phone = phone or None

    db.session.commit()
    return jsonify(current_user.to_dict())


# ---------------------------------------------------------- change password
@auth_bp.post("/api/user/me/password")
@login_required
def change_password():
    data = _body()
    current_password = data.get("current_password") or ""
    new_password = data.get("new_password") or ""

    if not current_user.check_password(current_password):
        return _err("Current password is incorrect.", 401)
    if len(new_password) < 6:
        return _err("New password must be at least 6 characters.")

    current_user.set_password(new_password)
    db.session.commit()
    return jsonify({"ok": True})
