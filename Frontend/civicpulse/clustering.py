"""
Duplicate / corroboration clustering.

Two distinct things this disambiguates, both raised in the ideation doc:

  1. "Repeat complaints" (doc recommendation #7): the SAME citizen filing
     a similar, still-open complaint again — usually frustration that
     nothing happened yet. Don't treat this as a fresh independent signal;
     mark it as a repeat and let the officer see it's the second+ time.

  2. "Spam vs. corroboration" (doc edge case #3): DIFFERENT citizens
     independently reporting what looks like the same underlying issue
     (a pothole, an outage, a specific officer) close together in time.
     That's a strong signal the issue is real and worth prioritising —
     the opposite of spam. Each such match bumps `corroboration_count` on
     the whole cluster.

Matching is TF-IDF cosine similarity over title+body, scoped to
open (non-resolved) complaints in the same DEPARTMENT filed within
CLUSTER_WINDOW_HOURS — department scoping matters more than the topic
model's fine category here, since two people describing the same pothole
rarely use identical vocabulary and department is the coarser, more
reliable match key. This is the same honest-simplification posture as the
rest of the codebase: no geolocation is captured yet (see the README's
"not yet built" list), so "same issue" is inferred from text + department
+ time window only, not distance. A future version with real lat/lng
would tighten this considerably.
"""

CLUSTER_WINDOW_HOURS = 96
SIMILARITY_THRESHOLD = 0.45
CANDIDATE_LIMIT = 300  # cap how many open complaints we'll vectorize per call, for latency on a big table

# --- coordinated-manipulation ("astroturfing") detection ------------------
# Doc's own framing: distinguish genuine corroboration from manufactured
# consensus "by lack of independent variation in phrasing and timing, not
# by volume alone." Two complaints about the same real pothole, written by
# two different people in their own words, will be SIMILAR but not
# NEAR-IDENTICAL. A templated astroturf campaign is near-identical AND
# compressed into a short window AND arrives in volume. All three,
# together, are what trips this — any one alone (e.g. a big cluster built
# up gradually over days) stays classified as ordinary corroboration.
ASTROTURF_SIMILARITY_THRESHOLD = 0.85
ASTROTURF_WINDOW_MINUTES = 90
ASTROTURF_MIN_COUNT = 8


def assign_cluster(new_complaint_id, user_id, title, body, department, filed_at):
    """Call AFTER the new Complaint row has been flushed (needs its id).
    Returns a dict of fields to set on that row:
        cluster_id, corroboration_count, is_repeat_filing
    and, when a corroborating (different-citizen) match was found, also
    returns `bump_cluster_ids` — other complaint ids in the same cluster
    whose corroboration_count should be updated to match, since the
    cluster's "how many people confirmed this" number should read the same
    on every member, not just the newest one.
    """
    from datetime import timedelta
    from models import Complaint

    window_start = filed_at - timedelta(hours=CLUSTER_WINDOW_HOURS)
    candidates = (
        Complaint.query
        .filter(
            Complaint.department == department,
            Complaint.stage != "resolved",
            Complaint.filed_at >= window_start,
            Complaint.id != new_complaint_id,
        )
        .order_by(Complaint.filed_at.desc())
        .limit(CANDIDATE_LIMIT)
        .all()
    )

    if not candidates:
        return {"cluster_id": new_complaint_id, "corroboration_count": 1, "is_repeat_filing": False, "bump_cluster_ids": [], "suspected_coordinated": False}

    try:
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.metrics.pairwise import cosine_similarity
    except Exception:
        return {"cluster_id": new_complaint_id, "corroboration_count": 1, "is_repeat_filing": False, "bump_cluster_ids": [], "suspected_coordinated": False}

    corpus = [f"{c.title} {c.body}" for c in candidates]
    query_text = f"{title} {body}"
    vectorizer = TfidfVectorizer(ngram_range=(1, 2), min_df=1)
    matrix = vectorizer.fit_transform(corpus + [query_text])
    sims = cosine_similarity(matrix[-1], matrix[:-1])[0]

    matches = [(candidates[i], float(sims[i])) for i in range(len(candidates)) if sims[i] >= SIMILARITY_THRESHOLD]
    if not matches:
        return {"cluster_id": new_complaint_id, "corroboration_count": 1, "is_repeat_filing": False, "bump_cluster_ids": [], "suspected_coordinated": False}

    matches.sort(key=lambda pair: pair[1], reverse=True)
    best_match, _ = matches[0]
    cluster_root = best_match.cluster_id or best_match.id

    same_user_match = any(c.user_id == user_id for c, _ in matches)

    # Distinct citizens across the whole cluster, including this new filer.
    cluster_members = Complaint.query.filter(Complaint.cluster_id == cluster_root).all()
    distinct_users = {c.user_id for c in cluster_members} | {best_match.user_id, user_id}
    corroboration_count = len(distinct_users)

    suspected_coordinated = _detect_astroturf(cluster_members, best_match, matches, filed_at)

    return {
        "cluster_id": cluster_root,
        "corroboration_count": corroboration_count,
        "is_repeat_filing": same_user_match,
        "bump_cluster_ids": [c.id for c in cluster_members],
        "suspected_coordinated": suspected_coordinated,
    }


def _detect_astroturf(cluster_members, best_match, matches, filed_at):
    """Returns True when this cluster looks like coordinated/templated
    submissions rather than organic corroboration. See the module-level
    comment for the reasoning — all three conditions (near-identical
    phrasing, compressed time window, enough volume to matter) must hold
    together; any single one alone is normal, legitimate behaviour."""
    all_members = cluster_members + [best_match]
    if len(all_members) < ASTROTURF_MIN_COUNT:
        return False

    timestamps = [m.filed_at for m in all_members if m.filed_at] + [filed_at]
    if len(timestamps) < 2:
        return False
    span_minutes = (max(timestamps) - min(timestamps)).total_seconds() / 60
    if span_minutes > ASTROTURF_WINDOW_MINUTES:
        return False

    # "Near-identical phrasing" — the best-match similarity score from the
    # caller already tells us how close the newest submission is to the
    # closest existing one; if even the single best match isn't near-
    # identical, this isn't templated content, regardless of volume/timing.
    top_similarity = matches[0][1] if matches else 0
    return top_similarity >= ASTROTURF_SIMILARITY_THRESHOLD
