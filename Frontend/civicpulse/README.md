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
  threat/audit-tier/low-confidence flagged.
- **Duplicate/corroboration clustering** (ideation doc gap #3 — the
  "40 residents, one signal" case) is **not built**. `broad_category` and
  `audit_tier` exist; cross-complaint clustering by geolocation/time
  window doesn't yet. Complaints don't currently store geolocation at all.
- **Two-party closure / CV-verified resolution** (gap #6) is not built —
  `stage` still moves to `resolved` on either an officer action or the
  self-resolution agent, not on citizen confirmation.
- **Severity vs. stated-urgency split** (gap #4) is not built — `priority`
  is still one blended number, not two separate scores.

## Architecture

| Layer | What it does |
|---|---|
| `models.py` | SQLAlchemy models: `User`, `Complaint`, `ClassificationLog`, `AutoResolutionLog`, `Policy` |
| `classify.py` | Classifies a complaint: TF-IDF + Logistic Regression (`classifier_model.joblib`, trained by `train_classifier.py`), with a keyword-rule fallback if the model file is missing. Also detects corruption/threat/audit-tier signals and builds the one-line officer brief. |
| `taxonomy.py` | Maps the classifier's ~20 fine categories to 5 broad top-level labels (Crime & Public Safety, Healthcare & Welfare, Infrastructure & Utilities, Corruption & Vigilance, General Governance) — adding a 6th broad label or remapping a fine category is a config change here, not a retrain. |
| `auto_resolve.py` | Confidence-gated self-resolution agent — see limitations above. |
| `complaints.py` | Citizen-facing complaint API: file, list mine, browse/search the public queue. |
| `officer.py` | Official-facing API: summary stats, triage-ordered queue, bulk assign/escalate/resolve, per-complaint audit trail. Gated by `User.role == "official"`, enforced server-side. |
| `policy_engine.py` | Loads/recommends civic schemes — now backed by the `policies` Postgres table (seeded once from `policies_data.json`), not read fresh off disk. |
| `seed_data.py` | First-boot seeding: demo accounts, hand-written demo complaints, plus a sample of real dataset rows run through the actual classifier so the demo queue isn't just 9 hand-written examples. |
| `train_classifier.py` | Offline script — run manually, not on every server boot — to (re)build `classifier_model.joblib`. |

## Pages

| Route | Purpose |
|---|---|
| `/` | Landing / pitch page |
| `/login` | Citizen login + register |
| `/complaint` | File a new complaint |
| `/dashboard` | Citizen dashboard — status, own complaints, recommended policies |
| `/track` | Public complaint queue + policy search, no admin required |
| `/officer` | Official triage dashboard — role-gated, see below |

## The officer dashboard (`/officer`)

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

Access is gated by `User.role == "official"` — set directly in the DB (or
via the seeded `officer@civicpulse.local` demo account); there's no
self-service upgrade path, deliberately, since a real deployment would use
department SSO/directory membership instead of a signup checkbox.

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

- Duplicate/corroboration clustering by geolocation + time window (gap #3)
- Two-party closure with CV-verified before/after photos (gap #6)
- Cross-grievance pattern memory / systemic-negligence alerts (gap #7)
- Severity vs. stated-urgency as two separate scores (gap #4)
- Service-failure vs. unverified-named-individual-allegation splitting (gap #5)
- SMS/IVR status-check for non-smartphone citizens
- Multilingual UI (complaints already carry a `language` field; the UI
  chrome itself is English-only)
