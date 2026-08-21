"""
Complaint classifier — v2.

Loads classifier_model.joblib (TF-IDF + Logistic Regression, trained by
train_classifier.py on data/grievance_simulated_dataset.csv) if it's
present next to this file. If it isn't — e.g. a fresh clone that hasn't
run `python train_classifier.py` yet, or the joblib/sklearn import fails
for any reason — classify() falls back to the original keyword-rule
heuristic below, so the app never crashes for lack of a model file.

Return shape is unchanged from v1 plus four new keys, so nothing calling
classify() elsewhere had to change:
    category, department, authority, priority   (as before)
    confidence      float 0-1, or None on the rules fallback
    needs_review    True when confidence is below CONFIDENCE_THRESHOLD
    corruption_flag / threat_flag   bool
    source          "model" or "rules" — which path produced this result

Honesty note: this model is trained on a *simulated* dataset (disclosed in
the README/pitch), and its held-out F1 on that dataset is close to 1.0 —
that's expected and not something to quote as-is in a pitch deck, because
the simulated rows are templated and cleaner than real complaints. Say so
plainly if asked for accuracy numbers, and prefer testing/quoting
performance on messier, hand-written examples instead.
"""

import os
import random

from taxonomy import broad_category

CONFIDENCE_THRESHOLD = 0.35

# Fatality/incident-already-happened language — see the ideation doc's
# "accident response delay, fatality" example. This is deliberately a
# blunt, high-precision-low-recall keyword check, not a model: a false
# negative here just means the case rides the normal SLA pipeline (safe
# default), whereas a false positive routes a routine complaint into the
# audit-tier queue an official would notice was miscategorised immediately.
# Tightening/replacing this with a proper classifier is future work, same
# caveat as classify.py's other simplifications.
_AUDIT_TIER_PHRASES = (
    "didn't survive", "did not survive", "passed away", "died", "death",
    "fatal", "fatality", "no longer alive", "killed",
)


def _detect_audit_tier(text_lower):
    return any(p in text_lower for p in _AUDIT_TIER_PHRASES)


def build_brief(category, priority, corruption_flag, threat_flag, audit_tier, needs_review):
    """One-line, always-present scan summary for the officer queue (see
    officer.py) — the point is that an official triaging 10,000+ tickets in
    a shift should be able to make a keep/skip decision from this line
    alone, without opening the full complaint body every time."""
    tags = []
    if audit_tier:
        tags.append("AUDIT TIER — incident already occurred, review not routine fix")
    if threat_flag:
        tags.append("safety escalation")
    if corruption_flag:
        tags.append("route to Vigilance, mask identity")
    if needs_review:
        tags.append("low-confidence, needs manual categorisation")
    band = "urgent" if priority >= 85 else ("high" if priority >= 60 else ("medium" if priority >= 35 else "low"))
    tag_text = f" · {' · '.join(tags)}" if tags else ""
    return f"{category} · {band} priority ({priority}){tag_text}"


# authority is department-shaped text already for CSV-derived categories,
# and department == authority is the same simplification v1 used — a real
# authority/escalation-contact lookup is future work (see the ideation
# doc's note on seeding a static department->contact table).
DEPARTMENT_OVERRIDES = {
    "Municipal Corporation - Sanitation": "Ward / Panchayat Office",
}

_HERE = os.path.dirname(os.path.abspath(__file__))
_MODEL_PATH = os.path.join(_HERE, "classifier_model.joblib")

_bundle = None
_load_attempted = False


def _get_bundle():
    global _bundle, _load_attempted
    if _load_attempted:
        return _bundle
    _load_attempted = True
    try:
        import joblib
        _bundle = joblib.load(_MODEL_PATH)
    except Exception:
        _bundle = None
    return _bundle


def _priority_from_signals(base_confidence, corruption, threat):
    priority = int(30 + base_confidence * 40)
    if threat:
        priority = max(priority, 90)
    if corruption:
        priority = max(priority, 80)
    return max(5, min(99, priority + random.randint(0, 5)))


def _score_stated_urgency(title, body):
    """How urgently THIS complainant wrote it — tone/formatting signals,
    independent of what the topic is about. Deliberately simple (caps
    ratio, exclamation marks, a small set of urgency words) rather than a
    trained model: the point (ideation doc gap #4) is that topic-driven
    severity and self-reported urgency need to be visibly different
    numbers on the complaint, not that the urgency signal itself needs to
    be sophisticated. 0-100.
    """
    text = f"{title} {body}"
    letters = [ch for ch in text if ch.isalpha()]
    caps_ratio = (sum(1 for ch in letters if ch.isupper()) / len(letters)) if letters else 0
    exclamations = text.count("!")
    urgency_words = sum(1 for w in ("urgent", "immediately", "emergency", "asap", "please help", "desperate")
                         if w in text.lower())

    score = 20 + caps_ratio * 120 + min(exclamations, 5) * 8 + urgency_words * 12
    return max(0, min(100, int(score)))


def _blend_priority(severity, urgency):
    """Final `priority` (what routing/SLA actually uses) blends the two,
    weighted toward severity — a calmly-worded genuine emergency should
    still outrank a melodramatically-worded trivial one. Both raw scores
    stay on the complaint (see Complaint.modeled_severity/stated_urgency)
    so an officer can see when tone and topic disagree."""
    blended = 0.72 * severity + 0.28 * min(urgency, 80)
    return max(5, min(99, int(round(blended))))


