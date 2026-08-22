# CivicPulse

Built for SIH 2026 Internals, PS97 (Citizen Grievance Classification using AI) · Team Gryezen

A working Flask + Postgres (Supabase) grievance triage app: citizens file
complaints, a trained classifier routes and prioritises them (with a
corruption/threat/audit-tier routing fork), a confidence-gated agent
auto-resolves the routine ones, and officials get a dedicated triage
dashboard. See `Civic_Pulse_ideation.md` for the research this is built
against — most features below map directly to a numbered gap or phase in
that document.

## Quick start

```bash
pip install -r requirements.txt
python train_classifier.py   # builds classifier_model.joblib from data/grievance_simulated_dataset.csv — run once, and again any time that CSV changes
python app.py
```

Then open `http://127.0.0.1:5000/`. Without `DATABASE_URL` set, it falls
back to a local `instance/civicpulse.db` sqlite file (auto-created) — set
`DATABASE_URL` to your Supabase Postgres connection string to use that
instead. `SECRET_KEY` is required in production; a dev default is used if
unset. `GOOGLE_API_KEY` is optional — only used for the Gemini-backed
policy recommender in `policy_engine.py`, which falls back to a plain
keyword-overlap ranking if unset.

Demo logins are seeded automatically on first boot (all passwords from
`DEMO_OFFICER_PASSWORD` / `DEMO_ADMIN_PASSWORD` env vars, or the printed
fallback if unset):
- `demo@civicpulse.local` — citizen
- `officer@civicpulse.local` — verified official, see `/officer`
- `pending-officer@civicpulse.local` — official stuck in `pending_review` (no ID document attached), for exercising `/admin` without registering a new account by hand
- `admin@civicpulse.local` — admin, see `/admin`

**⚠️ Before you commit or share this repo again:** `.env` is in
`.gitignore`, but if you've ever zipped/shared the working directory
including it, rotate the Supabase DB password and the Gemini API key —
both are plaintext secrets, and a leaked one should be treated as
compromised regardless of how it leaked.

## What's real vs. simplified — read this before a demo

Everything below actually runs — nothing is mocked in the sense the old
version of this README described. But some pieces are intentionally
simplified for a hackathon scope, and pretending otherwise is exactly the
"claims automation that doesn't exist" failure mode `Civic_Pulse_ideation.md`
flagged in the original codebase audit. Be upfront about these if asked:

- **The classifier is trained on a *simulated* dataset**
  (`data/grievance_simulated_dataset.csv`, 10k rows). Held-out F1 is
  ~0.98–1.0, which is expected — the simulated rows are templated and
  cleaner than real complaints will ever be. Don't quote that number as a
  real-world accuracy claim; test against messier hand-written examples
  instead (see `train_classifier.py`'s docstring).
- **The self-resolution agent** (`auto_resolve.py`) compares a new
  complaint to whatever's already in *this deployment's* `complaints`
  table with `stage == "resolved"` — on a fresh install, that's the
  seeded demo/dataset rows, not a large corpus of real outcomes. It only
  acts on a conservative allowlist of low-stakes categories (street
  lighting, sanitation, roads, water) and never on anything corruption/
  threat/audit-tier/low-confidence/corroborated/repeat/coordinated-flagged.
- **Duplicate/corroboration/astroturf clustering** (`clustering.py`, gap
  #3 + the astroturfing edge case) matches by TEXT SIMILARITY + DEPARTMENT
  + TIME WINDOW only — there is no geolocation field on `Complaint` at
  all, so "same issue" is inferred from wording, not physical proximity. A
  real deployment capturing lat/lng would tighten this considerably.
  Astroturf detection specifically requires all three of near-identical
  phrasing + a compressed (90-min) window + volume (8+) — it's a coarse
  heuristic, not a trained detector, and is designed to hold-for-review
  rather than auto-dismiss, so a false positive costs a human glance, not
  a suppressed real signal.
- **Repeated-closure-dispute escalation + two-party photo closure**
  (`complaints.py`'s `/dispute` and `/confirm`, `officer.py`'s
  `resolve-with-photo`, gap #6) — an official resolving a complaint (with
  or without a photo) only ever sets `pending_confirmation`; only the
  CITIZEN's own confirm/dispute actually closes or reopens it, and two
  disputes forces `audit_tier`. The photo "verification" is an
  average-hash (aHash) perceptual comparison via Pillow — it answers "is
  the after-photo materially different from the before-photo" (catches
  re-submitting the same photo), NOT "was the reported issue actually
  fixed" (that needs a trained per-category CV model — object detection
  for potholes/garbage/etc. — which is not built). A known aHash
  limitation: two different but visually flat/uniform photos can hash
  identically. The similarity score is shown to the citizen as context,
  never used to auto-gate closure — see `uploads.py`'s own docstring.
