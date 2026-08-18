"""
CivicPulse — temporary demo shell (frontend team)
---------------------------------------------------
This is NOT the real backend. It just serves the static prototype
(templates/*.html + static/style.css + static/main.js) over Flask
with clean URLs, so the team can click through the flow before the
real API (see the design doc's /api routes) is wired up.

Run:
    pip install flask
    python app.py
Then open http://127.0.0.1:5000/
"""

from flask import Flask, render_template, send_from_directory

app = Flask(__name__)

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
}


@app.route("/")
def home():
    return render_template(PAGES["index"])


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


@app.route("/<page>")
def page(page):
    """Clean URLs: /login, /complaint, /dashboard, /track"""
    template = PAGES.get(page)
    if template is None:
        return render_template(PAGES["index"]), 404
    return render_template(template)


# Also accept the .html links already used inside the pages themselves
# (nav links, buttons) so nothing in the HTML had to change.
@app.route("/<page>.html")
def page_html(page):
    template = PAGES.get(page)
    if template is None:
        return render_template(PAGES["index"]), 404
    return render_template(template)


if __name__ == "__main__":
    import os
    # Render (and most PaaS hosts) inject PORT and expect the app to bind 0.0.0.0.
    # DEBUG stays off unless explicitly set — never run the Werkzeug debugger in prod.
    port = int(os.environ.get("PORT", 5000))
    debug = os.environ.get("FLASK_DEBUG", "0") == "1"
    app.run(host="0.0.0.0", port=port, debug=debug)
