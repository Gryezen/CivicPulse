"""
Seed data — runs once at startup (see app.py) if the complaints table is
empty.

DEMO_QUEUE_COMPLAINTS mirrors track.html's old mockQueueComplaints, seeded
under a "demo citizen" system account so the public queue on
Complaints & Policies isn't empty on a fresh database. Real complaints
filed by real accounts show up alongside these.

dataset_sample_complaints() supplements those hand-written rows with real
rows sampled from data/grievance_simulated_dataset.csv, run through the
*actual* classify() (see classify.py) — including the corruption/threat
flags and low-confidence "Needs Human Review" bucket — so the demo queue
shows the new classifier really working, not just the hand-authored
examples above.

(Policy data used to be seeded here too — it now lives in
policies_data.json, seeded into the `policies` table by
policy_engine.seed_policies_if_empty().)
"""

import csv
import os
import random

_HERE = os.path.dirname(os.path.abspath(__file__))
_CSV_PATH = os.path.join(_HERE, "data", "grievance_simulated_dataset.csv")

_LANGUAGES = ["English", "Hindi", "Tamil", "Telugu", "Kannada", "Bengali", "Marathi"]
_STAGES = ["received", "processing", "assigned", "resolved"]

DEMO_ACCOUNT = {
    "name": "Demo Citizen",
    "email": "demo@civicpulse.local",
    "region": "Chennai, TN",
    "education": "Undergraduate",
    "employed": True,
    "occupation": "Demo account",
    "language": "English",
    "role": "citizen",
}

# For demoing /officer — a real deployment gates this role via department
# SSO/directory, not a signup form; this account exists purely so judges/
# reviewers can log in and see the officer dashboard without a manual DB
# edit. Password set via DEMO_OFFICER_PASSWORD env var, same pattern as
# DEMO_ACCOUNT_PASSWORD.
DEMO_OFFICER_ACCOUNT = {
    "name": "Duty Officer",
    "email": "officer@civicpulse.local",
    "region": "Chennai, TN",
    "education": "Postgraduate",
    "employed": True,
    "occupation": "Ward Grievance Officer",
    "language": "English",
    "role": "official",
}

# (title, body, category, department, authority, language, priority, stage, filed_at, files, note)
DEMO_QUEUE_COMPLAINTS = [
    dict(title="Open manhole near school gate, Anna Nagar",
         body="Uncovered manhole directly outside a primary school gate, first reported this morning. High foot traffic of children before 8am.",
         category="Public Safety", department="Municipal Corporation — Public Safety Cell",
         authority="Municipal Corporation Services", language="Tamil", priority=96, stage="processing",
         filed_offset_days=2, files_count=3,
         note="Flagged Urgent by the model due to child-safety keywords and proximity to a school. Escalated directly to the Public Safety Cell."),
    dict(title="Transformer sparking after rain, Sector 12",
         body="Visible sparking from a pole-mounted transformer since last night's rain. Residents have been asked to stay clear of the area.",
         category="Electricity", department="Electricity Board",
         authority="Electricity Board", language="Hindi", priority=91, stage="assigned",
         filed_offset_days=3, files_count=2,
         note="Assigned to an Electricity Board field engineer. Ranked Urgent on fire/electrocution risk terms detected in the complaint text."),
    dict(title="Streetlight not working on 4th Cross Road",
         body="Streetlight pole #14 on 4th Cross Road has been dark for over a week. Area is unsafe for pedestrians after 7pm.",
         category="Street Lighting", department="Ward Office",
         authority="Municipal Corporation Services", language="English", priority=58, stage="processing",
         filed_offset_days=7, files_count=2,
         note="Classified as a duplicate cluster with 6 similar reports and routed to the Ward Office. Expect an update within 48 hours."),
    dict(title="Sewage overflow onto main road, Kumaran Street",
         body="Sewage line overflow has been flowing onto the main carriageway for two days, causing a strong odour and health concerns.",
         category="Drainage & Sewage", department="Drainage & Sewage Board",
         authority="Drainage & Sewage Board", language="Tamil", priority=83, stage="received",
         filed_offset_days=4, files_count=4,
         note="Queued for AI triage — high priority pre-score due to public-health terms; awaiting confirmation from the model."),
    dict(title="Garbage not collected for 6 days, Ward 14",
         body="Household waste has piled up on the corner of Ward 14 since last Tuesday. Attracting stray animals.",
         category="Sanitation & Waste", department="Ward / Panchayat Office",
         authority="Ward / Panchayat Office", language="English", priority=47, stage="received",
         filed_offset_days=10, files_count=3,
         note="In the queue for AI triage. This usually takes under a minute — check back shortly."),
    dict(title="Pothole cluster causing two-wheeler accidents",
         body="A cluster of deep potholes near the flyover entrance has caused at least two reported two-wheeler falls this week.",
         category="Roads & Potholes", department="PWD (Roads)",
         authority="PWD (Roads)", language="Kannada", priority=72, stage="assigned",
         filed_offset_days=5, files_count=5,
         note="Ranked High on injury-risk and repeat-mention signals; assigned to PWD for inspection within 5 working days."),
    dict(title="Encroachment blocking footpath, MG Road",
         body="A vendor stall has expanded onto the footpath, forcing pedestrians onto the road.",
         category="Encroachment", department="Town Planning (Encroachment)",
         authority="Town Planning (Encroachment)", language="Telugu", priority=34, stage="received",
         filed_offset_days=8, files_count=1,
         note="Ranked Low relative to the current queue — no immediate safety signal detected — but retained for routing."),
    dict(title="Water supply pipeline leak near bus stand",
         body="Visible leak from the main pipeline opposite the bus stand, wasting water and flooding the walkway.",
         category="Water Supply", department="Public Sector Utility (Water Works)",
         authority="Public Sector Utility", language="English", priority=64, stage="resolved",
         filed_offset_days=23, files_count=1,
         note="Marked resolved by the Public Works Engineer. Reopen this complaint if the issue recurs."),
    dict(title="Streetlight flickering, minor — Gandhi Nagar",
         body="One streetlight flickers intermittently but still functions. Low urgency, filed for the record.",
         category="Street Lighting", department="Ward Office",
         authority="Municipal Corporation Services", language="Bengali", priority=21, stage="resolved",
         filed_offset_days=11, files_count=0,
         note="Resolved — bulb replaced during routine ward maintenance."),
]