- **Policy auto-refresh** (`policy_ingest.py`) is a pluggable ingestion
  adapter (JSON file or URL), NOT a live web scraper — see that file's
  own docstring for why (the ideation doc argues against building a live
  scraper for officials' contact directories for the same reasons; the
  same logic applies to scheme data). The file-based adapter is tested
  end-to-end in this repo (`scripts/demo_policy_source.json`); the URL
  adapter is written but **has not been run against a real HTTP endpoint**
  in this sandbox (no network access) — verify it against your actual
  source before relying on it.
- **SMS/IVR status-check** (`ivr.py`) is the message-handling LOGIC a
  real gateway webhook (Twilio, Exotel, etc.) would call — command
  parsing, phone-to-account lookup, plain-text reply. **No SMS or call
  is actually sent by anything in this repo.** Try it via `/sms-demo`, an
  in-app chat widget that exercises the same handler without a telecom
  account. Wiring the real inbound webhook to a specific gateway's
  request/response contract is a thin adapter on top of
  `ivr.handle_inbound()`, not built here.
- **Officer identity verification** (`admin.py`) has two paths: a shared
  department code (instant, see "Officer accounts & verification" below)
  or an uploaded ID-document photo reviewed by a human admin. **Neither
  is real KYC** — the admin review is a person visually sanity-checking
  that a plausible-looking document was provided, not verification
  against any government database, and there's no OCR or forgery
  detection. Say so plainly if asked; see `admin.py`'s own docstring.
