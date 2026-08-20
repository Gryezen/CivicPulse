"""
CivicPulse — Flask app
-----------------------
Serves the prototype pages and a small account/auth API backed by Postgres
(Supabase in production, sqlite locally if DATABASE_URL isn't set).

Local run:
    cp .env.example .env      # fill in DATABASE_URL / SECRET_KEY (or leave
                               # DATABASE_URL unset to use a local sqlite file)
    pip install -r requirements.txt
    python app.py
Then open http://127.0.0.1:5000/

See README.md for the Supabase + Render setup.
"""

import json
import os

from flask import Flask, render_template, send_from_directory, jsonify, redirect, url_for, request
from flask_login import login_required, current_user
from dotenv import load_dotenv

from extensions import db, login_manager
from auth import auth_bp
from complaints import complaints_bp
from officer import officer_bp
from policy_engine import PolicyRecommender, find_policy, load_policies, seed_policies_if_empty

load_dotenv()

app = Flask(__name__)

app.config["SECRET_KEY"] = os.environ.get("SECRET_KEY", "dev-only-insecure-key-change-me")

# DATABASE_URL is what Supabase (and Render, and most Postgres hosts) hand you —
# postgres://... or postgresql://... . SQLAlchemy 2.x wants the "postgresql://"
# scheme, and Supabase's copy-paste URL sometimes still says "postgres://".
db_url = os.environ.get("DATABASE_URL", "").strip()
if db_url.startswith("postgres://"):
    db_url = db_url.replace("postgres://", "postgresql://", 1)
if not db_url:
    # No Supabase configured yet — fall back to a local sqlite file so the
    # app still runs out of the box. Swap in DATABASE_URL when Supabase is set up.
    db_url = "sqlite:///" + os.path.join(app.instance_path, "civicpulse.db")
    os.makedirs(app.instance_path, exist_ok=True)

app.config["SQLALCHEMY_DATABASE_URI"] = db_url
app.config["SQLALCHEMY_ENGINE_OPTIONS"] = {"pool_pre_ping": True}

db.init_app(app)
login_manager.init_app(app)
login_manager.login_view = "login"

app.register_blueprint(auth_bp)
app.register_blueprint(complaints_bp)
app.register_blueprint(officer_bp)


@login_manager.user_loader
def load_user(user_id):
    from models import User
    return db.session.get(User, user_id)


# API calls should get a 401 JSON response when unauthenticated, not a
# redirect to /login — only page routes redirect.
@login_manager.unauthorized_handler
def unauthorized():
    if request.path.startswith("/api/"):
        return jsonify({"error": "Not logged in."}), 401
    return redirect(url_for("login", next=request.path))


def _ensure_new_columns():
    """db.create_all() only creates tables that don't exist yet — it never
    ALTERs an existing one. That's fine for a brand-new Supabase project,
    but this app's `complaints` table already existed (and had real rows)
    in Supabase before the confidence/needs_review/corruption_flag/
    threat_flag columns were added to the model. This adds them if missing,
    so the same codebase works against a fresh DB and an already-seeded
    Supabase project without a manual migration step.

    Postgres supports "ADD COLUMN IF NOT EXISTS" directly. Guarded in a
    try/except anyway (and per-column, not one big statement) so one
    column already existing — or sqlite's older ALTER syntax — can't abort
    the rest.
    """
    from sqlalchemy import text

    statements = [
        "ALTER TABLE complaints ADD COLUMN IF NOT EXISTS confidence FLOAT",
        "ALTER TABLE complaints ADD COLUMN IF NOT EXISTS needs_review BOOLEAN NOT NULL DEFAULT FALSE",
        "ALTER TABLE complaints ADD COLUMN IF NOT EXISTS corruption_flag BOOLEAN NOT NULL DEFAULT FALSE",
        "ALTER TABLE complaints ADD COLUMN IF NOT EXISTS threat_flag BOOLEAN NOT NULL DEFAULT FALSE",
        "ALTER TABLE complaints ADD COLUMN IF NOT EXISTS broad_category VARCHAR(60) NOT NULL DEFAULT 'General Governance'",
        "ALTER TABLE complaints ADD COLUMN IF NOT EXISTS audit_tier BOOLEAN NOT NULL DEFAULT FALSE",
        "ALTER TABLE complaints ADD COLUMN IF NOT EXISTS auto_resolved BOOLEAN NOT NULL DEFAULT FALSE",
        "ALTER TABLE complaints ADD COLUMN IF NOT EXISTS ai_brief VARCHAR(240)",
        "ALTER TABLE complaints ADD COLUMN IF NOT EXISTS assigned_officer VARCHAR(160)",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'citizen'",
    ]
    for stmt in statements:
        try:
            db.session.execute(text(stmt))
            db.session.commit()
        except Exception:
            db.session.rollback()  # sqlite (no IF NOT EXISTS support) or column already there — safe to skip


