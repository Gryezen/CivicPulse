"""
Database models.

One table for now: `users`. Complaints/dockets are still mocked in the
frontend (mockDockets / mockComplaints in the templates) — this only
covers the account/auth piece asked for. Add a Complaint model here,
FK'd to User.id, when that's ready to move off mock data.

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
