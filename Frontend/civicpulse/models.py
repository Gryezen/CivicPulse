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
    # Self-registration as "official" requires a correct verification code
    # (see auth.py's register() and OFFICIAL_VERIFICATION_CODE in app.py) —
    # this is a prototype-grade gate (a shared code distributed to a
    # department, not real identity verification/KYC), disclosed as such in
    # the register form and the README. is_verified is what officer.py's
    # role check actually gates on, kept separate from `role` so a future
    # manual-approval workflow (an admin flips is_verified after checking
    # documents) doesn't require changing what "role" means.
    role = db.Column(db.String(20), nullable=False, default="citizen")
    is_verified = db.Column(db.Boolean, nullable=False, default=False)
    employee_id = db.Column(db.String(80), nullable=True)
    department = db.Column(db.String(160), nullable=True)

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
        # Both conditions matter: role alone is not enough to reach
        # /officer or any /api/officer/* route — see officer.py's
        # _official_required, which reads this property, not `role`
        # directly, specifically so a code fix here (e.g. a future manual-
        # approval workflow) doesn't require touching every call site.
        return self.role == "official" and self.is_verified

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
            "isVerified": bool(self.is_verified),
            "isOfficial": self.is_official,
            "employeeId": self.employee_id or "",
            "department": self.department or "",
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

    # Corroboration/duplicate clustering (see clustering.py). cluster_id
    # points at the "root" complaint's own id (self-referential, no FK
    # constraint — keeps this cheap to backfill/migrate). A complaint with
    # cluster_id == its own id is either unclustered or the root of a
    # cluster with corroboration_count > 1.
    cluster_id = db.Column(db.String(36), nullable=True, index=True)
    corroboration_count = db.Column(db.Integer, nullable=False, default=1)  # distinct citizens in this cluster
    is_repeat_filing = db.Column(db.Boolean, nullable=False, default=False)  # same citizen re-filing a similar open complaint
    # Near-identical phrasing + compressed time window + volume — see
    # clustering.py's _detect_astroturf(). Held for human review instead
    # of getting the normal corroboration priority boost; NOT auto-
    # dismissed, since a false positive here just means a human looks at
    # something that turns out to be genuine — the safe failure direction.
    suspected_coordinated = db.Column(db.Boolean, nullable=False, default=False)

    # Severity (topic/signal-driven) vs stated urgency (how urgently *this*
    # complainant wrote it) — see classify.py's _score_urgency(). priority
    # is a blend of both, but keeping them separate lets an officer see
    # when someone's tone is far more alarmed than the actual topic
    # warrants, or vice versa.
    modeled_severity = db.Column(db.Integer, nullable=True)
    stated_urgency = db.Column(db.Integer, nullable=True)

    # Multi-issue splitting (see splitting.py). bundle_id is self-
    # referential (points at the first sub-issue's own id), same pattern
    # as cluster_id — a complaint that was never split has bundle_id ==
    # its own id. unverified_allegation marks a sub-issue that was hearsay
    # about a named individual rather than a directly-reported/factual
    # complaint — see splitting.py's own docstring for why this is kept
    # distinct from corruption_flag.
    bundle_id = db.Column(db.String(36), nullable=True, index=True)
    unverified_allegation = db.Column(db.Boolean, nullable=False, default=False)

    # Repeated-closure-dispute escalation (ties to ideation doc gap #6).
    # A citizen can reopen a "resolved" complaint via POST
    # /api/complaints/<id>/dispute (see complaints.py); after
    # DISPUTE_ESCALATION_THRESHOLD reopens the complaint is forced into
    # audit_tier instead of looping through the same resolve/dispute cycle
    # indefinitely.
    dispute_count = db.Column(db.Integer, nullable=False, default=0)

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
            "clusterId": self.cluster_id,
            "corroborationCount": self.corroboration_count,
            "isRepeatFiling": bool(self.is_repeat_filing),
            "suspectedCoordinated": bool(self.suspected_coordinated),
            "modeledSeverity": self.modeled_severity,
            "statedUrgency": self.stated_urgency,
            "bundleId": self.bundle_id,
            "unverifiedAllegation": bool(self.unverified_allegation),
            "disputeCount": self.dispute_count,
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

    source_url/last_synced_at/external_id support the ingestion pipeline
    (see policy_ingest.py) that keeps this table current from an external
    feed instead of only ever growing by hand-editing the JSON seed —
    external_id is that feed's own identifier for the scheme (its dedup
    key on re-sync), separate from `slug` which is this app's own primary
    key and may be derived from the title if the source doesn't provide a
    stable id.
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

    external_id = db.Column(db.String(160), nullable=True, index=True)
    source_url = db.Column(db.String(500), nullable=True)
    last_synced_at = db.Column(db.DateTime, nullable=True)

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
            "sourceUrl": self.source_url or "",
            "lastSyncedAt": self.last_synced_at.isoformat() if self.last_synced_at else None,
        }


