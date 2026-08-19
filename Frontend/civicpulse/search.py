"""
Keyword-overlap scoring for complaints — a stand-in for real semantic
search until the NLP model is wired in. Ported from the client-side
scoreComplaints() that used to live in track.html, so ranking behaves
identically now that it's server-side.

Policy scoring lives separately in policy_engine.py (it works over the
policies_data.json dicts, not a DB model).
"""

import re

STOPWORDS = {
    "the", "a", "an", "is", "are", "was", "were", "in", "on", "at", "near", "not",
    "for", "and", "of", "to", "my", "our", "it", "this", "that", "with", "from",
    "has", "have", "been", "there", "still", "again", "since", "me", "please",
    "i", "we", "issue", "problem",
}


def extract_terms(query):
    if not query:
        return []
    tokens = re.split(r"[^a-z0-9]+", query.lower())
    seen = []
    for t in tokens:
        if len(t) >= 3 and t not in STOPWORDS and t not in seen:
            seen.append(t)
    return seen


def score_complaint(terms, complaint):
    title_text = complaint.title.lower()
    wide_text = f"{complaint.body} {complaint.category} {complaint.department} {complaint.authority}".lower()
    score = 0
    for t in terms:
        if t in title_text:
            score += 3
        elif t in wide_text:
            score += 1
    return score

