"""
Self-resolution agent (Phase 7 in the ideation doc: "Confidence-Gated
Automation").

What this honestly does: for a NEW complaint, it looks at past complaints
in the SAME fine category that are already marked `stage == "resolved"`,
and measures how textually similar the new one is to each (TF-IDF cosine
similarity over title+body — the same representation classify.py's model
uses, just fit fresh on this small per-category corpus rather than the
training set). If the closest match clears both a similarity bar and the
classifier's own confidence bar, AND the category is on an explicit
low-risk allowlist, AND none of the corruption/threat/audit-tier/needs-
review flags are set, the agent auto-dispatches the same standard action
that resolved the closest match — no human in the loop for that one case.

What this does NOT honestly do: this is not "learned from millions of real
resolutions." The corpus it compares against is whatever's already in this
deployment's `complaints` table — on a fresh install, that's the demo/
dataset-sample rows seeded by seed_data.py. That's disclosed here plainly
because "the agent said X so it must be right" is exactly the failure mode
the ideation doc warns about (self-grading closure, unexplainable black
boxes). Every decision — act or don't — gets one AutoResolutionLog row
with the reasoning, specifically so this claim can be checked.

Deliberately conservative defaults: this only fires for categories that are
inherently low-stakes even when the classifier is wrong (a mis-auto-
resolved streetlight complaint is an inconvenience; a mis-auto-resolved
safety complaint is not). Categories involving safety, health, corruption,
or law enforcement are never on the allowlist, regardless of confidence.
"""

SIMILARITY_THRESHOLD = 0.55
CONFIDENCE_THRESHOLD = 0.75
MIN_PAST_RESOLVED = 2  # need at least this many resolved examples in-category before the agent will act at all

# Conservative allowlist — low-stakes, high-volume, procedurally simple
# categories only. Everything else (health, police, corruption, pension,
# fire, etc.) always goes to a human, no matter how confident the model is.
AUTO_RESOLVABLE_CATEGORIES = {
    "Street Lighting",
    "Sanitation & Waste",
    "Municipal Corporation - Sanitation",
    "Roads & PWD",
    "Roads & Potholes",
    "Water Supply",
    "Water Supply / Sanitation",
}

_STANDARD_ACTIONS = {
    "Street Lighting": "auto-generated work order to the Ward Office electrical team",
    "Sanitation & Waste": "auto-generated pickup request to the Ward / Panchayat sanitation team",
    "Municipal Corporation - Sanitation": "auto-generated pickup request to the Ward / Panchayat sanitation team",
    "Roads & PWD": "auto-generated inspection request to PWD (Roads)",
    "Roads & Potholes": "auto-generated inspection request to PWD (Roads)",
    "Water Supply": "auto-generated repair request to the Water Works field team",
    "Water Supply / Sanitation": "auto-generated repair request to the Water Works field team",
}


def attempt_auto_resolve(complaint_id, title, body, category, confidence, corruption_flag,
                          threat_flag, audit_tier, needs_review):
    """Returns (action_taken: bool, note: str | None, log_fields: dict).

    `log_fields` is meant to be unpacked straight into an AutoResolutionLog
    row by the caller (complaints.py) — kept as a plain dict here so this
    module doesn't need to import models.py / touch the DB session itself.
    """
    reason_prefix = f"category={category!r}"

    if category not in AUTO_RESOLVABLE_CATEGORIES:
        return False, None, {"action_taken": False, "reason": f"{reason_prefix} not on the auto-resolve allowlist"}
    if corruption_flag or threat_flag or audit_tier:
        return False, None, {
            "action_taken": False,
            "reason": f"{reason_prefix} — corruption/threat/audit-tier flag set, always routes to a human",
        }
    if needs_review:
        return False, None, {"action_taken": False, "reason": f"{reason_prefix} — below classification confidence threshold"}
    if confidence is None or confidence < CONFIDENCE_THRESHOLD:
        return False, None, {
            "action_taken": False,
            "reason": f"{reason_prefix} — classifier confidence {confidence} below {CONFIDENCE_THRESHOLD}",
        }

    from models import Complaint  # local import — avoids a circular import with models.py at module load

    past = (
        Complaint.query
        .filter(Complaint.category == category, Complaint.stage == "resolved", Complaint.id != complaint_id)
        .order_by(Complaint.filed_at.desc())
        .limit(200)
        .all()
    )
    if len(past) < MIN_PAST_RESOLVED:
        return False, None, {
            "action_taken": False,
            "reason": f"{reason_prefix} — only {len(past)} past-resolved example(s) in this category, need {MIN_PAST_RESOLVED}+",
        }

    try:
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.metrics.pairwise import cosine_similarity
    except Exception:
        return False, None, {"action_taken": False, "reason": "scikit-learn unavailable at runtime"}

    corpus = [f"{p.title} {p.body}" for p in past]
    query_text = f"{title} {body}"
    vectorizer = TfidfVectorizer(ngram_range=(1, 2), min_df=1)
    matrix = vectorizer.fit_transform(corpus + [query_text])
    sims = cosine_similarity(matrix[-1], matrix[:-1])[0]
    best_idx = int(sims.argmax())
    best_similarity = float(sims[best_idx])
    best_match = past[best_idx]

    if best_similarity < SIMILARITY_THRESHOLD:
        return False, None, {
            "action_taken": False,
            "reason": f"{reason_prefix} — closest past-resolved match only {best_similarity:.2f} similar, below {SIMILARITY_THRESHOLD}",
            "matched_complaint_id": best_match.id,
            "similarity": best_similarity,
        }

    action = _STANDARD_ACTIONS.get(category, "auto-generated work order to the responsible department")
    note = (
        f"Auto-resolved by the self-resolution agent: {action}, based on a "
        f"{best_similarity:.0%} textual match to a previously resolved complaint "
        f"in this category (classifier confidence {confidence:.0%}). "
        "An officer can reopen this at any time — see the audit trail for the full reasoning."
    )
    return True, note, {
        "action_taken": True,
        "reason": f"{reason_prefix} — {best_similarity:.2f} similarity to resolved complaint {best_match.id}, confidence {confidence:.2f}",
        "matched_complaint_id": best_match.id,
        "similarity": best_similarity,
    }
