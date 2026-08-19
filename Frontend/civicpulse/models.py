"""
Database models.

    users       — accounts (see auth.py)
    complaints  — filed grievances, FK'd to users.id (see complaints.py)

Policies are NOT a DB table — they live in policies_data.json and are
served through policy_engine.py (the PolicyGyaan bridge), rendered into
templates via Jinja. See app.py's dashboard()/track()/policy_detail().

Uses a plain string UUID as the primary key (not Postgres' native UUID
type) so the same model works against Supabase's Postgres in production
and a local sqlite file in dev — see app.py's DATABASE_URL fallback.
"""

import uuid
from datetime import datetime, timezone

from flask_login import UserMixin
from werkzeug.security import generate_password_hash, check_password_hash

from extensions import db


def _uuid():
    return str(uuid.uuid4())


class User(UserMixin, db.Model):
    __tablename__ = "users"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)

    # collected at registration (see the register form in login.html)
    name = db.Column(db.String(120), nullable=False)
    email = db.Column(db.String(255), nullable=False, unique=True, index=True)
    password_hash = db.Column(db.String(255), nullable=False)
    region = db.Column(db.String(160), nullable=True)
    education = db.Column(db.String(60), nullable=True)
    employed = db.Column(db.Boolean, nullable=False, default=True)
    occupation = db.Column(db.String(160), nullable=True)
    language = db.Column(db.String(30), nullable=False, default="English")

    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))

    complaints = db.relationship("Complaint", backref="user", lazy="dynamic")

    # --- password helpers -------------------------------------------------
    def set_password(self, raw_password):
        self.password_hash = generate_password_hash(raw_password)

    def check_password(self, raw_password):
        return check_password_hash(self.password_hash, raw_password)

    # --- flask-login ---------------------------------------------------
    def get_id(self):
        return self.id

    # --- serialization ---------------------------------------------------
    def to_dict(self):
        """Shape returned to the frontend — mirrors main.js's `getAccount()` object."""
        return {
            "id": self.id,
            "name": self.name or "",
            "email": self.email or "",
            "region": self.region or "",
            "education": self.education or "",
            "employed": bool(self.employed),
            "occupation": self.occupation or "",
            "language": self.language or "English",
        }


class Complaint(db.Model):
    __tablename__ = "complaints"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    user_id = db.Column(db.String(36), db.ForeignKey("users.id"), nullable=False, index=True)

    title = db.Column(db.String(300), nullable=False)
    body = db.Column(db.Text, nullable=False)
    language = db.Column(db.String(30), nullable=False, default="English")

    # set by classify.py at creation time — stands in for the real NLP model
    category = db.Column(db.String(80), nullable=False)
    department = db.Column(db.String(160), nullable=False)
    authority = db.Column(db.String(160), nullable=False)
    priority = db.Column(db.Integer, nullable=False)  # 0-100

    stage = db.Column(db.String(20), nullable=False, default="received")  # received/processing/assigned/resolved
    files_count = db.Column(db.Integer, nullable=False, default=0)
    note = db.Column(db.Text, nullable=True)  # "next update" note shown in the queue detail

    filed_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))

    def to_dict(self):
        return {
            "id": self.id,
            "title": self.title,
            "body": self.body,
            "language": self.language,
            "category": self.category,
            "department": self.department,
            "authority": self.authority,
            "priority": self.priority,
            "stage": self.stage,
            "files": self.files_count,
            "note": self.note or "",
            "filed": self.filed_at.strftime("%Y-%m-%d") if self.filed_at else "",
            "filedDisplay": self.filed_at.strftime("%d %b %Y") if self.filed_at else "",
        }


