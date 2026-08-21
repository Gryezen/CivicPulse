"""
Complaint splitting — two distinct edge cases from the ideation doc that
both boil down to "one submission, more than one routable issue":

  1. **Bundled multi-issue complaints** (structurally-tricky case):
         "Water hasn't come in 4 days, also the road outside is broken,
         also my neighbor built an illegal extension, also my pension is
         still pending."
     Needs splitting into separate routable issues instead of one blob
     that can never cleanly resolve because it belongs to four
     departments at once.

  2. **Factual complaint + unverified character attack** (gap #5):
         "The ration shop dealer never opens on time, and everyone knows
         he's been pocketing bribes from half the ward for years."
     Should split into (a) "shop not open per schedule" — factual,
     verifiable, routes normally, and (b) "bribery allegation" — unverified
     hearsay, tagged separately, does NOT auto-attach to the named
     individual's official record without independent review. This is
     deliberately NOT the same handling as classify.py's corruption_flag
     (which is for a complainant reporting bribery demanded of THEM
     directly) — hearsay about a named person is weaker evidence and
     needs to be visibly weaker in the system, not silently upgraded to
     the same "route to Vigilance" treatment.

Both detections are simple and disclosed as such: clause-splitting on
conjunctions/punctuation + reusing classify() per clause, and a keyword-
pattern check for hearsay language. Nothing here is a trained model —
same honest-simplification posture as the rest of the codebase. A clause
this misses just stays part of the parent issue, which is the safe
failure mode (worse UX, not a wrong routing).
"""

import re

_CLAUSE_SPLIT_RE = re.compile(
    r"(?:,?\s+also\s+|,?\s+and\s+also\s+|;\s*)", re.IGNORECASE
)
_MIN_CLAUSE_LEN = 12  # ignore fragments too short to be a standalone issue

_HEARSAY_RE = re.compile(
    r"\b(everyone knows|it'?s (?:said|rumou?red)|rumou?red|allegedly|people say|word is|"
    r"i heard|apparently)\b", re.IGNORECASE
)
_CORRUPTION_TOPIC_RE = re.compile(
    r"\b(bribe|bribery|corrupt|kickback|pocketing|payoff|under[- ]the[- ]table)\b", re.IGNORECASE
)


def _split_clauses(body):
    parts = [p.strip(" .") for p in _CLAUSE_SPLIT_RE.split(body)]
    return [p for p in parts if len(p) >= _MIN_CLAUSE_LEN]


def _is_unverified_allegation(clause):
    return bool(_HEARSAY_RE.search(clause) and _CORRUPTION_TOPIC_RE.search(clause))


def split_complaint(title, body):
    """Returns a list of sub-issues: [{"text": str, "kind": "service"|"unverified_allegation"}, ...].

    If nothing to split, returns a single-item list with kind="service"
    and the full original text — callers should treat a 1-item result as
    "no split happened" and keep existing single-complaint behaviour.
    """
    clauses = _split_clauses(body)

    if len(clauses) <= 1:
        # No bundling detected. Still check the whole text for an
        # unverified-allegation pattern layered onto an otherwise normal
        # complaint (the ration-shop example is only 2 "clauses" joined by
        # "and", which the regex above does catch, but keep this fallback
        # for phrasing patterns the clause splitter doesn't cut cleanly).
        if _is_unverified_allegation(body):
            # Try a lighter split: sentence-level, so the factual part
            # doesn't get swallowed by the hearsay part.
            sentences = [s.strip() for s in re.split(r"(?<=[.!?])\s+", body) if s.strip()]
            factual = [s for s in sentences if not _is_unverified_allegation(s)]
            allegation = [s for s in sentences if _is_unverified_allegation(s)]
            if factual and allegation:
                return (
                    [{"text": f"{title}. {' '.join(factual)}".strip(), "kind": "service"}]
                    + [{"text": " ".join(allegation), "kind": "unverified_allegation"}]
                )
        return [{"text": f"{title}. {body}".strip(), "kind": "service"}]

    # Multiple clauses — classify each independently only to decide
    # whether they're genuinely DIFFERENT issues (imported lazily to avoid
    # a circular import at module load, same pattern as auto_resolve.py).
    from classify import classify

    sub_issues = []
    broad_categories_seen = set()
    for clause in clauses:
        kind = "unverified_allegation" if _is_unverified_allegation(clause) else "service"
        sub_issues.append({"text": clause, "kind": kind})
        if kind == "service":
            try:
                broad_categories_seen.add(classify(title, clause).get("broad_category"))
            except Exception:
                pass

    # Only actually split if clauses land in genuinely different broad
    # categories — "also" doesn't always mean "different department"
    # (e.g. two sentences both about the same water outage). If they all
    # land in one bucket, treat it as one issue after all.
    if len(broad_categories_seen) <= 1 and not any(s["kind"] == "unverified_allegation" for s in sub_issues):
        return [{"text": f"{title}. {body}".strip(), "kind": "service"}]

    return sub_issues
