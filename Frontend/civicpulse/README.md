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

Demo logins are seeded automatically on first boot: a citizen account
(`demo@civicpulse.local`) and an official account (`officer@civicpulse.local`,
password from `DEMO_OFFICER_PASSWORD` env var) so you can see both the
citizen flow and `/officer` without creating accounts by hand.

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
- **Bundled-issue / unverified-allegation splitting** (`splitting.py`,
  gap #5 + the "structurally tricky" bundling case) is a clause-split +
  per-clause reclassification + a hearsay-keyword regex — not an NLP
  model. It will miss phrasings that don't use "also"/";"-style
  separators or the specific hearsay markers it checks for; missing a
  split just means the complaint stays as one blob (the safe failure
  mode), not a wrong split.
- **Severity vs. stated-urgency split** (`classify.py`'s
  `modeled_severity`/`stated_urgency`, gap #4) — urgency is scored from
  caps-ratio/exclamation-marks/a small urgency-word list, not sentiment
  analysis. It correctly ranks the doc's own two contrast cases (a
  dramatic EPF delay vs. a calm "no food for three days" ration
  complaint) lower/higher respectively — verified in this repo's own
  ad-hoc testing, not against a labelled urgency dataset.
- **Repeated-closure-dispute escalation** (`complaints.py`'s
  `/dispute` endpoint, gap #6) exists — reopening a resolved complaint
  twice forces `audit_tier`. What's still missing from full gap #6 is
  **two-party closure with CV-verified before/after photos** — closure
  today is still officer/agent-asserted, not photo-confirmed.
- **Policy auto-refresh** (`policy_ingest.py`) is a pluggable ingestion
  adapter (JSON file or URL), NOT a live web scraper — see that file's
  own docstring for why (the ideation doc argues against building a live
  scraper for officials' contact directories for the same reasons; the
  same logic applies to scheme data). The file-based adapter is tested
  end-to-end in this repo (`scripts/demo_policy_source.json`); the URL
  adapter is written but **has not been run against a real HTTP endpoint**
  in this sandbox (no network access) — verify it against your actual
  source before relying on it.

## Architecture

| Layer | What it does |
|---|---|
| `models.py` | SQLAlchemy models: `User`, `Complaint`, `ClassificationLog`, `AutoResolutionLog`, `Policy` |
| `classify.py` | Classifies a complaint: TF-IDF + Logistic Regression (`classifier_model.joblib`, trained by `train_classifier.py`), with a keyword-rule fallback if the model file is missing. Detects corruption/threat/audit-tier signals, scores `modeled_severity`/`stated_urgency` separately, and builds the one-line officer brief. |
| `taxonomy.py` | Maps the classifier's ~20 fine categories to 5 broad top-level labels (Crime & Public Safety, Healthcare & Welfare, Infrastructure & Utilities, Corruption & Vigilance, General Governance) — adding a 6th broad label or remapping a fine category is a config change here, not a retrain. |
| `splitting.py` | Splits a bundled multi-issue submission into separate routable issues, and separates an unverified allegation about a named individual from the factual/service part of the same complaint. |
| `clustering.py` | Duplicate/corroboration/astroturf detection — repeat filings, multi-citizen corroboration with a shared priority boost, and coordinated/templated-submission detection that withholds the boost pending human review. |
| `auto_resolve.py` | Confidence-gated self-resolution agent — see limitations above. |
| `complaints.py` | Citizen-facing complaint API: file (splits + clusters + auto-resolves as needed), dispute a resolved complaint, list mine, browse/search the public queue. |
| `officer.py` | Official-facing API: summary stats + systemic-pattern alerts, triage-ordered queue, bulk assign/escalate/resolve, per-complaint audit trail, policy-sync trigger. Gated by `User.is_official` (role AND verified), enforced server-side. |
| `policy_engine.py` | Loads/recommends civic schemes — backed by the `policies` Postgres table (seeded once from `policies_data.json`), not read fresh off disk. |
| `policy_ingest.py` | Keeps the `policies` table current from an external JSON feed — see limitations above. |
| `seed_data.py` | First-boot seeding: demo accounts (citizen + verified official), hand-written demo complaints, plus a sample of real dataset rows run through the actual classifier so the demo queue isn't just 9 hand-written examples. |
| `train_classifier.py` | Offline script — run manually, not on every server boot — to (re)build `classifier_model.joblib`. |

## Pages

| Route | Purpose |
|---|---|
| `/` | Landing / pitch page |
| `/login` | Citizen/official login + register (role picked at registration — see below) |
| `/complaint` | File a new complaint |
| `/dashboard` | Citizen dashboard — status, own complaints (with a "not actually fixed?" dispute action on resolved ones), recommended policies |
| `/track` | Public complaint queue + policy search, no admin required |
| `/officer` | Official triage dashboard — role-gated, see below |

## Officer accounts & verification

Officials can self-register on `/login` (Register tab → "Government
official"). This requires:
- Employee ID and department (free text, stored but not checked against
  any real HR system)
- A verification code matching the `OFFICIAL_VERIFICATION_CODE` env var

**What this honestly is:** a shared-secret gate against a random citizen
ticking "I'm an official" on the signup form — the code is meant to be
distributed to a department out-of-band (a circular, an onboarding
email). **What this is not:** real identity verification/KYC. A
production deployment should replace this with department SSO or a
manual-approval workflow — `User.is_verified` is kept as its own column
specifically so that swap doesn't require touching `role` or any of the
`is_official` call sites (see `models.py`'s comment on `is_official`).
If `OFFICIAL_VERIFICATION_CODE` is unset, official self-registration
fails closed rather than silently accepting any code.

## Officer dashboard (`/officer`)

Built for the "10,000+ tickets, 8-hour shift" problem the ideation doc
raises directly: the default view is **triage order, not submission
order** — audit-tier and threat-flagged cases first, then corruption-
flagged, then everything else by priority — and every row leads with the
one-line AI brief (`Complaint.ai_brief`), not the full complaint body, so
an official can make a keep/skip decision without opening each ticket.
Auto-resolved cases are hidden by default (a queue of what the agent
already closed isn't useful triage material). Supports bulk assign/
escalate/resolve across a selection, and a per-complaint "View audit
trail" panel showing every classification and auto-resolution decision
with its reasoning and confidence — the explainability feature from gap
#10.

Access is gated by `User.is_official` (role == "official" AND
`is_verified` — see "Officer accounts & verification" above); there's no
self-service upgrade path from citizen to official after registration,
deliberately, since a real deployment would use department SSO/directory
membership rather than an account-settings toggle.

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

- **CV-verified two-party closure** (gap #6) — the reopen/dispute
  escalation half of this gap is built (see `complaints.py`'s
  `/dispute` endpoint); photo-verified before/after closure is not.
- SMS/IVR status-check for non-smartphone citizens
- Multilingual UI (complaints already carry a `language` field; the UI
  chrome itself is English-only)
- Real identity/KYC verification for officials — see "Officer accounts &
  verification" above for what's actually implemented instead
- Coordination-pattern detection beyond the phrasing+timing+volume
  heuristic in `clustering.py` — no network-graph/account-level analysis
  (shared IPs, account creation bursts, etc.), text-only

Everything else the ideation doc calls out by number is built — see the
"What's real vs. simplified" section above for the honest caveats on each:
gap #3 (corroboration + astroturf clustering), #4 (severity/urgency
split), #5 (bundled-issue + unverified-allegation splitting), #7
(systemic pattern alerts), plus the 5-broad-category taxonomy, the
officer dashboard, and the self-resolution agent from the doc's own
action-plan paragraph.

