"""
PolicyGyaan bridge.
--------------------
This is the Jinja bridge referenced in main.js: it replaces the hardcoded
`CP_POLICIES` array that used to live in static/main.js. Policies now live
in Postgres (the `policies` table — see models.Policy), seeded once from
policies_data.json by seed_policies_if_empty() (called from app.py, same
"seed if empty" pattern as the demo complaints). policies_data.json stays
in the repo as the editable seed source — add a policy there and restart
to get it into the DB — rather than requiring a DB console for a 6-row
table. app.py renders real, per-user recommendations straight into the
templates as `{{ policies_json|safe }}`.

Two ranking strategies:
  - `PolicyRecommender.recommend()` — calls Gemini (google-genai), the same
    model/approach as PolicyGyaan's PromptManager.load_dashboard_prompt(),
    adapted to CivicPulse's user profile fields (occupation/region/
    education/employed instead of PolicyGyaan's profession/gender/age/state).
  - `keyword_score()` — a pure-Python port of the old client-side
    scorePolicies() in main.js. Used whenever there's no GOOGLE_API_KEY
    configured, the Gemini call fails, or the model's response can't be
    parsed — so the dashboard/track pages never end up empty.
"""

import json
import os
import re


_POLICIES_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "policies_data.json")

_STOPWORD_MIN_LEN = 3


def _load_seed_policies():
    """The raw JSON seed file — used to populate the DB on first boot, and
    as a last-resort fallback if the DB is unreachable."""
    with open(_POLICIES_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)
    return data["policies"]


def seed_policies_if_empty():
    """Called once from app.py's startup block. Mirrors the demo-complaint
    seeding pattern: only inserts if the table is empty, so it's safe to
    call on every boot and never overwrites a policy an officer has since
    edited in the DB directly."""
    from models import Policy
    from extensions import db

    if Policy.query.first() is not None:
        return
    for p in _load_seed_policies():
        db.session.add(Policy(
            slug=p["slug"], title=p["title"], source=p.get("source", ""),
            category=p["category"], summary=p["summary"],
            keywords=p.get("keywords", []), eligibility=p.get("eligibility", ""),
            roadmap=p.get("roadmap", []),
        ))
    db.session.commit()


def load_policies():
    """Load the policy dataset from Postgres. Falls back to the JSON seed
    file if the table is empty or the query fails for any reason (e.g. DB
    briefly unreachable) — the dashboard/track pages should never end up
    empty just because Postgres hiccuped."""
    try:
        from models import Policy
        rows = Policy.query.order_by(Policy.title).all()
        if rows:
            return [r.to_dict() for r in rows]
    except Exception:
        pass
    return _load_seed_policies()


def find_policy(slug, policies=None):
    """Look up a single policy by slug — server-side equivalent of the old
    `CP_POLICIES.find(p => p.slug === slug)` in policy.html."""
    policies = policies if policies is not None else load_policies()
    for p in policies:
        if p.get("slug") == slug:
            return p
    return None


def keyword_score(query, policies=None):
    """Port of scorePolicies() from static/main.js.

    Returns a list of {"policy": ..., "score": ...} sorted by score desc,
    so callers can reuse the same "ranked / filter(score > 0)" pattern the
    frontend already used.
    """
    policies = policies if policies is not None else load_policies()
    terms = sorted(set(
        t for t in re.split(r"[^a-z0-9]+", (query or "").lower()) if len(t) >= _STOPWORD_MIN_LEN
    ))

    ranked = []
    for p in policies:
        text = f"{p.get('title', '')} {p.get('summary', '')} {p.get('category', '')}".lower()
        keywords = p.get("keywords", [])
        score = 0
        for t in terms:
            if any(t in k or k in t for k in keywords):
                score += 3
            elif t in text:
                score += 1
        ranked.append({"policy": p, "score": score})

    ranked.sort(key=lambda r: r["score"], reverse=True)
    return ranked


def _parse_indices(text, max_index):
    """Robustly pull integer indices out of whatever Gemini returns.

    PolicyGyaan's original process_indices() assumed a very specific
    "1, 2, 3" layout and threw on anything else. This just grabs every
    integer in the response and keeps the ones that are valid indices.
    """
    found = [int(n) for n in re.findall(r"\d+", text or "")]
    seen = []
    for n in found:
        if 0 <= n < max_index and n not in seen:
            seen.append(n)
    return seen


class PolicyRecommender:
    """Thin wrapper around google-genai, modelled on PolicyGyaan's
    PromptManager.load_dashboard_prompt() — but degrades gracefully instead
    of crashing when there's no API key or the call fails, since this now
    runs on every dashboard/track page load rather than behind a button."""

    def __init__(self, api_key=None, model="gemini-2.5-flash"):
        self.model = model
        self.client = None
        if api_key:
            try:
                from google import genai
                self.client = genai.Client(api_key=api_key)
            except Exception:
                # google-genai not installed, or the client couldn't init —
                # fall back to keyword scoring everywhere below.
                self.client = None

    def _profile_line(self, user):
        parts = []
        if user.get("occupation"):
            parts.append(f"occupation: {user['occupation']}")
        if user.get("region"):
            parts.append(f"region: {user['region']}")
        if user.get("education"):
            parts.append(f"education: {user['education']}")
        parts.append(f"employed: {'yes' if user.get('employed') else 'no'}")
        if user.get("language"):
            parts.append(f"preferred language: {user['language']}")
        return ", ".join(parts) if parts else "no profile info available"

    def recommend(self, user, context_text="", policies=None, limit=6):
        """Return up to `limit` policies personalised for `user`
        (a dict shaped like models.User.to_dict()), optionally weighted by
        `context_text` (e.g. the citizen's own complaint titles).

        Falls back to keyword_score() whenever Gemini isn't configured or
        the response can't be parsed, so callers always get a usable list.
        """
        policies = policies if policies is not None else load_policies()
        if not policies:
            return []

        if self.client is not None:
            try:
                return self._recommend_via_gemini(user, context_text, policies, limit)
            except Exception:
                pass  # fall through to keyword scoring below

        ranked = keyword_score(context_text, policies)
        ranked = [r for r in ranked if r["score"] > 0] or [{"policy": p, "score": 0} for p in policies]
        return [r["policy"] for r in ranked[:limit]]

    def _recommend_via_gemini(self, user, context_text, policies, limit):
        listing = [
            {"index": i, "title": p["title"], "category": p["category"], "summary": p["summary"]}
            for i, p in enumerate(policies)
        ]
        prompt = (
            "You are ranking civic-scheme recommendations for a citizen of India using the "
            "CivicPulse app. Citizen profile — " + self._profile_line(user) + ". "
        )
        if context_text.strip():
            prompt += f"Their recent complaints/searches mention: \"{context_text.strip()}\". "
        prompt += (
            f"From this policy list (JSON): {json.dumps(listing)} — "
            f"return only the {limit} most relevant policy indices for this citizen, most "
            "relevant first. Reply with nothing but the indices separated by commas, e.g. "
            "\"2, 0, 5\". Do not explain your reasoning."
        )

        response = self.client.models.generate_content(model=self.model, contents=prompt)
        indices = _parse_indices(getattr(response, "text", "") or "", len(policies))
        if not indices:
            raise ValueError("no usable indices returned by model")
        return [policies[i] for i in indices[:limit]]