with app.app_context():
    from datetime import datetime, timedelta, timezone
    from models import User, Complaint, ClassificationLog, Policy  # noqa: F401  (registers models with SQLAlchemy)
    from seed_data import DEMO_ACCOUNT, DEMO_OFFICER_ACCOUNT, DEMO_QUEUE_COMPLAINTS, dataset_sample_complaints

    db.create_all()
    _ensure_new_columns()
    seed_policies_if_empty()

    # Officer demo login — seeded independently of the complaint-seeding
    # block below (account existence shouldn't depend on whether complaints
    # happen to be empty). Safe to run every boot: only inserts if missing.
    if User.query.filter_by(email=DEMO_OFFICER_ACCOUNT["email"]).first() is None:
        officer_user = User(**DEMO_OFFICER_ACCOUNT)
        officer_user.set_password(os.environ.get("DEMO_OFFICER_PASSWORD", "demo-officer-not-for-login"))
        db.session.add(officer_user)
        db.session.commit()

    # Seed a handful of demo complaints (under a system "Demo Citizen" account)
    # so the Complaints & Policies queue isn't empty on a fresh database.
    # Real complaints from real accounts are added alongside these via the
    # normal POST /api/complaints flow.
    if Complaint.query.count() == 0:
        demo_user = User.query.filter_by(email=DEMO_ACCOUNT["email"]).first()
        if not demo_user:
            demo_user = User(**DEMO_ACCOUNT)
            demo_user.set_password(os.environ.get("DEMO_ACCOUNT_PASSWORD", "demo-account-not-for-login"))
            db.session.add(demo_user)
            db.session.commit()
        now = datetime.now(timezone.utc)
        for c in DEMO_QUEUE_COMPLAINTS:
            db.session.add(Complaint(
                user_id=demo_user.id,
                title=c["title"], body=c["body"], language=c["language"],
                category=c["category"], department=c["department"], authority=c["authority"],
                priority=c["priority"], stage=c["stage"], files_count=c["files_count"],
                note=c["note"], filed_at=now - timedelta(days=c["filed_offset_days"]),
            ))
        # Real rows from the simulated dataset, run through the actual
        # classify() — proves out the model + corruption/threat flags +
        # needs-review bucket with more than 9 hand-written examples.
        for c in dataset_sample_complaints():
            db.session.add(Complaint(
                user_id=demo_user.id,
                title=c["title"], body=c["body"], language=c["language"],
                category=c["category"], department=c["department"], authority=c["authority"],
                priority=c["priority"], stage=c["stage"], files_count=c["files_count"],
                note=c["note"], filed_at=now - timedelta(days=c["filed_offset_days"]),
                confidence=c["confidence"], needs_review=c["needs_review"],
                corruption_flag=c["corruption_flag"], threat_flag=c["threat_flag"],
            ))
        db.session.commit()


# ---------------------------------------------------------------------------
# PolicyGyaan bridge — see policy_engine.py.
#
# ALL_POLICIES replaces the hardcoded CP_POLICIES array that used to live in
# static/main.js. POLICY_RECOMMENDER calls Gemini (google-genai) to rank
# policies per-citizen the same way PolicyGyaan's PromptManager did, and
# transparently falls back to keyword scoring if GOOGLE_API_KEY isn't set
# or the call fails, so the pages never end up empty.
# ---------------------------------------------------------------------------
with app.app_context():
    ALL_POLICIES = load_policies()  # now a DB read (see policy_engine.load_policies) — needs app context
POLICY_RECOMMENDER = PolicyRecommender(api_key=os.environ.get("GOOGLE_API_KEY"))


