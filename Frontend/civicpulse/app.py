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

import os

from flask import Flask, render_template, send_from_directory, jsonify, redirect, url_for, request
from flask_login import login_required, current_user
from dotenv import load_dotenv

from extensions import db, login_manager
from auth import auth_bp

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


with app.app_context():
    from models import User  # noqa: F401  (registers the model with SQLAlchemy)
    db.create_all()


# Every page maps 1:1 to a template — swap this for real routes/logic
# once the backend team's Flask REST API is ready. Search each template
# for `TODO(backend)` to find every spot that needs a real fetch() call.
PAGES = {
    "index": "index.html",
    "login": "login.html",
    "complaint": "complaint.html",
    "dashboard": "dashboard.html",
    "track": "track.html",
    "account": "account.html",
    "policy": "policy.html",
}

# Pages that require a logged-in session. Login-gated at the route level
# (not just hidden in JS) so a signed-out visitor can't just load the HTML.
PROTECTED_PAGES = {"complaint", "dashboard", "track", "account", "policy"}


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


@app.route("/<page>")
def page(page):
    """Clean URLs: /login, /complaint, /dashboard, /track, /account"""
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
