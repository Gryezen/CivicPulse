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
    POST  /api/user/me/password           { current_password, new_password }
    POST  /api/user/me/account-type       { target_role, current_password, employee_id,
                                             department, verification_code, id_document }
    POST  /api/user/me/resend-verification {}

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
doesn't check. role/employee_id/department are NOT patchable via the
general-purpose PATCH /api/user/me — that only ever touches profile
fields. Changing account type is its own, deliberately narrower,
endpoint (below) — same verification inputs as registration, plus a
password re-check, since it's a privilege change rather than a profile
edit.

Account-type change (citizen <-> official): POST /api/user/me/account-type
needs `current_password` (re-auth — same idea as
POST /api/user/me/password) and `target_role` of "citizen" or "official".
Switching TO "official" requires employee_id + department, plus the same
EITHER verification_code OR id_document as register()'s official path,
and lands the account in the same auto_verified/pending_review split.
Switching back to "citizen" drops the officer-only fields and re-locks
/officer access. "admin" is never a valid target here — see admin.py's
module docstring for why that tier is DB/seed-only. An admin account
also can't self-demote through this endpoint, to avoid a self-lockout
with no other admin path in this prototype.

Pending-review resend: POST /api/user/me/resend-verification lets an
account stuck in verification_status="pending_review" re-signal an admin
without re-uploading anything — it just bumps
User.verification_requested_at, which admin.py's queue can sort/show
staleness by. Rate-limited to once per rolling 24h via that same
timestamp (set at initial registration too, so the first resend is only
available a day after registering, not immediately).
"""

import os
import re
from datetime import datetime, timedelta, timezone

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

RESEND_COOLDOWN = timedelta(days=1)


def _err(message, status=400):
    return jsonify({"error": message}), status


def _body():
    return request.get_json(silent=True) or {}


def _resolve_official_verification(data):
    """Shared by register() and account_type() — given a request body,
    apply the EITHER-a-correct-code-OR-an-id-document rule and return
    (is_verified, verification_status, error_response_or_None).

    Doesn't touch the DB or save the document; callers do that with the
    id_document payload themselves once they have a user row to attach it to.
    """
    verification_code = (data.get("verification_code") or "").strip()
    expected_code = os.environ.get("OFFICIAL_VERIFICATION_CODE", "")
    id_document = data.get("id_document")

    code_matches = bool(expected_code) and verification_code.upper() == expected_code.upper()

    if code_matches:
        return True, "auto_verified", None
    if id_document:
        return False, "pending_review", None

    if not expected_code:
        return None, None, _err(
            "Official verification needs either a department verification code (not configured on "
            "this deployment) or an ID document for manual review. Attach a document to continue.", 403
        )
    return None, None, _err(
        "Invalid verification code. Either check the code your department issued you, or attach an "
        "ID document photo instead — an admin will manually review it.", 403
    )


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
    verification_requested_at = None
    id_document = data.get("id_document")  # optional base64 image — see uploads.py

    if role == "official":
        employee_id = (data.get("employee_id") or "").strip()
        department = (data.get("department") or "").strip()

        if not employee_id:
            return _err("Employee ID is required for an official account.")
        if not department:
            return _err("Department is required for an official account.")

        is_verified, verification_status, err = _resolve_official_verification(data)
        if err:
            return err
        if verification_status == "pending_review":
            verification_requested_at = datetime.now(timezone.utc)

    user = User(
        name=name, email=email, region=region, education=education,
        employed=employed, occupation=occupation, language=language,
        role=role, is_verified=is_verified, verification_status=verification_status,
        employee_id=employee_id or None, department=department or None,
        verification_requested_at=verification_requested_at,
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


# ---------------------------------------------------------- account type
@auth_bp.post("/api/user/me/account-type")
@login_required
def change_account_type():
    """Switch between citizen and official. See module docstring."""
    data = _body()
    target_role = (data.get("target_role") or "").strip().lower()
    current_password = data.get("current_password") or ""

    if current_user.is_admin:
        return _err("Admin accounts can't change their own role here.", 403)
    if target_role not in ("citizen", "official"):
        return _err("Invalid account type.")
    if not current_user.check_password(current_password):
        return _err("Current password is incorrect.", 401)
    if target_role == current_user.role:
        if target_role == "citizen":
            return _err("This account is already a citizen account.")
        if current_user.is_verified:
            return _err("This account is already a verified official account.")
        # else: already "official" but not verified yet (pending_review or
        # rejected) — fall through and let them resubmit verification
        # details instead of forcing a citizen round-trip first.

    if target_role == "citizen":
        # Drop officer-only fields and re-lock /officer — see
        # models.py User.is_official, which reads role AND is_verified.
        current_user.role = "citizen"
        current_user.is_verified = False
        current_user.verification_status = "none"
        current_user.employee_id = None
        current_user.department = None
        current_user.id_document_path = None
        current_user.verification_requested_at = None
        db.session.commit()
        return jsonify(current_user.to_dict())

    # target_role == "official"
    employee_id = (data.get("employee_id") or "").strip()
    department = (data.get("department") or "").strip()
    if not employee_id:
        return _err("Employee ID is required for an official account.")
    if not department:
        return _err("Department is required for an official account.")

    is_verified, verification_status, err = _resolve_official_verification(data)
    if err:
        return err

    id_document = data.get("id_document")
    if verification_status == "pending_review" and id_document:
        try:
            from uploads import save_upload
            path, _ = save_upload(f"user-{current_user.id}", id_document, "id-document")
            current_user.id_document_path = path
        except Exception:
            pass  # same as register() — a failed upload shouldn't block the request; the pending queue still needs a human either way

    current_user.role = "official"
    current_user.employee_id = employee_id
    current_user.department = department
    current_user.is_verified = is_verified
    current_user.verification_status = verification_status
    current_user.verification_requested_at = (
        datetime.now(timezone.utc) if verification_status == "pending_review" else None
    )
    db.session.commit()
    return jsonify(current_user.to_dict())


# ----------------------------------------------------- resend verification
@auth_bp.post("/api/user/me/resend-verification")
@login_required
def resend_verification():
    if current_user.role != "official" or current_user.verification_status != "pending_review":
        return _err("No pending verification request on this account.", 400)

    now = datetime.now(timezone.utc)
    last = current_user.verification_requested_at
    if last is not None:
        if last.tzinfo is None:
            last = last.replace(tzinfo=timezone.utc)  # sqlite strips tzinfo on round-trip
        elapsed = now - last
        if elapsed < RESEND_COOLDOWN:
            wait = RESEND_COOLDOWN - elapsed
            hours = int(wait.total_seconds() // 3600)
            minutes = int((wait.total_seconds() % 3600) // 60)
            when = f"{hours}h {minutes}m" if hours else f"{minutes}m"
            return _err(f"You can resend a verification request once per day. Try again in {when}.", 429)

    current_user.verification_requested_at = now
    db.session.commit()
    return jsonify(current_user.to_dict())
