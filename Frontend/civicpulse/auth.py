"""
Auth + account API.

Session-based auth (Flask-Login's signed cookie) — no separate token to
manage on the frontend, `fetch(..., {credentials: 'same-origin'})` (the
default) just works. Endpoints:

    POST  /api/auth/register    { name, email, password, region, education,
                                   employed, occupation, language }
    POST  /api/auth/login       { email, password }
    POST  /api/auth/logout
    GET   /api/user/me
    PATCH /api/user/me          { any subset of the profile fields above,
                                   except password }
    POST  /api/user/me/password { current_password, new_password }

All responses are JSON. Errors are `{"error": "message"}` with a 4xx/5xx
status — the frontend's fetch wrappers (see main.js) surface `error` in a
toast.
"""

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

    if not name:
        return _err("Full name is required.")
    if not EMAIL_RE.match(email):
        return _err("Enter a valid email address.")
    if len(password) < 6:
        return _err("Password must be at least 6 characters.")
    if language not in VALID_LANGUAGES:
        return _err("Unsupported language.")
    if User.query.filter_by(email=email).first():
        return _err("An account with this email already exists.", 409)

    user = User(
        name=name, email=email, region=region, education=education,
        employed=employed, occupation=occupation, language=language,
    )
    user.set_password(password)
    db.session.add(user)
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
