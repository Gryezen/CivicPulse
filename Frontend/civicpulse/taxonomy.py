"""
Two-tier category taxonomy.

The classifier (classify.py) outputs a *fine* category — one of the ~20
department-shaped labels learned from data/grievance_simulated_dataset.csv
(e.g. "Electricity Board", "Police Department", "Pension / EPFO"). That's
the right granularity for *routing* (which department gets the ticket),
but it's too granular for an official who needs to triage 10,000+ tickets
in a shift and think in terms of "how many Crime issues today" rather than
"how many Police Department vs. Police Department/Transport issues".

BROAD_CATEGORIES is the top layer: a small, fixed list an official can
hold in their head. Five for now, per the brief — adding a sixth is a
config change here, not a retraining or architecture change (the doc's
own scalability talking point). Every fine category must map to exactly
one broad label; unmapped fine categories fall back to the "General
Governance" catch-all rather than raising, so a future label added to the
training data doesn't crash the app until someone maps it.
"""

BROAD_CATEGORIES = [
    "Crime & Public Safety",
    "Healthcare & Welfare",
    "Infrastructure & Utilities",
    "Corruption & Vigilance",
    "General Governance",
]

_FINE_TO_BROAD = {
    # Crime & Public Safety
    "Police Department": "Crime & Public Safety",
    "Police Department / Transport": "Crime & Public Safety",
    "Police Department (Cyber Cell)": "Crime & Public Safety",
    "Fire & Emergency Services": "Crime & Public Safety",
    "Public Safety": "Crime & Public Safety",  # v1 keyword-rule fallback category

    # Healthcare & Welfare
    "Health Department / Hospital": "Healthcare & Welfare",
    "Women & Child Welfare": "Healthcare & Welfare",
    "Public Distribution System (Ration)": "Healthcare & Welfare",
    "Animal Husbandry / Municipal (Strays)": "Healthcare & Welfare",
    "Pension / EPFO": "Healthcare & Welfare",

    # Infrastructure & Utilities
    "Electricity Board": "Infrastructure & Utilities",
    "Water Supply / Sanitation": "Infrastructure & Utilities",
    "Roads & PWD": "Infrastructure & Utilities",
    "Roads & PWD / Electricity Board": "Infrastructure & Utilities",
    "Municipal Corporation - Sanitation": "Infrastructure & Utilities",
    "Housing & Urban Development": "Infrastructure & Utilities",
    "Environment / Pollution Control": "Infrastructure & Utilities",
    "Transport / RTO": "Infrastructure & Utilities",
    "Education Department": "Infrastructure & Utilities",
    # v1 keyword-rule fallback categories
    "Water Supply": "Infrastructure & Utilities",
    "Drainage & Sewage": "Infrastructure & Utilities",
    "Roads & Potholes": "Infrastructure & Utilities",
    "Street Lighting": "Infrastructure & Utilities",
    "Sanitation & Waste": "Infrastructure & Utilities",
    "Encroachment": "Infrastructure & Utilities",

    # Corruption & Vigilance
    "Vigilance / Anti-Corruption Bureau": "Corruption & Vigilance",

    # General Governance (catch-all)
    "Revenue Department / Land Records": "General Governance",
    "Banking / Financial Services": "General Governance",
    "Multiple Departments (Split Required)": "General Governance",
    "General Grievance": "General Governance",
    "Uncategorized / Needs Human Review": "General Governance",
    "Municipal Corporation Services": "General Governance",
}


def broad_category(fine_category, corruption_flag=False):
    """Map a fine category to one of the 5 broad labels.

    corruption_flag overrides the fine-category mapping — a bribery
    allegation about a pothole is a Corruption & Vigilance issue for
    triage purposes, not an Infrastructure one, regardless of what the
    topic classifier said (this mirrors classify.py's own routing fork).
    """
    if corruption_flag:
        return "Corruption & Vigilance"
    return _FINE_TO_BROAD.get(fine_category, "General Governance")