def dataset_sample_complaints(n_general=18, n_corruption=6, n_threat=6, seed=42):
    """Sample rows from the simulated dataset, run them through classify(),
    and shape them like DEMO_QUEUE_COMPLAINTS entries. Imports classify()
    lazily (only called from inside app.py's app-context startup block) to
    avoid a hard dependency for anything that imports seed_data.py without
    needing this.
    """
    from classify import classify

    if not os.path.exists(_CSV_PATH):
        return []

    with open(_CSV_PATH, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    rng = random.Random(seed)
    corruption_rows = [r for r in rows if "corruption_flag" in r["suggested_workflow"]]
    threat_rows = [r for r in rows if "threat_flag" in r["suggested_workflow"]]
    general_rows = [r for r in rows if r not in corruption_rows and r not in threat_rows]

    picked = (
        rng.sample(general_rows, min(n_general, len(general_rows)))
        + rng.sample(corruption_rows, min(n_corruption, len(corruption_rows)))
        + rng.sample(threat_rows, min(n_threat, len(threat_rows)))
    )

    out = []
    for row in picked:
        title = row["case_name"].split(":", 1)[-1].strip() or row["case_name"]
        body = row["complaint_description"]
        result = classify(title, body)

        note = "Filed — queued for AI triage."
        if result.get("needs_review"):
            note = "Below the model's confidence threshold — held for human review rather than auto-routed."
        elif result.get("corruption_flag"):
            note = "Routed to Vigilance / Anti-Corruption; rerouted away from the named office; complainant identity withheld."
        elif result.get("threat_flag"):
            note = "Flagged for urgent safety review — bypassed the standard SLA queue."

        out.append(dict(
            title=title[:300],
            body=body,
            category=result["category"],
            department=result["department"],
            authority=result["authority"],
            language=rng.choice(_LANGUAGES),
            priority=result["priority"],
            stage=rng.choice(_STAGES),
            filed_offset_days=rng.randint(0, 30),
            files_count=rng.randint(0, 3),
            note=note,
            confidence=result.get("confidence"),
            needs_review=bool(result.get("needs_review")),
            corruption_flag=bool(result.get("corruption_flag")),
            threat_flag=bool(result.get("threat_flag")),
        ))
    return out
