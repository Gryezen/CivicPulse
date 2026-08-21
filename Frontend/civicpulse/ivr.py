"""
SMS/IVR status-check for citizens without a smartphone or reliable data
connection — mentioned in the ideation doc as a gap for anyone who can't
use the web app.

**What this honestly is:** the message-handling LOGIC a real SMS/IVR
gateway (Twilio, Exotel, Gupshup, a state government's own SMS gateway,
etc.) would call via webhook when an inbound text/call comes in — command
parsing, phone-to-account lookup, and a plain-text reply. **What this is
NOT**: a working connection to any actual telecom carrier. No SMS or call
is truly sent by anything in this repo — wiring `/webhook/ivr/inbound` to
a specific gateway's actual webhook contract (Twilio's TwiML response
format, Exotel's applet JSON, etc.) is a thin adapter on top of this
function that needs a real account with that gateway to test, which this
sandbox has no way to obtain or verify. See templates/sms-demo.html for a
way to exercise this logic without a telecom account — it's an in-browser
chat widget that hits this same handler.

**Auth model**: this endpoint has NO login — by definition, an SMS
arrives from a phone number, not a logged-in browser session. Trust
instead comes from validating the WEBHOOK is really the configured
gateway calling in (see IVR_WEBHOOK_SECRET below) — once that's
established, the `phone` field in the payload is trustworthy because the
telecom carrier itself is the one asserting which number the message
came from, the same way Twilio's request signature works in production.
Fails closed if IVR_WEBHOOK_SECRET is unset, same pattern as
OFFICIAL_VERIFICATION_CODE and POLICY_SOURCE_URL elsewhere in this repo.

Commands (case-insensitive, matches how someone would actually type on a
phone keypad/SMS):
    STATUS                  latest 3 complaints for the sending phone number
    STATUS <id-fragment>    a specific complaint (first 8 chars of its id)
    HELP                    command list
    (anything else)         treated as STATUS
"""

import os

from flask import Blueprint, request, jsonify

ivr_bp = Blueprint("ivr", __name__)

MAX_STATUS_RESULTS = 3
HELP_TEXT = "CivicPulse SMS: reply STATUS for your latest complaints, or STATUS <id> for one specific complaint."


def _stage_label(complaint):
    labels = {
        "received": "Received",
        "processing": "In review",
        "assigned": "Assigned to a department",
        "pending_confirmation": "Marked resolved — awaiting your confirmation (reply CONFIRM <id> or DISPUTE <id>)",
        "resolved": "Resolved",
    }
    return labels.get(complaint.stage, complaint.stage)


def _format_line(complaint):
    short_id = complaint.id[:8]
    title = complaint.title if len(complaint.title) <= 40 else complaint.title[:37] + "..."
    return f"[{short_id}] {title} — {_stage_label(complaint)}"


def handle_inbound(phone, text):
    """Pure function, no Flask request object needed — easy to unit test
    and easy to reuse from a different gateway adapter later. Returns the
    plain-text reply body. Must be called inside an app context (touches
    the DB)."""
    from models import User, Complaint

    phone = (phone or "").strip()
    text = (text or "").strip()
    command, _, rest = text.partition(" ")
    command = command.upper()
    rest = rest.strip()

    if not phone:
        return "Could not identify your phone number. Please contact your ward office."

    user = User.query.filter_by(phone=phone).first()
    if not user:
        return (
            "This number isn't linked to a CivicPulse account yet. Add your phone number from the "
            "'Account' page in the app, or ask someone to file complaints on your behalf."
        )

    if command == "HELP":
        return HELP_TEXT

    if command in ("CONFIRM", "DISPUTE") and rest:
        complaint = _find_by_short_id(user, rest)
        if not complaint:
            return f"No complaint found matching '{rest}' on your account."
        if command == "CONFIRM":
            if not complaint.pending_confirmation:
                return f"[{complaint.id[:8]}] isn't awaiting your confirmation."
            return f"To confirm [{complaint.id[:8]}] is fixed, please reply from the app or web — SMS confirmation isn't wired up yet in this prototype."
        else:
            return f"To dispute [{complaint.id[:8]}], please reply from the app or web — SMS dispute isn't wired up yet in this prototype."

    # Default / STATUS path — with or without an id fragment.
    if rest:
        complaint = _find_by_short_id(user, rest)
        if not complaint:
            return f"No complaint found matching '{rest}' on your account."
        return _format_line(complaint) + f"\nFiled {complaint.filed_at.strftime('%d %b %Y') if complaint.filed_at else ''}. Category: {complaint.category}."

    complaints = (
        Complaint.query.filter_by(user_id=user.id)
        .order_by(Complaint.filed_at.desc())
        .limit(MAX_STATUS_RESULTS)
        .all()
    )
    if not complaints:
        return "You have no complaints on file yet. File one from the CivicPulse app."

    lines = [_format_line(c) for c in complaints]
    return f"Your latest complaint(s):\n" + "\n".join(lines)


def _find_by_short_id(user, fragment):
    from models import Complaint
    fragment = fragment.strip().lower()
    candidates = Complaint.query.filter_by(user_id=user.id).all()
    for c in candidates:
        if c.id.lower().startswith(fragment):
            return c
    return None


@ivr_bp.post("/webhook/ivr/inbound")
def inbound_webhook():
    """Gateway-agnostic inbound webhook. Expects JSON (or form-encoded,
    since most telecom gateways POST form data, not JSON):
        {"phone": "+919876543210", "text": "STATUS"}
    Returns {"reply": "..."} — adapting this to a specific gateway's
    expected response format (e.g. Twilio TwiML XML) is a thin wrapper
    around this handler, not built here (see module docstring)."""
    secret = os.environ.get("IVR_WEBHOOK_SECRET")
    if not secret:
        return jsonify({"error": "IVR webhook is not configured on this deployment."}), 403
    if request.args.get("secret") != secret and request.headers.get("X-IVR-Secret") != secret:
        return jsonify({"error": "Invalid or missing webhook secret."}), 403

    data = request.get_json(silent=True) or request.form
    phone = data.get("phone") or data.get("From") or ""
    text = data.get("text") or data.get("Body") or ""

    reply = handle_inbound(phone, text)
    return jsonify({"reply": reply})


@ivr_bp.post("/api/ivr/demo")
def demo_endpoint():
    """Backs templates/sms-demo.html's in-browser chat widget — same
    handle_inbound() logic as the real webhook, but callable directly from
    a logged-in browser session for demo purposes (no gateway/secret
    needed since this is explicitly the "pretend to be an SMS" path, not
    the production inbound webhook). The demo widget still has to supply a
    phone number explicitly (not the logged-in session's identity) to
    accurately simulate what an SMS gateway actually provides."""
    data = request.get_json(silent=True) or {}
    phone = data.get("phone") or ""
    text = data.get("text") or ""
    reply = handle_inbound(phone, text)
    return jsonify({"reply": reply})