# Every page maps 1:1 to a template — dashboard/track/policy have their own
# routes further down since they need extra Jinja context (policies_json
# etc). Search each template for `TODO(backend)` to find any spot still
# waiting on a real fetch() call.
PAGES = {
    "index": "index.html",
    "login": "login.html",
    "complaint": "complaint.html",
    "account": "account.html",
}

# Pages that require a logged-in session. Login-gated at the route level
# (not just hidden in JS) so a signed-out visitor can't just load the HTML.
PROTECTED_PAGES = {"complaint", "account"}


@app.route("/")
def home():
    return render_template(PAGES["index"])


@app.route("/login")
def login():
    if current_user.is_authenticated:
        return redirect(url_for("page", page="dashboard"))
    return render_template(PAGES["login"])


# CSS/JS are referenced by the HTML as plain "style.css" / "main.js"
# (no /static/ prefix), so serve them at the root explicitly.
@app.route("/style.css")
def style_css():
    return send_from_directory(app.static_folder, "style.css")


@app.route("/main.js")
def main_js():
    return send_from_directory(app.static_folder, "main.js")


@app.route("/img/<path:filename>")
def img_asset(filename):
    return send_from_directory(app.static_folder + "/img", filename)


def _serve_page(page):
    template = PAGES.get(page)
    if template is None:
        return render_template(PAGES["index"]), 404
    if page in PROTECTED_PAGES and not current_user.is_authenticated:
        return redirect(url_for("login", next="/" + page))
    return render_template(template)


def _recommended_policies_json(limit=6):
    """Personalised recommendations for the logged-in citizen, rendered
    server-side and injected into the template as `const CP_POLICIES = ...`
    — this is what used to be the hardcoded array in static/main.js."""
    from models import Complaint
    complaints = (
        Complaint.query.filter_by(user_id=current_user.id)
        .order_by(Complaint.filed_at.desc())
        .limit(20)
        .all()
    )
    context_text = " ".join(c.title for c in complaints)
    policies = POLICY_RECOMMENDER.recommend(
        user=current_user.to_dict(),
        context_text=context_text,
        policies=ALL_POLICIES,
        limit=limit,
    )
    return json.dumps(policies)


@app.route("/officer")
@app.route("/officer.html")
@login_required
def officer_dashboard():
    if not current_user.is_official:
        return render_template("index.html"), 403
    return render_template("officer.html")


@app.route("/dashboard.html")
@login_required
def dashboard():
    return render_template("dashboard.html", policies_json=_recommended_policies_json(limit=6))


@app.route("/track")
@app.route("/track.html")
@login_required
def track():
    # The NLP search box on this page needs the full catalogue to search
    # against, not just a personalised top-N, so it gets everything.
    return render_template("track.html", all_policies_json=json.dumps(ALL_POLICIES))


@app.route("/policy/<string:slug>")
@login_required
def policy_detail(slug):
    policy = find_policy(slug, ALL_POLICIES)
    return render_template("policy.html", policy=policy)


@app.route("/api/policies")
@login_required
def api_policies():
    return jsonify(ALL_POLICIES)


@app.route("/api/policies/<string:slug>")
@login_required
def api_policy_detail(slug):
    policy = find_policy(slug, ALL_POLICIES)
    if policy is None:
        return jsonify({"error": "Policy not found."}), 404
    return jsonify(policy)


@app.route("/<page>")
def page(page):
    """Clean URLs: /login, /complaint, /account (dashboard/track/policy have
    their own routes above, since they need extra template context)."""
    if page == "login":
        return login()
    return _serve_page(page)


# Also accept the .html links already used inside the pages themselves
# (nav links, buttons) so nothing in the HTML had to change.
@app.route("/<page>.html")
def page_html(page):
    if page == "login":
        return login()
    return _serve_page(page)


if __name__ == "__main__":
    # Render (and most PaaS hosts) inject PORT and expect the app to bind 0.0.0.0.
    # DEBUG stays off unless explicitly set — never run the Werkzeug debugger in prod.
    port = int(os.environ.get("PORT", 5000))
    debug = os.environ.get("FLASK_DEBUG", "0") == "1"
    app.run(host="0.0.0.0", port=port, debug=debug)
