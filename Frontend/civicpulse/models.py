"""
Database models.

    users                users               accounts (see auth.py)
    complaints           complaints          filed grievances, FK'd to users.id (see complaints.py)
    classification_logs  ClassificationLog   audit trail of every classify() decision
    auto_resolution_logs AutoResolutionLog   audit trail of every self-resolution agent decision
    policies             Policy              civic schemes, seeded from policies_data.json (see policy_engine.py)

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

    # "citizen" (default) or "official" — gates /officer (see officer.py).
    # No self-service upgrade path in this prototype: officials are seeded
    # or promoted directly in the DB, same as a real deployment would use
    # SSO/department directory membership rather than a signup checkbox.
    role = db.Column(db.String(20), nullable=False, default="citizen")

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

    @property
    def is_official(self):
        return self.role == "official"

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
            "role": self.role or "citizen",
        }


class Complaint(db.Model):
    __tablename__ = "complaints"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    user_id = db.Column(db.String(36), db.ForeignKey("users.id"), nullable=False, index=True)

    title = db.Column(db.String(300), nullable=False)
    body = db.Column(db.Text, nullable=False)
    language = db.Column(db.String(30), nullable=False, default="English")

    # set by classify.py at creation time — see classify.py's own docstring
    # for what actually produces these now (trained model, with a keyword
    # fallback if the model bundle is missing).
    category = db.Column(db.String(80), nullable=False)
    broad_category = db.Column(db.String(60), nullable=False, default="General Governance")  # see taxonomy.py
    department = db.Column(db.String(160), nullable=False)
    authority = db.Column(db.String(160), nullable=False)
    priority = db.Column(db.Integer, nullable=False)  # 0-100

    # New in the classify.py v2 pass — see ClassificationLog for the full
    # per-decision audit trail; these columns are just the latest value,
    # cheap to read for badges in the queue/track UI.
    confidence = db.Column(db.Float, nullable=True)  # 0-1, category model's own confidence
    needs_review = db.Column(db.Boolean, nullable=False, default=False)  # confidence below threshold
    corruption_flag = db.Column(db.Boolean, nullable=False, default=False)
    threat_flag = db.Column(db.Boolean, nullable=False, default=False)
    # "audit tier" — a case where the underlying incident is already over
    # (a death, a completed crime) and needs a fatality/incident-review
    # workflow instead of the normal fix-it SLA pipeline. See classify.py's
    # _detect_audit_tier() and the ideation doc's "accident response delay,
    # fatality" example.
    audit_tier = db.Column(db.Boolean, nullable=False, default=False)

    # Self-resolution agent (see auto_resolve.py) — set when the agent
    # auto-dispatched a routine action instead of leaving this for an
    # officer. ai_brief is a one-line, always-present scan summary for the
    # officer queue (see classify.py's build_brief()) — independent of
    # auto-resolution, every complaint gets one.
    auto_resolved = db.Column(db.Boolean, nullable=False, default=False)
    ai_brief = db.Column(db.String(240), nullable=True)

    assigned_officer = db.Column(db.String(160), nullable=True)  # free-text for this prototype; see officer.py

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
            "broadCategory": self.broad_category,
            "department": self.department,
            "authority": self.authority,
            "priority": self.priority,
            "confidence": round(self.confidence, 3) if self.confidence is not None else None,
            "needsReview": bool(self.needs_review),
            "corruptionFlag": bool(self.corruption_flag),
            "threatFlag": bool(self.threat_flag),
            "auditTier": bool(self.audit_tier),
            "autoResolved": bool(self.auto_resolved),
            "aiBrief": self.ai_brief or "",
            "assignedOfficer": self.assigned_officer or "",
            "stage": self.stage,
            "files": self.files_count,
            "note": self.note or "",
            "filed": self.filed_at.strftime("%Y-%m-%d") if self.filed_at else "",
            "filedDisplay": self.filed_at.strftime("%d %b %Y") if self.filed_at else "",
        }


class ClassificationLog(db.Model):
    """Audit trail: one row per classification decision made by classify().

    Covers the doc's explainability/officer-override questions cheaply —
    every automated decision is timestamped and has its own confidence
    score, independent of whatever the complaint's *current* fields say
    (which an officer may have since overridden).
    """
    __tablename__ = "classification_logs"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    complaint_id = db.Column(db.String(36), db.ForeignKey("complaints.id"), nullable=False, index=True)

    category = db.Column(db.String(80), nullable=False)
    department = db.Column(db.String(160), nullable=False)
    priority = db.Column(db.Integer, nullable=False)
    confidence = db.Column(db.Float, nullable=True)
    corruption_flag = db.Column(db.Boolean, nullable=False, default=False)
    threat_flag = db.Column(db.Boolean, nullable=False, default=False)
    model_source = db.Column(db.String(20), nullable=False, default="rules")  # "model" or "rules" (fallback)

    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))

    def to_dict(self):
        return {
            "id": self.id,
            "complaintId": self.complaint_id,
            "category": self.category,
            "department": self.department,
            "priority": self.priority,
            "confidence": round(self.confidence, 3) if self.confidence is not None else None,
            "corruptionFlag": bool(self.corruption_flag),
            "threatFlag": bool(self.threat_flag),
            "modelSource": self.model_source,
            "createdAt": self.created_at.isoformat() if self.created_at else "",
        }


class AutoResolutionLog(db.Model):
    """Audit trail for the self-resolution agent (see auto_resolve.py).

    One row per auto-resolution attempt — including ones the agent
    declined to act on — so an official/auditor can see not just "this was
    auto-resolved" but "the agent considered auto-resolving this and chose
    not to, because X". That "chose not to" row is what makes the feature
    defensible rather than a black box (same reasoning as ClassificationLog).
    """
    __tablename__ = "auto_resolution_logs"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    complaint_id = db.Column(db.String(36), db.ForeignKey("complaints.id"), nullable=False, index=True)

    action_taken = db.Column(db.Boolean, nullable=False, default=False)
    reason = db.Column(db.Text, nullable=False)  # human-readable explanation either way
    matched_complaint_id = db.Column(db.String(36), nullable=True)  # closest past-resolved match, if any
    similarity = db.Column(db.Float, nullable=True)  # cosine similarity to that match, 0-1

    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))

    def to_dict(self):
        return {
            "id": self.id,
            "complaintId": self.complaint_id,
            "actionTaken": bool(self.action_taken),
            "reason": self.reason,
            "matchedComplaintId": self.matched_complaint_id,
            "similarity": round(self.similarity, 3) if self.similarity is not None else None,
            "createdAt": self.created_at.isoformat() if self.created_at else "",
        }


class Policy(db.Model):
    """Civic scheme / policy — now a real table instead of policies_data.json.

    policies_data.json is kept in the repo as the seed source (see
    policy_engine.seed_policies_if_empty) — easiest way for a non-engineer
    teammate to add a policy is still editing that JSON file and restarting,
    it just lands in Postgres now instead of being read fresh off disk on
    every request.
    """
    __tablename__ = "policies"

    slug = db.Column(db.String(120), primary_key=True)
    title = db.Column(db.String(200), nullable=False)
    source = db.Column(db.String(80), nullable=True)
    category = db.Column(db.String(80), nullable=False)
    summary = db.Column(db.Text, nullable=False)
    keywords = db.Column(db.JSON, nullable=False, default=list)
    eligibility = db.Column(db.Text, nullable=True)
    roadmap = db.Column(db.JSON, nullable=False, default=list)  # list of {phase, detail, status}

    def to_dict(self):
        return {
            "slug": self.slug,
            "title": self.title,
            "source": self.source or "",
            "category": self.category,
            "summary": self.summary,
            "keywords": self.keywords or [],
            "eligibility": self.eligibility or "",
            "roadmap": self.roadmap or [],
        }


