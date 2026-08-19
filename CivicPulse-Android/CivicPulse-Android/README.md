# CivicPulse — Android app

A native Android client for CivicPulse, built alongside the web app. This
lives in its own top-level folder (`CivicPulse-Android/`) and doesn't touch
anything under `Frontend/civicpulse/` (or the flat `civicpulse/` layout the
backend branch uses), so it merges in independently.

## Stack

- **Kotlin + Jetpack Compose** (Material 3) — single-activity, one NavHost
- **Retrofit + OkHttp + kotlinx.serialization** for networking
- **A persistent cookie jar** (SharedPreferences-backed, field-level
  serialization) — the backend uses session-based Flask-Login auth, so the
  app carries the session cookie the same way a browser would, and it
  survives app restarts
- **DataStore** for local settings (server URL, cached display name)
- No Hilt/Dagger — dependencies are wired by hand in `CivicPulseApp.kt` and
  `ui/navigation/ViewModelFactory.kt`
- minSdk 24 (Android 7.0+), targetSdk 34

## Opening the project

1. Android Studio (Koala or newer) → **Open** → select `CivicPulse-Android/`.
2. Let Gradle sync.
3. Run on an emulator or device. Debug builds default to
   `http://10.0.2.2:5000/` (the emulator's alias for the host machine's
   localhost); release builds default to `https://civicpulse.onrender.com/`.

### Pointing it at a real backend

Open the app → Account → "Developer options — server settings" → enter a
base URL → Save. No rebuild needed. Stored in DataStore, applied immediately
via an OkHttp interceptor that rewrites each request's scheme/host/port.

## API contract — what's real vs. mocked

The govtheme backend branch (`auth.py`) ships **real, working** account/auth
endpoints. Nothing else is implemented server-side yet — every complaint and
policy endpoint is still `mockDockets` / `mockComplaints` / `CP_POLICIES` in
the HTML/JS, each flagged with a `TODO(backend)` comment in the templates.

### Confirmed & live (`data/remote/ApiService.kt`)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register` | → `User`, 201 |
| POST | `/api/auth/login` | → `User` |
| POST | `/api/auth/logout` | → `{"ok": true}` |
| GET | `/api/user/me` | → `User`, 401 if not logged in |
| PATCH | `/api/user/me` | any subset of profile fields (not password) |
| POST | `/api/user/me/password` | `{current_password, new_password}` |

Errors are always `{"error": "message"}` — parsed by
`data/remote/ErrorParsing.kt`.

### Guessed, not yet implemented (falls back to local demo data)

| Method | Path | Fallback |
|---|---|---|
| POST | `/api/create/complaint` | client-side classifier (`classifyComplaintLocally`) |
| GET | `/api/admin/view/complaint` | `DEMO_DASHBOARD_COMPLAINTS` |
| GET | `/api/admin/view/complaint/{id}` | `DEMO_DOCKETS[id]` |
| GET | `/api/citizen/search?q=` | local `scoreDockets()` keyword ranking |
| GET | `/api/policies/` | `DEMO_POLICIES` |
| GET | `/api/policies/{slug}` | `DEMO_POLICIES.find { slug }` |

Every repository (`data/repository/*.kt`) tries the real call first and
falls back to `data/local/DemoData.kt` — a direct Kotlin port of the web
app's `mockDockets`, `mockComplaints`, `CP_POLICIES`, `classifyComplaint()`,
`scoreDockets()`, and `scorePolicies()` — on any failure. This means the app
is fully demoable today, and each fallback silently stops firing the moment
its real endpoint starts responding. `ApiService.kt`'s doc comment has the
full reasoning; that's the one file to edit if any path/shape turns out
different once implemented.

## What's implemented

- **Splash** — calls `GET /api/user/me` to confirm the session cookie is
  still valid, then routes to Login or Dashboard.
- **Login / Register** — tabbed, same fields and validation as `login.html`
  (region, education, employment + occupation, one of the 11 supported
  languages).
- **Dashboard** — stat tiles (total/received/in review/resolved), your
  complaint list, and top policy recommendations with a "Browse all →" link.
- **File complaint** — title, date range, authority level, language
  (matches `complaint.html`'s option list, including "Other"), description,
  up to 5 attachments (resolved from `content://` Uris into cached files for
  multipart upload). Falls back to the same keyword classifier
  `complaint.html` uses if the create endpoint isn't reachable.
- **Complaints & Policies (Track)** — docket-ID lookup, free-text
  keyword search, and a full sortable/filterable queue (sort by
  priority/newest/oldest; filter by category and by the four-stage pipeline
  — received → AI triage → assigned → resolved), each docket expandable into
  a status-rail + body + note detail. A link into PolicyGyaan sits at the
  top, matching the web nav's "Complaints & Policies" pairing.
- **PolicyGyaan** — a searchable policy list and a detail screen with an
  eligibility box and a roadmap stepper (done/current/upcoming), a straight
  port of `policy.html`'s design. Reachable from the dashboard's
  recommendation cards or the Track screen.
- **Account** — profile editing, language preference, email + password
  change, logout, and the server-settings dev screen.
- **Bottom navigation** across Dashboard / File / Track / Account (Policy is
  one tap deeper, matching the web's own nav structure rather than crowding
  a 5th bottom-nav slot).

## Known gaps / next steps

- `GET /api/admin/view/complaint` is assumed citizen-scoped when called by a
  logged-in citizen; if the real backend keeps that route admin-only, it'll
  need a citizen-facing sibling — a one-line change in `ApiService.kt` +
  `ComplaintRepository.myComplaints()`.
- No push notifications for status changes (would need FCM + a backend
  webhook).
- No offline caching (Room) beyond the in-memory demo fallback.
- The `date_from_to` vs. `date_from`/`date_to` mismatch noted in
  `ApiService.kt` — the web mock groups dates as a pair, this sends them as
  two multipart fields. Reconcile once the real endpoint exists.
- Every demo-data fallback branch in the repositories should come out once
  its real endpoint is confirmed working — `data/local/DemoData.kt` itself
  is worth keeping (the classifier especially, as an instant client-side
  pre-check), just stop calling it unconditionally on failure.
- App icon is a placeholder generated in-brand (navy circle, saffron pulse
  mark) — swap for a final asset whenever one exists.
- The web's gov-topbar font-scale controls (A− / A / A+) have no Android
  equivalent here — Android's own system font scaling (Settings ›
  Accessibility) covers the same need natively, so nothing was built for it.

## Merging with the backend branch

Nothing in this folder touches `Frontend/civicpulse/`, the flat
`civicpulse/` layout, `app.py`, `models.py`, `auth.py`,
`requirements.txt`, or any existing web file — it's purely additive. When a
complaint/policy endpoint goes live:

1. Update the matching method in `data/remote/ApiService.kt`.
2. Delete (or narrow) the corresponding fallback in `data/repository/*.kt`.
3. Everything else — screens, ViewModels, navigation — keeps working
   unchanged, since they only depend on the repository layer.

For the confirmed auth endpoints, this is already done — `AuthRepository`
talks to the real `/api/auth/*` and `/api/user/me*` routes with no fallback.
