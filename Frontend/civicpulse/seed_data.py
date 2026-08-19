"""
Seed data — runs once at startup (see app.py) if the complaints table is
empty.

DEMO_QUEUE_COMPLAINTS mirrors track.html's old mockQueueComplaints, seeded
under a "demo citizen" system account so the public queue on
Complaints & Policies isn't empty on a fresh database. Real complaints
filed by real accounts show up alongside these.

(Policy data used to be seeded here too — it now lives in
policies_data.json, served through policy_engine.py.)
"""

DEMO_ACCOUNT = {
    "name": "Demo Citizen",
    "email": "demo@civicpulse.local",
    "region": "Chennai, TN",
    "education": "Undergraduate",
    "employed": True,
    "occupation": "Demo account",
    "language": "English",
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