- **Wellbeing-risk detection** (`classify.py`'s `_detect_wellbeing_risk`,
  the ideation doc's "pension complaint burying a suicidal-ideation
  sentence" extreme case) is a coarse, high-precision-low-recall phrase
  check, same posture as the existing `audit_tier` detector — not a
  clinical or diagnostic model. It never talks back to the citizen about
  it beyond a calm routing note, and it does exactly one thing: holds the
  complaint for a trained human to check in on, on top of (not instead
  of) the underlying civic issue still being handled.
- **Same-filer repeated-targeting pattern** (`clustering.py`'s
  `check_filer_pattern`, the ideation doc's "shopkeeper files fake
  complaints against a rival every festival season" gaming case) —
  honesty note: this can't actually detect "always targets the same
  rival," since there's no named-entity extraction here. What it checks
  instead is the weaker but still useful proxy the doc itself names as
  the differentiator: enough same-category complaints from one filer that
  no other citizen has ever corroborated. Flags for human review; never
  auto-rejects on this signal alone.
- **Mock CPGRAMS-shaped integration bridge**
  (`cpgrams_integration.py`, doc section 3.9) is a REST surface shaped
  like a plausible CPGRAMS-to-CivicPulse ingestion API — field names and
  response envelope modelled on CPGRAMS' own public fields — **not a
  working connection to the real government system.** Every ingested
  grievance runs through the exact same classify → cluster → auto-resolve
  pipeline a citizen's own submission does, filed under a system bridge
  account. Disabled by default (fails closed if `CPGRAMS_INTEGRATION_KEY`
  is unset).

## Architecture


| Layer | What it does |
|---|---|
| `models.py` | SQLAlchemy models: `User`, `Complaint`, `ClassificationLog`, `AutoResolutionLog`, `Policy` |
| `classify.py` | Classifies a complaint: TF-IDF + Logistic Regression (`classifier_model.joblib`, trained by `train_classifier.py`), with a keyword-rule fallback if the model file is missing. Detects corruption/threat/audit-tier signals, scores `modeled_severity`/`stated_urgency` separately, and builds the one-line officer brief. |
| `taxonomy.py` | Maps the classifier's ~20 fine categories to 5 broad top-level labels (Crime & Public Safety, Healthcare & Welfare, Infrastructure & Utilities, Corruption & Vigilance, General Governance) — adding a 6th broad label or remapping a fine category is a config change here, not a retrain. |
| `splitting.py` | Splits a bundled multi-issue submission into separate routable issues, and separates an unverified allegation about a named individual from the factual/service part of the same complaint. |
| `clustering.py` | Duplicate/corroboration/astroturf detection — repeat filings, multi-citizen corroboration with a shared priority boost, and coordinated/templated-submission detection that withholds the boost pending human review. |
| `auto_resolve.py` | Confidence-gated self-resolution agent — see limitations above. |
| `uploads.py` | Local photo storage + lightweight perceptual-hash comparison, backing two-party photo-verified closure and official ID-document uploads — see limitations above. |
| `complaints.py` | Citizen-facing complaint API: file (splits + clusters + auto-resolves as needed, optional before-photo), confirm/dispute a resolution, list mine, browse/search the public queue. |
| `officer.py` | Official-facing API: summary stats + systemic-pattern alerts, triage-ordered queue, bulk assign/escalate/resolve, photo-evidence resolve, per-complaint audit trail, policy-sync trigger. Gated by `User.is_official` (role AND verified), enforced server-side. |
| `admin.py` | Admin review queue for officials stuck in `pending_review` — approve/reject. Gated by `User.is_admin`. |
| `ivr.py` | SMS/IVR status-check message handling — see limitations above. |
| `policy_engine.py` | Loads/recommends civic schemes — backed by the `policies` Postgres table (seeded once from `policies_data.json`), not read fresh off disk. |
| `policy_ingest.py` | Keeps the `policies` table current from an external JSON feed — see limitations above. |
| `seed_data.py` | First-boot seeding: demo accounts (citizen, verified official, pending official, admin), hand-written demo complaints, plus a sample of real dataset rows run through the actual classifier so the demo queue isn't just 9 hand-written examples. |
| `train_classifier.py` | Offline script — run manually, not on every server boot — to (re)build `classifier_model.joblib`. |

## Pages


| Route | Purpose |
|---|---|
| `/` | Landing / pitch page |
| `/login` | Citizen/official login + register (role picked at registration — see below) |
| `/complaint` | File a new complaint (optional before-photo) |
| `/dashboard` | Citizen dashboard — status, own complaints (confirm/dispute a resolution, "not actually fixed?"), recommended policies |
| `/track` | Public complaint queue + policy search, no admin required |
| `/officer` | Official triage dashboard — role-gated, see below |
| `/admin` | Admin review queue for pending official verifications — role-gated |
| `/sms-demo` | In-app chat widget simulating the SMS/IVR status-check channel |

## Officer accounts & verification

Officials can self-register on `/login` (Register tab → "Government
official"), supplying Employee ID and department (free text, not checked
against any real HR system) plus ONE of two paths:

1. **Fast track** — a verification code matching `OFFICIAL_VERIFICATION_CODE`.
   Verifies instantly (`verification_status = "auto_verified"`).
2. **Admin review** — no code (or a wrong one), but an ID document photo
   attached instead. Registration still succeeds, but the account sits
   at `verification_status = "pending_review"` — `is_verified` stays
   `False`, so `/officer` stays inaccessible — until an admin approves it
   from `/admin` (see below). If neither a valid code nor a document is
   given, registration is rejected outright (nothing for an admin to
   review).

**What this honestly is:** two different strengths of gate against a
random citizen ticking "I'm an official" — a shared department secret, or
a human looking at a photo of *something* the registrant claims is their
ID. **What this is not, either way:** real identity verification/KYC —
no government-database check, no OCR, no forgery detection. A production
deployment should replace both with department SSO — `User.is_verified`
is kept as its own column specifically so that swap doesn't require
touching `role` or any of the `is_official` call sites (see `models.py`'s
comment on `is_official`). If `OFFICIAL_VERIFICATION_CODE` is unset, only
the fast-track path is disabled — the document + admin-review path still
works.

## Admin review (`/admin`)

Lists every account with `verification_status == "pending_review"` —
their employee ID, department, and (if provided) their uploaded ID
document photo — with Approve/Reject buttons. Approve sets
`is_verified = True` (unlocks `/officer` immediately); reject leaves it
`False` permanently with `verification_status = "rejected"`, keeping the
account around for audit rather than deleting it.

Gated by `User.is_admin` (`role == "admin"`) — a third tier above
official, seeded directly (`admin@civicpulse.local`, see Quick start)
and never self-registerable through any form in this app. A real
deployment would manage admin access the same way it manages any other
privileged internal tooling — directly, not through public signup.

## Officer dashboard (`/officer`)

Built for the "10,000+ tickets, 8-hour shift" problem the ideation doc
raises directly: the default view is **triage order, not submission
order** — audit-tier and threat-flagged cases first, then corruption-
flagged, then everything else by priority — and every row leads with the
one-line AI brief (`Complaint.ai_brief`), not the full complaint body, so
an official can make a keep/skip decision without opening each ticket.
Auto-resolved cases are hidden by default (a queue of what the agent
already closed isn't useful triage material). Supports bulk assign/
escalate/resolve across a selection, resolving with photo evidence (see
below), and a per-complaint "View audit trail" panel showing every
classification and auto-resolution decision with its reasoning and
confidence — the explainability feature from gap #10.

Access is gated by `User.is_official` (role == "official" AND
`is_verified` — see "Officer accounts & verification" above); there's no
self-service upgrade path from citizen to official after registration,
deliberately, since a real deployment would use department SSO/directory
membership rather than an account-settings toggle.

## Two-party photo-verified closure (gap #6)

An official resolving a complaint — via the officer dashboard's bulk
"resolve" action, or "Resolve with after-photo" — never directly sets
`stage = "resolved"`. It sets `pending_confirmation = True`, and the
complaint sits there until the CITIZEN who filed it explicitly confirms
(`POST /api/complaints/<id>/confirm`, a "Confirm fixed" button on
`/dashboard`) or disputes it (`/dispute`). Two disputes on the same
complaint force `audit_tier = True` instead of looping the same
resolve/dispute cycle indefinitely.

The photo side (`uploads.py`) computes an average-hash (aHash) similarity
between a complaint's before-photo (optional, attached at filing) and
after-photo (attached when an official resolves with evidence) — this
flags "the after-photo looks suspiciously similar to the before-photo"
for the citizen's attention, but **never** auto-gates the closure itself.
See `uploads.py`'s docstring for exactly what this hash comparison does
and doesn't verify (short version: image similarity, not "was the
pothole actually filled").

## SMS/IVR status-check

`/sms-demo` is an in-app chat widget simulating what a citizen without
the app would experience over plain SMS — it calls the exact same
`ivr.handle_inbound()` logic a real gateway webhook would call. Link a
phone number from `/account` first, then text `STATUS` (or `HELP`) in
the demo. **No real SMS is sent anywhere in this repo** — see `ivr.py`'s
docstring for what would be needed to wire this to an actual carrier
(Twilio/Exotel/etc.), which needs a real gateway account this sandbox has
no way to obtain or test against.

The real inbound webhook (`/webhook/ivr/inbound`) is fully separate from
the demo endpoint and requires `IVR_WEBHOOK_SECRET` — fails closed if
unset, same pattern as `OFFICIAL_VERIFICATION_CODE`.

## Policy auto-refresh

`POST /api/officer/policies/sync` (officer-only) triggers
`policy_ingest.py` against either a `"source"` in the request body or the
`POLICY_SOURCE_URL` env var. See that file's docstring for the expected
JSON shape and — importantly — the honest caveat that the URL adapter
hasn't been run against a live source in this environment. Try it first
with the bundled demo file:

```bash
python policy_ingest.py --source scripts/demo_policy_source.json
```


## Design system — Government Grievance Portal

- **Palette**: white/off-white background, near-black ink text for
  contrast. Navy (`--navy`, `#0B2F8F`) carries all interactive/primary UI —
  links, buttons, focus. Saffron and green are reserved for status meaning
  and the letterhead strip, not decoration; red is reserved for
  urgent/error states. All tokens live at the top of `style.css` as CSS
  custom properties.
- **Type**: system font stack only (`-apple-system, "Segoe UI", Roboto,
  "Noto Sans", Arial`), no webfont loading, larger base size (106%) for
  legibility. A monospace stack is used only for functional data — docket
  numbers, stamps, field labels — never for decoration.
- **Signature motif**: a thin saffron/white/green rule (`.tricolour-rule`)
  fixed at the very top of every page.
- **Motion**: kept to a minimum — short (0.15–0.2s) color/border
  transitions for interactive feedback, plus a strong 3px focus outline
  everywhere. `prefers-reduced-motion` is respected.
- **Shape**: flat, boxy — 2px border-radius throughout, 2px solid borders
  instead of soft shadows.

## Not yet built

Straight from the ideation doc's own prioritised gap list — see that
document for the full reasoning behind each:

- Multilingual UI (complaints already carry a `language` field; the UI
  chrome itself is English-only)
- Real government-database identity/KYC verification for officials —
  both current paths (shared code, admin-reviewed document) are disclosed
  as prototype-grade, not real KYC — see "Officer accounts & verification"
  above
- A real telecom connection for the SMS/IVR channel — `ivr.py`'s logic
  works, `/sms-demo` proves it out, but no gateway account exists to wire
  it to an actual phone number
- Coordination-pattern detection beyond the phrasing+timing+volume
  heuristic in `clustering.py` — no network-graph/account-level analysis
  (shared IPs, account creation bursts, etc.), text-only
- Named-entity extraction (recognising WHO or WHAT BUSINESS a complaint
  is about) — `check_filer_pattern`'s targeting-pattern detection uses a
  no-corroboration-history proxy instead, since there's no entity
  recognition to check "always names the same rival" directly
- A trained CV model for judging whether a reported issue was actually
  fixed (a pothole-filled classifier, a garbage-cleared classifier, etc.)
  — the photo closure feature only checks before/after visual similarity,
  not issue-specific resolution — see "Two-party photo-verified closure"
  above
- A scheduled/cron-triggered auto-confirm timeout for `pending_confirmation`
  complaints a citizen never responds to — today they wait indefinitely
  for the citizen's confirm/dispute
- A real connection from `cpgrams_integration.py` to the actual CPGRAMS
  system — see that file's own docstring; it's a mock built to a
  plausible shape, not a live integration, and CPGRAMS' real auth scheme
  hasn't been reverse-engineered here

Everything else the ideation doc calls out by number is built — see the
"What's real vs. simplified" section above for the honest caveats on each:
gap #1's wellbeing-risk routing (the extreme "buried distress signal"
case), gap #3 (corroboration + astroturf clustering + same-filer
targeting patterns), #4 (severity/urgency split), #5 (bundled-issue +
unverified-allegation splitting), #6 (two-party photo-verified closure +
dispute escalation), #7 (systemic pattern alerts), plus the
5-broad-category taxonomy, the officer dashboard, the self-resolution
agent, SMS/IVR status-check, the mock CPGRAMS ingestion bridge (doc
section 3.9), the DPDP data-handling page (doc section 3.8, see
`/privacy`), and policy auto-refresh from the doc's own action-plan
paragraph.