def _classify_with_model(title, body, bundle):
    text = f"{title} {body}"
    text_lower = text.lower()
    X = bundle["vectorizer"].transform([text])

    cat_model = bundle["category_model"]
    proba = cat_model.predict_proba(X)[0]
    best_idx = proba.argmax()
    category = str(cat_model.classes_[best_idx])  # numpy.str_ -> str, so jsonify()/SQLAlchemy see a plain str
    confidence = float(proba[best_idx])

    corruption = bool(bundle["corruption_model"].predict(X)[0])
    threat = bool(bundle["threat_model"].predict(X)[0])
    audit_tier = _detect_audit_tier(text_lower)

    needs_review = confidence < CONFIDENCE_THRESHOLD
    display_category = "Uncategorized / Needs Human Review" if needs_review else category
    department = DEPARTMENT_OVERRIDES.get(category, category)

    severity = _priority_from_signals(confidence, corruption, threat)
    if audit_tier:
        severity = max(severity, 95)
    urgency = _score_stated_urgency(title, body)
    priority = _blend_priority(severity, urgency)
    if audit_tier:
        priority = max(priority, 95)  # audit tier still forces the floor on the blended number

    return {
        "category": display_category,
        "modeled_severity": severity,
        "stated_urgency": urgency,
        "broad_category": broad_category(category, corruption_flag=corruption),
        "department": department if not needs_review else "Unassigned — pending review",
        "authority": department if not needs_review else "Unassigned — pending review",
        "priority": priority,
        "confidence": confidence,
        "needs_review": needs_review,
        "corruption_flag": corruption,
        "threat_flag": threat,
        "audit_tier": audit_tier,
        "source": "model",
    }


# --- v1 keyword-rule fallback (unchanged in spirit — kept as a safety net) --

CATEGORY_RULES = [
    {"category": "Public Safety", "department": "Municipal Corporation — Public Safety Cell",
     "authority": "Municipal Corporation Services",
     "keywords": ["danger", "safety", "accident", "manhole", "child", "school", "theft"], "boost": 20},
    {"category": "Electricity", "department": "Electricity Board",
     "authority": "Electricity Board",
     "keywords": ["transformer", "power", "electricity", "wire", "spark", "shock"], "boost": 15},
    {"category": "Water Supply", "department": "Public Sector Utility (Water Works)",
     "authority": "Public Sector Utility",
     "keywords": ["water", "pipeline", "tap", "supply"], "boost": 5},
    {"category": "Drainage & Sewage", "department": "Drainage & Sewage Board",
     "authority": "Drainage & Sewage Board",
     "keywords": ["drain", "sewage", "overflow", "flood"], "boost": 10},
    {"category": "Roads & Potholes", "department": "PWD (Roads)",
     "authority": "PWD (Roads)",
     "keywords": ["pothole", "road", "highway", "tar"], "boost": 8},
    {"category": "Street Lighting", "department": "Ward Office",
     "authority": "Ward Office",
     "keywords": ["streetlight", "light", "lamp", "bulb", "dark"], "boost": 0},
    {"category": "Sanitation & Waste", "department": "Ward / Panchayat Office",
     "authority": "Ward / Panchayat Office",
     "keywords": ["garbage", "waste", "trash", "sanitation", "collected"], "boost": 0},
    {"category": "Encroachment", "department": "Town Planning (Encroachment)",
     "authority": "Town Planning (Encroachment)",
     "keywords": ["encroachment", "footpath", "illegal", "stall", "vendor"], "boost": -5},
]

DEFAULT_RESULT = {
    "category": "General Grievance",
    "department": "Municipal Corporation Services",
    "authority": "Municipal Corporation Services",
}


def _classify_with_rules(title, body):
    text = f"{title} {body}".lower()

    best = None
    for rule in CATEGORY_RULES:
        hits = sum(1 for k in rule["keywords"] if k in text)
        if hits > 0 and (best is None or hits > best["hits"]):
            best = {**rule, "hits": hits}

    corruption = any(k in text for k in ("bribe", "bribery", "payment demanded", "extort"))
    threat = any(k in text for k in ("threat", "attack", "weapon", "assault"))
    audit_tier = _detect_audit_tier(text)

    if best is None:
        priority = 35 + random.randint(0, 14)
        result = {**DEFAULT_RESULT, "priority": priority}
    else:
        base = 45 + best["hits"] * 12 + best["boost"]
        priority = max(5, min(99, base + random.randint(0, 9)))
        result = {
            "category": best["category"],
            "department": best["department"],
            "authority": best["authority"],
            "priority": priority,
        }

    if threat:
        result["priority"] = max(result["priority"], 90)
    if corruption:
        result["priority"] = max(result["priority"], 80)
    if audit_tier:
        result["priority"] = max(result["priority"], 95)

    severity = result["priority"]  # rules path: severity and the raw pre-blend priority are the same signal
    urgency = _score_stated_urgency(title, body)
    blended_priority = _blend_priority(severity, urgency)
    if audit_tier:
        blended_priority = max(blended_priority, 95)

    result.update({
        "broad_category": broad_category(result["category"], corruption_flag=corruption),
        "priority": blended_priority,
        "modeled_severity": severity,
        "stated_urgency": urgency,
        "confidence": None,
        "needs_review": False,
        "corruption_flag": corruption,
        "threat_flag": threat,
        "audit_tier": audit_tier,
        "source": "rules",
    })
    return result


def classify(title, body):
    bundle = _get_bundle()
    if bundle is not None:
        try:
            result = _classify_with_model(title, body, bundle)
        except Exception:
            result = _classify_with_rules(title, body)
    else:
        result = _classify_with_rules(title, body)

    result["ai_brief"] = build_brief(
        result["category"], result["priority"], result["corruption_flag"],
        result["threat_flag"], result["audit_tier"], result["needs_review"],
    )
    return result
