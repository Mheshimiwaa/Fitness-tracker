# RepRise — Social Fitness & Progressive Overload Tracker

RepRise is a beginner-friendly Android fitness-tracking application built with **Kotlin + Jetpack Compose** on the front end and **Node.js + Express + MongoDB** on the back end.

It lets users track body weight, calculate BMI, log strength workouts, get **progressive overload** suggestions, and compete with friends on a weekly leaderboard — inspired by Duolingo's consistency-based social motivation.

> **Project status:** Phases 1, 2 and 3 are complete and verified. Phase 3 adds **body-weight tracking**, **BMI** and the **dashboard** on both sides — REST endpoints, an aggregated `GET /api/dashboard`, a real Home tab, and a Weight screen with add/edit/delete, a period selector and a hand-drawn trend chart. See [Implementation phases](#implementation-phases).

---

## Table of contents

1. [Overview](#overview)
2. [Features](#features)
3. [Technology stack](#technology-stack)
4. [Architecture](#architecture)
5. [Repository structure](#repository-structure)
6. [Prerequisites](#prerequisites)
7. [MongoDB Atlas setup](#mongodb-atlas-setup)
8. [Environment variables](#environment-variables)
9. [Backend — install & run](#backend--install--run)
10. [Android — setup & run](#android--setup--run)
11. [API base URL (emulator vs. physical device)](#api-base-url-emulator-vs-physical-device)
12. [API endpoints](#api-endpoints)
13. [Database schema & indexes](#database-schema--indexes)
14. [Testing](#testing)
15. [Common errors & solutions](#common-errors--solutions)
16. [Screenshots](#screenshots)
17. [Professor demonstration procedure](#professor-demonstration-procedure)
18. [Implementation phases](#implementation-phases)
19. [Known limitations & future improvements](#known-limitations--future-improvements)

---

## Overview

RepRise rewards **consistency**, not body weight or maximum lifting strength. Its core idea is progressive overload: each workout is compared with the user's previous performance on the same exercise, and simple, transparent, rule-based guidance suggests what to try next.

The Android app **never connects to MongoDB directly**. Every read and write goes through the Express REST API, which authenticates requests with JWTs and enforces ownership rules server-side.

---

## Features

### Essential (MVP)
| Feature | Status |
|---|---|
| Registration, login, logout, JWT-protected endpoints | ✅ Phase 2 |
| Editable profile — rename yourself in-app; goal/unit/height via the API | ✅ Phase 2 |
| Dashboard summary with honest empty states | ✅ Phase 3 |
| Body-weight tracking (CRUD, kg/lb, history, chart, period change) | ✅ Phase 3 |
| BMI from latest weight + height, with adult categories & disclaimers | ✅ Phase 3 |
| Workout logging (workout → exercises → sets of weight × reps) | ✅ Phase 4 |
| Progressive overload comparison + rule-based suggestions | Planned (Phase 5) |
| Friends (search, request, accept/reject, list, remove) | Planned (Phase 6) |
| Weekly leaderboard scored on the server | Planned (Phase 6) |
| Activity streaks (current + longest) | Planned (Phase 7) |
| Settings (theme, unit, height, goal, privacy, logout) | 🟡 theme + logout done (Phase 2); the rest in Phase 7 |

### Optional improvements
- Profile image rendering from a URL
- Pagination beyond the first page of history
- Friend workout-sharing toggle
- Data export

---

## Technology stack

**Android**
- Kotlin, Jetpack Compose, Material 3
- Navigation Compose, ViewModel, Coroutines & Flow
- Retrofit + OkHttp (Kotlinx Serialization)
- DataStore (theme/units) + EncryptedSharedPreferences (JWT)
- Gradle Kotlin DSL + version catalog

**Backend**
- Node.js + Express 5 (JavaScript, CommonJS)
- MongoDB Node.js driver (official) — **MongoDB Atlas** or local MongoDB
- JWT auth, bcryptjs password hashing, Zod validation, dotenv
- helmet, CORS, express-rate-limit

**Tooling**
- Android Studio, Gradle, Git/GitHub, Postman, MongoDB Atlas web UI

---

## Architecture

```
┌──────────────────────────────┐         ┌────────────────────────────────────┐
│        Android app           │  HTTPS  │          Express REST API          │
│                              │  JSON   │                                    │
│  Compose UI (Material 3)     │◄───────►│  routes → middleware →             │
│        │                     │  JWT    │  controllers → services →          │
│        ▼                     │         │  database (MongoDB driver)         │
│  ViewModel (immutable state) │         │                                    │
│        ▼                     │         │  auth · validation · rate limit ·  │
│  Repository                  │         │  CORS · request logging · errors   │
│        ▼                     │         └─────────────────┬──────────────────┘
│  Retrofit + OkHttp           │                           │
└──────────────────────────────┘                           ▼
        │                                       ┌──────────────────────┐
        ▼                                       │      MongoDB         │
   DataStore / token store                      │  users               │
   (theme, units, JWT)                          │  weight_entries      │
                                                │  workouts            │
                                                │  friendships         │
                                                └──────────────────────┘
```

**Why this shape?**
- **Business rules live on the server** (BMI, training volume, streaks, leaderboard score) so results are trustworthy and can be unit-tested.
- **The client only renders state** — Compose screens never talk to the database or contain scoring logic.
- **One repository layer** on Android keeps networking details out of the ViewModels.

### Backend request flow

```
HTTP request
   │
   ▼
helmet → cors → json body parser (100 kb limit) → request logger (dev)
   │
   ▼
routes  (URL → controller)
   │
   ▼
middleware: authenticate → validate(zod) → rateLimit
   │
   ▼
controllers (HTTP concerns only)
   │
   ▼
services (pure logic: auth, profiles — and later BMI, volume, progression, streaks, leaderboard)
   │
   ▼
database (MongoDB collections + indexes)
   │
   ▼
central error handler → consistent JSON error shape
```

### Android request flow

```
MainActivity
   │  applies the saved theme (DataStore)
   ▼
RepRiseApp ── observes AuthState ──┬─ Unknown   → LoadingState (checking the token)
   │                               ├─ SignedOut → AuthNavHost (login / register)
   │                               └─ SignedIn  → MainScaffold (the 4 tabs)
   ▼
AuthViewModel  (holds AuthFormState: submitting / error / field errors)
   │  suspend calls
   ▼
AuthRepository ── owns AuthState, saves & clears the JWT
   │
   ▼
Retrofit (RepRiseApi) ──► OkHttp ──┬─ AuthInterceptor adds "Bearer <token>"
                                   └─ apiCall() maps Response → ApiResult
```

**How the session works**
- The JWT is the *only* persisted session data, kept in `EncryptedSharedPreferences` (keys and values are encrypted with an Android Keystore key).
- On launch the app calls `GET /api/auth/me` to confirm the stored token. A rejected token is deleted and the user is shown the login screen, so there is no half-signed-in state.
- The session is a single `StateFlow` observed by the UI. Signing in or out swaps the whole composable tree, so the auth screens (and anything typed into them) cannot be reached again with the back button.
- **Errors are values, not exceptions** — `apiCall()` converts every outcome into an `ApiResult`; the server's `error.message` is shown to the user, and per-field validation messages are attached to the matching text box.

### Android request flow (Phase 3 — dashboard & weight)

```
Home tab                              Weight screen
   │  LaunchedEffect(Unit)                │  init { refresh() }
   ▼                                      ▼
DashboardViewModel                     WeightsViewModel  (list + add/edit/delete form)
   │  dashboard()                         │  history() / addWeight() / updateWeight() / deleteWeight()
   ▼                                      ▼
WeightRepository ── maps DTOs ⇄ domain models ──► Retrofit ──► OkHttp
   │
   ▼
DashboardUiState / WeightsUiState  (StateFlow, immutable)  ──►  Compose screens
```

- The **repository is the only** place that knows about `...Dto` types; screens only ever see `Dashboard`, `WeightEntry`, `WeightChange` and `Bmi`.
- `DELETE /api/weights/:id` answers **204 No Content**, so it goes through `apiCallUnit()`, which judges success by the status code rather than by a body.
- The Home tab re-fetches whenever it re-enters composition, so a weight logged on the Weight screen appears on the dashboard as soon as the user navigates back.
- The trend line is drawn with Compose's `Canvas` (`components/WeightChart.kt`) — **no charting dependency**.
- **Unit conversion happens on the server.** A reading is stored exactly as entered (value + unit) and converted to the user's *current* preferred unit when read, so changing kg ⇄ lb never rewrites history.

### Android request flow (Phase 4 — workout logging)

```
Workouts tab                          Workout editor (pushed)
   │  LaunchedEffect(Unit)                │  LaunchedEffect(workoutId) { load() }
   ▼                                      ▼
WorkoutsViewModel  (list + delete)     WorkoutEditorViewModel  (draft, validate, save)
   │  history() / deleteWorkout()         │  workout() / addWorkout() / updateWorkout()
   ▼                                      ▼
WorkoutRepository ── maps DTOs ⇄ domain models ──► Retrofit ──► OkHttp
   │                                                              │
   ▼                                                              ▼
WorkoutsUiState / WorkoutEditorUiState  (StateFlow)   exercises catalogue (suggestion chips)
```

- The editor's form state lives in the **ViewModel**, not in `remember`, so rotating the device does not lose a half-typed session.
- Validation is split: the obvious mistakes are caught client-side for instant feedback (`parseWorkoutDraft`, a **pure function** so it is unit-tested without a device), while the server stays the authority on plausible loads and its message wins.
- `PATCH` sends the **complete** workout and models its fields with no defaults, so `null` is encoded rather than skipped — that is what makes "clear the duration/note" possible.
- Tapping a session opens the same editor route with its id; `"new"` is the route argument that means "log a new workout".
- Volume (`totalVolumeKg`) is always shown in **kg** — it is a cross-session comparison figure, not a body weight with a chosen unit.

---

## Repository structure

```
reprise/
├── README.md
├── .gitignore
├── android/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── gradle/
│   │   ├── libs.versions.toml            # single source of dependency versions
│   │   └── wrapper/…
│   ├── gradlew / gradlew.bat
│   └── app/
│       ├── build.gradle.kts
│       └── src/
│           ├── AndroidManifest.xml
│           ├── res/                       # strings, themes, colours, icons
│           ├── test/java/com/reprise/app/ # JVM unit tests (no emulator needed)
│           └── java/com/reprise/app/
│               ├── RepRiseApplication.kt  # builds the dependency container
│               ├── MainActivity.kt        # applies the saved theme, hosts Compose
│               ├── di/AppModule.kt        # hand-written service locator
│               ├── navigation/            # RepRiseApp (auth gate), MainScaffold,
│               │                          # AuthNavHost, destinations
│               ├── domain/model/          # User, AuthState, ThemeMode, Weight, Workout, enums
│               ├── data/
│               │   ├── remote/            # RepRiseApi, ApiClient, AuthInterceptor,
│               │   │                      # ApiResult + ApiCall (error parsing), dto/
│               │   ├── local/             # TokenStore (encrypted), SettingsStore
│               │   └── repository/        # AuthRepository (session source of truth),
│               │                          # WeightRepository (weight + dashboard),
│               │                          # WorkoutRepository (workout logging)
│               ├── util/                  # Formatting (ISO dates, kg/lb numbers, volume)
│               ├── ui/theme/              # Color, Type, Theme, SettingsViewModel
│               ├── ui/auth/               # LoginScreen, RegisterScreen, AuthViewModel
│               ├── ui/home/               # HomeScreen (dashboard) + DashboardViewModel
│               ├── ui/weights/            # WeightsScreen + WeightsViewModel
│               ├── ui/workouts/           # WorkoutsScreen, WorkoutEditorScreen + ViewModels
│               ├── ui/{friends,profile}/…
│               └── components/            # reusable Compose widgets (ScreenContainer,
│                                          # DetailScreen, RepRiseCard, WeightChart, …)
└── backend/
    ├── package.json
    ├── .env.example                       # placeholders only — never real secrets
    ├── src/
    │   ├── server.js                      # startup: connect DB → ensure indexes → listen
    │   ├── app.js                         # Express app + middleware + routes
    │   ├── config/
    │   │   ├── env.js                     # central env loading & validation
    │   │   └── constants.js               # shared rules (goals, units, ranges, BMI bands)
    │   ├── database/                      # connection, collections, indexes
    │   ├── middleware/                    # requestLogger, notFound, errorHandler,
    │   │                                  # authenticate, validate, rateLimiter
    │   ├── routes/                        # index, health, auth, users, weights,
    │   │                                  # dashboard, workouts
    │   ├── controllers/                   # auth, users, weights, workouts, dashboard
    │   ├── services/                      # auth, user, weight, workout, bmi, dashboard
    │   ├── validators/                    # common.fields, auth, user, weight, workout (Zod)
    │   └── utils/                         # logger, ApiError, password, token, units
    └── tests/
        ├── helpers/                       # setup, app, db (shared test utilities)
        ├── health.test.js
        ├── auth.test.js
        ├── weights.test.js
        └── workouts.test.js
```

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Node.js | ≥ 20 (developed on 22) | must include the built-in test runner |
| npm | ≥ 9 | ships with Node |
| MongoDB | Atlas account **or** local `mongod` | the app only needs a connection string |
| Android Studio | latest | bundles the Android SDK + a JDK |
| JDK | 17 or 21 | bundled with Android Studio |
| Android SDK Platform 37 (Android 17) | API 37 | the app compiles against **API 37**; Android Studio installs it automatically on first Gradle sync |

**Android build toolchain** (declared in `android/gradle/libs.versions.toml`): Android Gradle Plugin **9.4.1**, Gradle **9.8.1**, Kotlin **2.2.10**, Compose BOM **2026.09.00**, `compileSdk` **37.2**, `targetSdk` **37**, `minSdk` **24** (Android 7.0).

> **Note on AGP 9:** since Android Gradle Plugin 9.0, Kotlin compilation is **built in**. The project therefore does **not** apply the `org.jetbrains.kotlin.android` plugin — doing so is a hard error. Only the extra Kotlin compiler plugins (Compose, serialization) are declared.

> **Note for this machine:** the JDK and Android SDK are installed *locally* (no system packages, no `sudo`) under `~/.toolchain`. Open a shell and run `source ~/.toolchain/env.sh` to use them from the command line. Android Studio should be pointed at `~/.toolchain/android-sdk` (`android/local.properties` already does this).

---

## MongoDB Atlas setup

1. Create a free **M0** cluster at <https://cloud.mongodb.com>.
2. **Database Access** → add a database user (username + password). *Do not reuse your Atlas login.*
3. **Network Access** → add your IP address (or `0.0.0.0/0` for local development only).
4. **Connect → Drivers** → copy the connection string. It looks like:
   ```
   mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority
   ```
5. Add a database name (`reprise`) to the path, so the URI ends with `/reprise`.
6. Put the finished string in `backend/.env` as `MONGODB_URI`.

**Local alternative:** if you have MongoDB installed, use `MONGODB_URI=mongodb://127.0.0.1:27017/reprise`. Everything else works identically.

---

## Environment variables

All variables live in **`backend/.env`** (git-ignored). Start from the template:

```bash
cp backend/.env.example backend/.env
```

| Variable | Required | Example | Purpose |
|---|---|---|---|
| `NODE_ENV` | no | `development` | `production` hides internal error details |
| `PORT` | no | `4000` | HTTP port |
| `MONGODB_URI` | **yes** | `mongodb+srv://…/reprise` | database connection string |
| `MONGODB_DB_NAME` | no | `reprise` | logical database name |
| `JWT_SECRET` | **yes** | *(48 random bytes)* | signs/verifies JWTs |
| `JWT_EXPIRES_IN` | no | `7d` | token lifetime |
| `BCRYPT_ROUNDS` | no | `10` | password-hash cost factor |
| `CORS_ORIGIN` | no | `*` | allowed origins (`*` in dev) |
| `ENABLE_HTTP_LOGS` | no | `true` | dev-only request logging |

Generate a secret:

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
```

> **Never** commit `.env`. The Android app stores **no** database credentials or signing secrets — only the API base URL.

---

## Backend — install & run

```bash
cd backend
cp .env.example .env          # then edit .env and set MONGODB_URI + JWT_SECRET
npm install
npm run dev                   # or: npm start
```

Expected output:

```
INFO  - Connected to MongoDB (db="reprise", host="…")
INFO  - MongoDB indexes ensured.
INFO  - RepRise API listening on http://localhost:4000
INFO  - Health check:  http://localhost:4000/api/health
```

Verify:

```bash
curl http://localhost:4000/api/health
```

```json
{
  "status": "ok",
  "service": "reprise-backend",
  "version": "0.1.0",
  "environment": "development",
  "database": { "connected": true, "name": "reprise" }
}
```

If the database is unreachable the endpoint returns **503** with `"status": "degraded"` — the server refuses to pretend everything is fine.

---

## Android — setup & run

**In Android Studio**
1. *File → Open* → select the `reprise/android` folder (not the repo root).
2. Let Gradle sync. Android Studio will use `android/local.properties` for the SDK location and offer to install **Android SDK Platform 37** if it is missing (accept it).
3. Select an emulator (or a connected device) and press **Run ▶**.

**From the command line**

```bash
source ~/.toolchain/env.sh      # adds the local JDK + Android SDK to PATH
cd android
./gradlew :app:assembleDebug    # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug     # installs onto a running emulator/device
./gradlew :app:testDebugUnitTest # runs the JVM unit tests (no device needed)
```

**What you see on first run**

The app opens on the **Create your account** / **Sign in** flow. The backend must
be running (see above) — if it is not, the app shows *"Can't reach the server…"*
rather than hanging. After registering or signing in, the four tabs appear and
the session survives closing the app, because the JWT is stored encrypted on the
device and re-validated with `GET /api/auth/me` on launch.

---

## API base URL (emulator vs. physical device)

The base URL is defined **once** in `android/app/build.gradle.kts` as a `buildConfigField`:

| Target | Value | Why |
|---|---|---|
| Android **emulator** | `http://10.0.2.2:4000/` | `10.0.2.2` is the emulator's alias for your computer's `localhost` |
| **Physical phone** (same Wi-Fi) | `http://<your-computer-LAN-IP>:4000/` | e.g. `http://192.168.1.20:4000/` |

For a physical device you must also:
1. Change `API_BASE_URL` in `app/build.gradle.kts` to your LAN IP.
2. Start the backend so it listens on all interfaces (Express does by default).
3. Make sure your phone and computer are on the same network and the firewall allows port `4000`.

Cleartext HTTP is only permitted for `10.0.2.2`, `localhost` and `127.0.0.1` (see `res/xml/network_security_config.xml`). Add your LAN IP there too when testing on a real device.

---

## API endpoints

All routes are prefixed with `/api`. Protected routes require `Authorization: Bearer <token>`.

### Implemented (Phase 1)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/health` | no | Service + database health (`200` ok, `503` degraded) |

### Implemented (Phase 2 — authentication & profile)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | no | create an account, returns a JWT (`201`) |
| POST | `/api/auth/login` | no | sign in with username/email + password, returns a JWT |
| GET | `/api/auth/me` | yes | the signed-in user (used to validate a stored token) |
| GET | `/api/users/me` | yes | read my profile |
| PATCH | `/api/users/me` | yes | update my profile |
| GET | `/api/users/search?username=<text>` | yes | prefix-search other users (excludes me) |

**Authentication flow**

```
POST /api/auth/register  { displayName, username, email, password }
                       -> 201 { user, token }
POST /api/auth/login     { username, password }     // username may be an email
                       -> 200 { user, token }
GET  /api/auth/me        Authorization: Bearer <token>
                       -> 200 { user }
```

Rules enforced by the server:
- Passwords are **bcrypt-hashed** (`BCRYPT_ROUNDS`, default 10) and never returned. Minimum 8 characters, at least one letter and one number.
- Usernames are 3–20 characters, start with a letter, and use only letters, numbers and underscores (lower-case).
- Email uniqueness is **case-insensitive**: `Alex@Example.com` and `alex@example.com` are the same account.
- Wrong password and unknown user return the **same** generic `401`, so usernames cannot be enumerated.
- Duplicate username/email → `409`; invalid input → `400` with per-field `details`; missing/bad token → `401`.
- `/api/users/me` only ever reads or writes the **caller's own** document.
- Auth endpoints are rate-limited (`20` requests / 15 min / IP in normal use).

### Implemented (Phase 3 — weight tracking, BMI & dashboard)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/weights` | yes | log a weigh-in (`201`) |
| GET | `/api/weights?days=&limit=&order=` | yes | my history + the change across the window |
| GET | `/api/weights/latest` | yes | my most recent reading (or `entry: null`) |
| GET | `/api/weights/:id` | yes | read one entry |
| PATCH | `/api/weights/:id` | yes | edit an entry |
| DELETE | `/api/weights/:id` | yes | delete an entry (`204`) |
| GET | `/api/dashboard` | yes | the Home tab's aggregated summary |

**Weight & dashboard flow**

```
POST  /api/weights   { weight, unit?, measuredAt?, note? }
                     -> 201 { entry }
GET   /api/weights?days=30
                     -> 200 { entries, count, change, days }
GET   /api/weights/latest
                     -> 200 { entry }        // entry is null when nothing is logged
PATCH /api/weights/:id  { weight?, unit?, measuredAt?, note? }
                     -> 200 { entry }
DELETE /api/weights/:id
                     -> 204 (no body)
GET   /api/dashboard
                     -> 200 { weight:{...}, workouts:{...}, bmi, bmiUnavailableReason }
```

A weight entry looks like this on the wire:

```json
{
  "id": "6acf3e75fc7b216d6e75b330",
  "weight": 82.4,          // the value to display, in `unit`
  "unit": "kg",            // the user's CURRENT preferred unit
  "weightKg": 82.4,        // the canonical kilograms (used for BMI)
  "measuredAt": "2026-10-14T08:33:57.367Z",
  "note": "morning"
}
```

Rules enforced by the server:
- `weight` is stored **exactly as entered** (value + unit). Reads convert to the user's *current* preferred unit, so switching kg ⇄ lb never rewrites history.
- Values must be plausible for the stated unit (20–500 kg / 44–1102 lb), and `measuredAt` may be back-dated but not set in the future.
- `unit` is optional: when omitted it defaults to the user's preferred unit.
- **Ownership is enforced inside the query** (`{ _id, userId }`), so requesting somebody else's entry returns `404` — the same response as a non-existent id, so the API cannot be used to discover which ids exist.
- `change` is `null` (not a misleading `0`) when the window holds fewer than two readings.
- `bmi` is `null` when it cannot be computed, and `bmiUnavailableReason` is `"no_height"` or `"no_weight"`, so the app can say what is missing instead of showing a fake number.

### Implemented (Phase 4 — workout logging)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/workouts` | yes | log a workout (`201`) |
| GET | `/api/workouts?days=&limit=&order=` | yes | my sessions, newest first |
| GET | `/api/workouts/exercises` | yes | distinct exercise names I have logged (editor suggestions) |
| GET | `/api/workouts/:id` | yes | read one workout |
| PATCH | `/api/workouts/:id` | yes | edit a workout (`exercises` **replaces** the list) |
| DELETE | `/api/workouts/:id` | yes | delete a workout (`204`) |

**Workout flow**

```
POST  /api/workouts
      { name, workoutDate?, durationMinutes?, notes?,
        exercises:[{ name, unit?, restSeconds?, notes?, sets:[{ reps, weight }] }] }
                        -> 201 { workout }
GET   /api/workouts?days=7&limit=50
                        -> 200 { workouts, count, days }
GET   /api/workouts/exercises
                        -> 200 { exercises }   // ["Barbell Row", "Bench Press", ...]
GET   /api/workouts/:id -> 200 { workout }
PATCH /api/workouts/:id -> 200 { workout }
DELETE /api/workouts/:id-> 204 (no body)
```

A workout looks like this on the wire:

```json
{
  "id": "6acf3e75fc7b216d6e75b330",
  "name": "Push day",
  "workoutDate": "2026-10-14T08:33:57.367Z",
  "durationMinutes": 52,
  "notes": "felt strong",
  "exercises": [
    {
      "name": "Bench Press",
      "unit": "kg",               // the unit the weights below are shown in
      "restSeconds": 120,
      "notes": null,
      "sets": [
        { "reps": 8, "weight": 60, "weightKg": 60 },
        { "reps": 6, "weight": 65, "weightKg": 65 }
      ]
    }
  ],
  "summary": { "exerciseCount": 1, "setCount": 2, "totalReps": 14, "totalVolumeKg": 870 },
  "createdAt": "2026-10-14T08:35:10.000Z",
  "updatedAt": "2026-10-14T08:35:10.000Z"
}
```

Rules enforced by the server:
- A workout is the tree `workouts → exercises[] → sets[]`; each set is `{ reps, weight }`, and the weight belongs to its **exercise's `unit`**.
- `summary` is **derived on every read** (never stored) so it cannot drift: `totalVolumeKg` = Σ `weight × reps`, converted to kilograms.
- `unit` is optional per exercise and defaults to the user's preferred unit. Reads convert each set to the user's *current* unit and also expose the canonical `weightKg`.
- Loads must be plausible for the unit (≤ 1000 kg / 2205 lb). The exact per-unit check runs in the **service**, and its `400` names the offending set (e.g. `exercises.0.sets.1.weight`). `reps` is 1–1000, and `weight` may be `0` for bodyweight/assisted sets.
- `workoutDate` may be back-dated but not set in the future; durations are 1–600 minutes.
- On `PATCH`, supplying `exercises` **replaces the whole list**, and an explicit `null` for `durationMinutes` or `notes` **clears** that field (so a value can be removed, not just changed).
- Ownership is enforced inside the query (`{ _id, userId }`), so another user's workout is a `404` — the same response as a non-existent id.
- `GET /api/dashboard` gained a `workouts` section: `{ totalCount, recentCount, recentDays, recentVolumeKg, lastWorkoutAt }` where "recent" is a **rolling** 7-day window (not a calendar week).

### Planned (Phases 5–7)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/workouts/:id/progression` | yes | progression vs. the previous session (rule-based) |
| POST | `/api/friends/requests` | yes | send a friend request |
| GET/PATCH | `/api/friends/requests[/:id]` | yes | list / accept / reject requests |
| GET/DELETE | `/api/friends[/:id]` | yes | list / remove friends |
| GET | `/api/leaderboard/weekly` | yes | weekly friends leaderboard |

> `GET /api/dashboard` simply gains new sections (progression, streak, leaderboard) as Phases 5–7 land — the app never has to be rewired.

**Error shape** (every failure):

```json
{ "error": { "message": "Human-readable explanation", "details": [] } }
```

---

## Database schema & indexes

Database name: **`reprise`**. Collections and their purpose:

### `users`
Account, profile and preferences. **One document per user.**
`_id`, `displayName`, `username`, `usernameNormalized`, `email`, `emailNormalized`, `passwordHash`, `profileImageUrl?`, `fitnessGoal`, `weightUnit`, `heightCm?`, `createdAt`, `updatedAt`.
*Never contains arrays of weight entries or workouts* — those live in their own collections so documents stay small.

### `weight_entries`
Body-weight history. `_id`, `userId`, `weight`, `unit`, `measuredAt`, `note?`, `createdAt`, `updatedAt`.
*The value is stored **exactly as entered**, together with its unit — there is deliberately no denormalised `weightKg` column, because that would let the two copies of the same fact drift apart. Conversion to the display unit happens on every read (`src/utils/units.js`), which is trivially cheap for a per-user history.*

### `workouts`
Workout sessions with embedded exercises and sets.
`_id`, `userId`, `name`, `workoutDate`, `durationMinutes?`, `notes?`, `exercises[]{ name, unit, restSeconds?, notes?, sets[]{ reps, weight } }`, `createdAt`, `updatedAt`.
*The `summary` (exercise/set/reps counts, `totalVolumeKg`) is **not stored** — it is derived on every read, so it can never drift from the sets it describes.*

### `friendships`
One row per relationship. `_id`, `requesterId`, `recipientId`, `status` (`pending`|`accepted`|`rejected`), `createdAt`, `updatedAt`.

### Indexes
| Collection | Index | Purpose |
|---|---|---|
| `users` | `usernameNormalized` **unique** | usernames are unique, case-insensitively |
| `users` | `emailNormalized` **unique** | emails are unique, case-insensitively |
| `weight_entries` | `{ userId: 1, measuredAt: -1 }` | "my weight history, newest first" |
| `workouts` | `{ userId: 1, workoutDate: -1 }` | "my workout history, newest first" |
| `workouts` | `{ userId: 1, 'exercises.name': 1, workoutDate: -1 }` (`user_exercise_recent`) | one exercise's history for the progressive-overload comparison (Phase 5) |
| `friendships` | `{ requesterId: 1, status: 1 }` | requests I sent / my friendships |
| `friendships` | `{ recipientId: 1, status: 1 }` | requests I received |

Indexes are created automatically at startup by `src/database/indexes.js` and are visible in Atlas under **Collections → Indexes**.

**Leaderboard scores and streaks are not stored** — they are recomputed from `workouts` on every request so the numbers can never drift out of sync.

---

## Testing

**Backend**

```bash
cd backend
npm test
```

Uses Node's built-in test runner (`node:test`) and built-in `fetch` — no extra test dependencies. **63 tests** currently pass.

- **Phase 1** — unknown-route `404` handling, and the health check in both the *degraded* and *ok* states.
- **Phase 2** — registration (incl. duplicate username/email → `409`, case-insensitive email), login (correct / wrong password / unknown user), JWT protection (`401` for missing or malformed tokens), profile updates, and username search (excludes the caller, leaks no private fields).
- **Phase 3** — weight schemas (per-unit range, future/back-dated timestamps, empty update bodies), `401` on every weight route, create/list/latest/read/update/delete, unit conversion after a preference change, ownership isolation (another user gets `404` and the owner's entry is untouched), BMI from height + latest weight, `bmiUnavailableReason`, and the 30-day change.
- **Phase 4** — workout schemas (nested exercises/sets, plausible loads, future dates, clearing a duration/note with an explicit `null`, empty update bodies), `401` on every workout route, create with a derived `summary`, the per-unit load check naming the exact set, the distinct-exercise catalogue, list/read/update (including replacing the exercise list)/delete, ownership isolation, and the dashboard's rolling training totals.

> **Tests never touch your real data.** `tests/helpers/setup.js` points the suite at a separate **`reprise_test`** database on the same cluster. Every test user is deleted before and after the run **along with everything that points at them** (`deleteUserDataByPrefix` — weight entries and workouts), so no orphaned documents accumulate. Tests that need the database are **skipped** (not failed) when MongoDB is unreachable, so the pure-validation tests run anywhere.

**Android**

```bash
cd android
./gradlew :app:testDebugUnitTest          # JVM unit tests (no device needed)
./gradlew :app:connectedDebugAndroidTest  # instrumented tests (needs a device/emulator)
```

The JVM suite (**58 tests**) covers the parts where a mistake is invisible until
runtime:

- **`ApiCallTest`** — every shape the API can answer with: a successful response,
  a `400` whose `details` array becomes per-field errors, a `409` whose
  `details` object names the offending field, a plain `401`, an unreadable body
  (falls back to a status-based message), and an unreachable server (no status
  code). These run with no network and no emulator — the `Response` objects are
  built in code.
- **`ApiCallTest` (204)** — `apiCallUnit` treats a bodyless `204` as success while
  still surfacing the server's message on a `404`.
- **`DomainMappingTest`** — pins the `fitnessGoal` / `weightUnit` / BMI-category
  strings to the backend's contract, and checks the unknown-value fallbacks and
  the theme-mode resolution.
- **`WeightRepositoryTest`** — drives a hand-written fake `RepRiseApi` to prove the
  DTO ⇄ domain mapping: values, unit codes, the canonical `weightKg`, the BMI
  category, a `null` change, the dashboard's training section, the delete path,
  and that a rejection passes through with the server's message intact.
- **`WorkoutRepositoryTest`** — the same fake-API technique for the workout tree:
  exercises → sets, the display weight *and* the canonical `weightKg`, the derived
  summary, the `days`/`limit` query, the exercise catalogue, the request bodies the
  editor produces, and — as its own test — that a `null` duration/note really is
  **encoded** (not dropped) so a field can be cleared.
- **`WorkoutDraftParseTest`** — the editor's validation rules, which live in a pure
  function (`parseWorkoutDraft`) precisely so they can be tested without a device:
  blank names, reps/weights, "0 kg is fine but −5 is not", a comma decimal
  separator, a blank vs. nonsense duration, and the no-exercises case.
- **`FormattingTest`** — ISO-8601 parsing, checked against `java.time.Instant` as
  an independent oracle, plus signed weight formatting, the editable-number
  helper (no floating-point noise leaking into a form), and volume grouping.

**Manual API check (Postman or curl)**

```bash
curl -i http://localhost:4000/api/health
curl -i http://localhost:4000/api/does-not-exist   # -> 404 JSON error
```

---

## Common errors & solutions

| Symptom | Cause | Fix |
|---|---|---|
| `Missing required environment variable "MONGODB_URI"` | no `backend/.env` | `cp backend/.env.example backend/.env` and fill it in |
| `Failed to connect to MongoDB` on startup | wrong URI / IP not allow-listed | check the URI, and add your IP in Atlas → Network Access |
| Health returns `503 degraded` | server is up but the DB ping failed | check the URI and that the cluster is running |
| `MongooseServerSelectionError`-style timeout | your network blocks port 27017 | allow it, or use a different network |
| Android: `SDK location not found` | `local.properties` missing | create it with `sdk.dir=/path/to/Android/sdk`, or open in Android Studio |
| Android: `Cleartext HTTP traffic not permitted` | plain-HTTP URL not allow-listed | add your host to `res/xml/network_security_config.xml` |
| Android: cannot reach the backend | wrong base URL | emulator → `10.0.2.2`; device → your LAN IP |
| `gradlew: Permission denied` | lost the executable bit | `chmod +x android/gradlew` |
| Android: `Failed to find Platform SDK ... android-37` | SDK Platform 37 not installed | Android Studio → SDK Manager → install **Android SDK Platform 37 (Android 17)**, or `sdkmanager "platforms;android-37.2"` |
| Android: *"requires ... compile against version 37 or later"* | `compileSdk` too low for the current AndroidX libraries | keep the `compileSdk { version = release(37) { minorApiLevel = 2 } }` block as-is |
| Android: `Failed to apply plugin 'org.jetbrains.kotlin.android'` | AGP 9 has built-in Kotlin | do **not** apply that plugin; see the note in [Prerequisites](#prerequisites) |

---

## Screenshots

> _Add screenshots of the finished app here before the demonstration. Suggested shots: dark-mode Home, weight history chart, an active workout, the progressive-overload comparison, and the weekly leaderboard._

---

## Professor demonstration procedure

The full sequence below is the target for Phase 7. **Currently steps 1–7 are runnable, in the app itself.**

1. Start the backend and show `GET /api/health` returning `database.connected: true`. ✅
2. Register a new user in the app → open Atlas → show the `users` document with `passwordHash` (**no** plain-text password). ✅
3. You are signed in automatically: rename yourself in Profile and switch **Appearance** between System / Light / Dark. ✅
4. Show the errors the app surfaces (wrong password, duplicate username, weak password), sign out, then sign back in — close and reopen the app to prove the session is remembered. ✅
5. Log a body weight from the **Weight** screen (Home → *Log weight*) → show the matching `weight_entries` document in Atlas. ✅
6. Switch the units in the API to pounds (`PATCH /api/users/me {"weightUnit":"lb"}`) and reload → the **same stored entry is displayed in lb**, proving conversion happens on read. Set a height and show **BMI** calculated from it plus the latest weight, with its WHO category and disclaimer. ✅
7. Create a workout from the **Workouts** tab (*Log workout*): name it, add an exercise with several sets, and save → show the nested `workouts` document in Atlas, and the derived `summary` on the card. Edit it, then delete it. ✅
8. Log a second session for the same exercise and demonstrate the progressive-overload comparison.
9. Register a second test account, send and accept a friend request → show the `friendships` document.
10. Complete qualifying workouts and show how the weekly leaderboard score is computed.
11. Explain the architecture, REST API, schema, authentication and scoring rules.

**Test accounts** *(already created in Atlas during Phase 2)*:

| Display name | Username | Email | Password | Purpose |
|---|---|---|---|---|
| Alex Lifter | `alex_lifter` | `alex@example.com` | `GoodPass123` | primary demo account |
| Sam Spotter | `sam_spotter` | `sam@example.com` | `GoodPass123` | friend / leaderboard rival |

> These are **demo** credentials for a local project. In a real deployment every account would have its own strong password. Create them (or more) with `POST /api/auth/register`.

All demo data is created through the app so Atlas shows genuine, reproducible records.

---

## Implementation phases

| Phase | Scope | Status |
|---|---|---|
| 1 | Repo, Android project, Express backend, MongoDB connection, env vars, health check | ✅ **complete & verified** |
| 2 | Auth (register/login/JWT), Compose theme, navigation, profile model | ✅ **complete & verified** (backend + Android auth screens, session restore, profile, theme) |
| 3 | Dashboard, weight tracking, BMI, chart | ✅ **complete & verified** (backend + Home tab, Weight screen, trend chart) |
| 4 | Workout logging (exercises + sets) | ✅ **complete & verified** (backend + Workouts tab, workout editor, Home training card) |
| 5 | Progressive overload + unit tests | ⏳ |
| 6 | Friends + weekly leaderboard | ⏳ |
| 7 | Streaks, settings, polish, demo prep | ⏳ |

---

## Known limitations & future improvements

- **Phases 1–4 complete** — authentication, profiles, body-weight tracking, BMI, the dashboard, and workout logging work end to end. The Friends tab is still an honest placeholder until Phase 6.
- The only profile field editable in the app is the **display name**; goal, unit and height can be changed through `PATCH /api/users/me` and get their UI in Phase 7. This means the BMI card prompts you to *"add your height in your profile"* while there is currently no in-app field for it.
- Weight entries and workouts are logged with the **current time** in the app (the common case). Back-dating is supported by the API (`measuredAt` / `workoutDate`) and documented above, but has no date picker in the UI yet.
- A workout's **training volume is always shown in kilograms**. It is a figure for comparing sessions across units, not a body weight the user chose a unit for, so it is deliberately not converted. Per-set weights *are* converted to the preferred unit.
- The workout editor does not offer a per-exercise **unit picker**: new sets are entered in the user's preferred unit (the server defaults and converts). Exercises that were originally logged in another unit are shown in the preferred unit and saved back in it.
- The weight history list is capped at the server's default page (100 entries); older entries are still counted in "All time" totals and can be paged through the API, but the app does not paginate yet. The Workouts list shows the 50 most recent sessions.
- Session tokens are **stateless JWTs with no server-side revocation list**, so logout clears the token locally. Acceptable for a single-device demo; production would add refresh/revocation.
- Leaderboard scoring, streaks and progressive overload are all **recomputed on demand** (no cached snapshots). Fine for an MVP; a snapshot collection could be added if leaderboards grow large.
- BMI is a **screening measure only** and is not a measure of muscle mass or body-fat percentage. Adult categories are not presented as definitive for users under 20.
- Progressive-overload suggestions are **advisory, rule-based** guidance — not medical advice — and never change a workout automatically.
- Optional future work: push notifications for streaks, profile images, friend workout sharing, data export.
