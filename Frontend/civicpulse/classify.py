"""
Complaint classifier.

Stand-in for the real NLP model — a straight port of the keyword-rule
heuristic that used to live client-side in complaint.html's
`classifyComplaint()`. Moving it server-side means the category/department/
priority a complaint gets is now consistent and can't be spoofed by editing
the page's JS before submitting.

TODO(backend): swap classify() for a call into the actual PolicyGyaan/NLP
model once it exists — the return shape (category, department, priority)
is the contract the rest of the app expects, so nothing else has to change.
"""

import random

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


def classify(title, body):
    text = f"{title} {body}".lower()

    best = None
    for rule in CATEGORY_RULES:
        hits = sum(1 for k in rule["keywords"] if k in text)
        if hits > 0 and (best is None or hits > best["hits"]):
            best = {**rule, "hits": hits}

    if best is None:
        priority = 35 + random.randint(0, 14)
        return {**DEFAULT_RESULT, "priority": priority}

    base = 45 + best["hits"] * 12 + best["boost"]
    priority = max(5, min(99, base + random.randint(0, 9)))
    return {
        "category": best["category"],
        "department": best["department"],
        "authority": best["authority"],
        "priority": priority,
    }
