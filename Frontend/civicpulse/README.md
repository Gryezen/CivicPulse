# CivicPulse — Frontend Prototype

Built for SIH 2026 Internals, PS97 (Citizen Grievance Classification using AI) · Team Gryezen

## Quick start (demo shell)

A temporary Flask app is included purely so the team can click through the
flow before the real backend exists. It is **not** the CivicPulse API —
just a static-page server with clean URLs.

```bash
pip install flask
python app.py
```

Then open `http://127.0.0.1:5000/`. Routes: `/`, `/login`, `/complaint`,
`/dashboard`, `/track` (the `.html` links used inside the pages also work,
e.g. `/dashboard.html`).

To open the HTML files directly with no server at all, that still works too
— `templates/*.html` reference `style.css`/`main.js` with plain relative
paths, so `python3 -m http.server` from inside `templates/` (with `style.css`
and `main.js` copied alongside) or any static host is fine.

## Pages

| File                    | Purpose                                                            |
|--------------------------|---------------------------------------------------------------------|
| `templates/index.html`   | Landing / pitch page — hero, speed/accuracy/transparency pillars, how-it-works trail |
| `templates/login.html`   | Citizen login + register (tabbed)                                  |
| `templates/complaint.html` | File a new complaint — title, date range, authority level, body, proof upload |
| `templates/dashboard.html` | Citizen dashboard — status stats, complaint list, policy recommendations |
| `templates/track.html`   | Public, no-login complaint tracker by docket ID (`?id=CP-XXXX` deep-linkable) |
| `static/style.css`       | Shared design system — tokens, letterhead, nav, buttons, forms, cards, stamps |
| `static/main.js`         | Shared utilities — toast, drag-and-drop file upload                |
| `app.py`                 | Temporary Flask demo shell (routing only — see above)              |

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
  fixed at the very top of every page — a plain "letterhead" device, the
  one deliberately official visual cue, kept disciplined rather than
  reproducing any government emblem.
- **Motion**: kept to a minimum. No scroll-reveal, no looping/decorative
  animation, no backdrop blur, no hover-lift. What remains is short
  (0.15–0.2s) color/border transitions for interactive feedback, plus a
  strong 3px focus outline everywhere. `prefers-reduced-motion` is
  respected as a fallback.
- **Shape**: flat, boxy — 2px border-radius throughout (circles stay
  circular where they're literally circular, e.g. avatars), 2px solid
  borders instead of soft shadows.

## Wiring to the real backend

Every place a real API call belongs is marked with a comment:

```js
// TODO(backend): POST /api/create/complaint  → send `payload` as multipart/form-data with files
```

Search each HTML file for `TODO(backend)` to find all of them. Per the API
design doc, the routes to wire in are:

- `POST /api/auth/` — used by `login.html` (both login and register forms)
- `POST /api/create/complaint` — used by `complaint.html`
- `GET /api/policies/` — used by `dashboard.html` (Recommended for you)
- `GET /api/admin/view/complaint/<complaintID>` — used by `track.html` and the
  dashboard's complaint list (currently mocked with 3 sample dockets:
  `CP-4821`, `CP-4790`, `CP-4602`)

All mock data is declared as plain JS objects/arrays near the top of each
page's `<script>` block — swap the mock for a `fetch()` call and the
rendering functions underneath don't need to change. Once real routes exist,
`app.py`'s page routes are the natural place to start passing real data into
`render_template(...)` instead of leaving it to client-side JS.

## Not yet built

- Moderator/official-facing views (`/api/admin/approve/plan/<complaintID>`,
  admin auth, user lookup by ID/email)
- WhatsApp chat-based reporting flow (mentioned in the pitch, not the web UI)
- PolicyGyaan-specific integration beyond the mocked recommendation cards
- Multilingual support (flagged in the pitch deck — plan for it in `<html
  lang>` / a strings file before it's bolted on later)
