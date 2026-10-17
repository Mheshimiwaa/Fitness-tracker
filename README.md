# RepRise — Social Fitness & Progressive Overload Tracker

RepRise is a beginner-friendly Android fitness-tracking application built with **Kotlin + Jetpack Compose** on the front end and **Node.js + Express + MongoDB** on the back end.

It lets users track body weight, calculate BMI, log strength workouts, get **progressive overload** suggestions, and compete with friends on a weekly leaderboard — inspired by Duolingo's consistency-based social motivation.

> **Project status:** Phases 1–6b are complete and verified, and **Phase 7 has begun**: the profile is now fully editable in the app — display name, training goal, preferred unit and height, including *removing* a height (which is what keeps the BMI honest). Streaks are the next Phase 7 feature. See [Implementation phases](#implementation-phases).

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
| Editable profile — display name, training goal, preferred unit and height, all in-app | ✅ Phase 2, extended in Phase 7 |
| Dashboard summary with honest empty states | ✅ Phase 3 |
| Body-weight tracking (CRUD, kg/lb, history, chart, period change) | ✅ Phase 3 |
| BMI from latest weight + height, with adult categories & disclaimers | ✅ Phase 3 |
| Workout logging (workout → exercises → sets of weight × reps) | ✅ Phase 4 |
| Progressive overload comparison + rule-based suggestions | ✅ Phase 5 |
| Friends (search, request, accept/reject, list, remove) | ✅ Phase 6a |
| Weekly leaderboard scored on the server | ✅ Phase 6b |
| Activity streaks (current + longest) | Planned (Phase 7) |
| Settings (theme, unit, height, goal, privacy, logout) | 🟡 theme, logout, unit, goal and height done; privacy still to come |

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
- **Business rules live on the server** (BMI, training volume, progressive-overload verdicts, friendship rules, streaks, leaderboard score) so results are trustworthy and can be unit-tested.
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
services (pure logic: auth, profiles, BMI, volume, progression, friendships, leaderboard — and later streaks)
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

### Android request flow (Phase 5 — progressive overload)

```
Workouts tab ── "Progress" ──► Progress screen (pushed)
                                  │  LaunchedEffect(Unit) { refresh() }
                                  ▼
                             ProgressionViewModel
                               ├─ trackedExercises()          → the list
                               └─ progression(exercise)       → the trend
                                  ▼
                             ProgressionRepository ── maps DTOs ──► Retrofit ──► OkHttp
                                  ▼
                             ProgressionUiState  (StateFlow)
                               ├─ selectedExercise == null → the list
                               └─ selectedExercise != null → the trend
```

- The screen has **two levels** — a list of logged exercises, then one exercise's trend — and both live in one state object. Selecting is a state change, not a navigation push, because an exercise name is free text with spaces and encoding it into a route would add nothing.
- The whole judgement is done **on the server**. The app maps the verdict string to an enum and draws it; it never re-implements a rule, so a threshold change never needs an app release.
- An exercise with fewer than two sessions has no trend; the payload says `insufficient_data` and shows a suggestion to log it again rather than inventing a comparison.

### Android request flow (Phase 6a — friends)

```
Friends tab
   │  LaunchedEffect(Unit) { refresh() }
   ▼
FriendsViewModel
   ├─ friends()        → my accepted friends
   ├─ requests()       → { incoming, outgoing }
   └─ onQueryChange()  → 350 ms debounce → searchUsers(query)
   ▼
FriendRepository ── maps DTOs ──► Retrofit ──► OkHttp
   ▼
FriendsUiState (StateFlow)
   ├─ friends / requests / busyIds / loadError
   └─ query / results / isSearching / hasSearched
```

- Search is **debounced** (350 ms) so typing a whole username is one request; the box clears its results when emptied rather than leaving a stale search on screen.
- What a result shows — *Add*, *Requested* or *Already friends* — is **derived from the lists already in state** (`FriendsUiState.relationTo`), so the server does not have to repeat the relationship with every search hit.
- A change is keyed on an id while it is in flight (`busyIds`), so the exact row that was tapped shows a spinner and a double-tap cannot fire twice.
- Every rule about who may befriend whom (not yourself, not an existing friend, only the recipient may accept) is enforced **on the server**; the app shows the server's message verbatim instead of duplicating the rules.
- Refreshing after a change is what moves a person out of *Requests* and into *Friends* — the two lists are one state object, so they can never disagree.

### Android request flow (Phase 6b — weekly leaderboard)

```
Friends tab ─── "Weekly leaderboard" card ───► Leaderboard screen (pushed)
                                                   │  LaunchedEffect(Unit) { refresh() }
                                                   ▼
                                             LeaderboardViewModel
                                                   │  weekly()
                                                   ▼
                                             LeaderboardRepository ── maps DTOs ──► Retrofit ──► OkHttp
                                                   ▼
                                             LeaderboardUiState (StateFlow)
                                                   └─ loading / error / board { week, scoring, entries }
```

- The leaderboard is a **pushed screen**, not a panel on the Friends tab: it answers a different question (*"how is everyone doing this week?"*) with its own refresh and its own back stack, and it leaves the two friend lists untouched.
- Ranking and scoring are **entirely server-side**. The app receives `rank`, `score` and the per-entry `activeDays`/`workoutCount` already computed and only draws them — no rule is duplicated in the client.
- The payload carries the **scoring rule itself** (`scoring: { pointsPerDay, bonusDays, bonusPoints }`), so the screen can explain a score without hard-coding the constants.
- `isMe` marks your own row, so the client never needs its own user id to highlight it; the board always contains at least *you*, even with no friends.
- No new collection, dependency or index was needed: `weeklyLeaderboard()` is a **single aggregation over `workouts`** that reuses the existing `user_workout_recent` index.
- `formatWeekRange()` renders the window in **UTC** (subtracting a day from the exclusive end) so what the screen shows is exactly the Monday→Monday window the server scored.

### Android request flow (Phase 7 — profile editing)

```
Profile tab
   │  one card per field; each chip tap / Save button calls its own action
   ▼
AuthViewModel   updateDisplayName / updateFitnessGoal / updateWeightUnit / updateHeight
   │  submit(field = …)
   ▼
AuthRepository ──► Retrofit ──► OkHttp
   │                ├─ updateProfile(UpdateProfileRequest)  // omitted = keep, height NOT clearable
   │                └─ updateHeight(UpdateHeightRequest)     // always sends heightCm, so null clears
   ▼
AuthState.SignedIn(user)   ← the single source of truth, refreshed from the server's reply
   │
   ▼
AuthFormState(field, saved) + fieldErrors  →  only the card that acted shows the outcome
```

- `AuthViewModel` is the **same** ViewModel the auth screens use: the repository already owns "who is signed in", so a profile write needs no second source of truth — the server's response *is* the new `AuthState`.
- The shared `AuthFormState` records **which field** acted (`field` + `saved`), so four cards do not all claim the same confirmation. `errorFor(field)` routes a server error to the one card it belongs to, and a failure naming other fields is ignored rather than shown in the wrong place.
- Chips save **immediately** — a tap *is* the edit — while the display name and height use a text field whose Save button stays disabled until the value actually differs from the stored one.
- Height is the only **clearable** field, and clearing it needs its own request body; the reason is documented under [Phase 7](#implementation-phases) below.
- Nothing is faked: every control here performs a real `PATCH` and the screen re-renders from the server's response.

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
│               ├── domain/model/          # User, AuthState, ThemeMode, Weight, Workout,
│               │                          # Progression, Friend, Leaderboard, enums
│               ├── data/
│               │   ├── remote/            # RepRiseApi, ApiClient, AuthInterceptor,
│               │   │                      # ApiResult + ApiCall (error parsing),
│               │   │                      # RepRiseJson (the one JSON config), dto/
│               │   ├── local/             # TokenStore (encrypted), SettingsStore
│               │   └── repository/        # AuthRepository (session source of truth),
│               │                          # WeightRepository (weight + dashboard),
│               │                          # WorkoutRepository (workout logging),
│               │                          # ProgressionRepository (progressive overload),
│               │                          # FriendRepository (friends + requests),
│               │                          # LeaderboardRepository (weekly leaderboard)
│               ├── util/                  # Formatting (ISO dates, kg/lb numbers, volume)
│               ├── ui/theme/              # Color, Type, Theme, SettingsViewModel
│               ├── ui/auth/               # LoginScreen, RegisterScreen, AuthViewModel
│               ├── ui/home/               # HomeScreen (dashboard) + DashboardViewModel
│               ├── ui/weights/            # WeightsScreen + WeightsViewModel
│               ├── ui/workouts/           # WorkoutsScreen, WorkoutEditorScreen + ViewModels
│               ├── ui/progression/        # ProgressionScreen + ProgressionViewModel
│               ├── ui/friends/            # FriendsScreen + FriendsViewModel
│               ├── ui/leaderboard/        # LeaderboardScreen + LeaderboardViewModel
│               ├── ui/profile/…
│               └── components/            # reusable Compose widgets (ScreenContainer,
│                                          # DetailScreen, RepRiseCard, WeightChart,
│                                          # UserAvatar, …)
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
    │   ├── routes/                        # index, health, auth, users, weights, dashboard,
    │   │                                  # workouts, progression, friends, leaderboard
    │   ├── controllers/                   # auth, users, weights, workouts, dashboard, progression,
    │   │                                  # friendship, leaderboard
    │   ├── services/                      # auth, user, weight, workout, bmi, dashboard, progression,
    │   │                                  # friendship, leaderboard
    │   ├── validators/                    # common.fields, auth, user, weight, workout, progression, friendship (Zod)
    │   └── utils/                         # logger, ApiError, password, token, units, objectId
    ├── scripts/                           # read-only dev tools (inspect-db.js, serve-db.js)
    └── tests/
        ├── helpers/                       # setup, app, db (shared test utilities)
        ├── health.test.js
        ├── auth.test.js
        ├── weights.test.js
        ├── workouts.test.js
        ├── progression.test.js
        ├── friends.test.js
        └── leaderboard.test.js
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

### Inspecting the database (development scripts)

`mongosh` and Compass are not always available, so two small **read-only** helpers live in `backend/scripts/`. They use the backend's own `express`/`mongodb` dependencies *and its real service functions* (volume, BMI, leaderboard score), so what they show cannot drift from what the API returns. Run them from `backend/`:

```bash
# a terminal report of exactly what the app has stored
node scripts/inspect-db.js                 # overview: collection counts + users
node scripts/inspect-db.js user <username> # one person: profile, weights, workouts, friends, points
node scripts/inspect-db.js indexes         # the indexes the app created
node scripts/inspect-db.js --help          # every command

# the same data as a read-only web page — handy for the demonstration
node scripts/serve-db.js                   # http://localhost:8081  (login: reprise / reprise123)
```

`inspect-db.js` is the quickest way to *see* a document without opening Atlas, and `raw <username>` prints the stored document verbatim (including the bcrypt `passwordHash`, never a plain-text password). `serve-db.js` renders every collection and every user as HTML; it binds to `127.0.0.1` by default — set `DBVIEW_HOST=0.0.0.0` (plus a real `DBVIEW_PASSWORD`) to reach it from another device on the same network. **Neither script writes, updates or deletes anything.**

Both default to the `MONGODB_URI` from `.env`; for a local server, pass it inline:

```bash
MONGODB_URI="mongodb://127.0.0.1:27017" node scripts/inspect-db.js
```

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

**Over USB, none of that is needed.** `adb reverse` forwards the phone's own `localhost` to your computer, and `127.0.0.1` is *already* on the cleartext allow-list — so a USB install needs no LAN IP and nothing to remember to revert:

```bash
adb reverse tcp:4000 tcp:4000     # re-run each time the phone reconnects
```

Point `API_BASE_URL` at `http://127.0.0.1:4000/` (instead of `10.0.2.2`) for that. It also works on Wi-Fi networks that isolate clients from each other.

> Either way, **revert the change before committing**: the committed debug URL is the emulator's `10.0.2.2`, so a pushed build stays correct for anyone running an emulator.

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

**Profile update flow** *(the app's UI for goal, unit and height arrived in Phase 7)*

```
PATCH /api/users/me  { displayName?, username?, fitnessGoal?, weightUnit?, heightCm?, profileImageUrl? }
                  -> 200 { user }
```

- The body is **partial**: an omitted field keeps its current value. `heightCm` and `profileImageUrl` are the exception — an explicit `null` **clears** them.
- The Android app expresses "leave it alone" by *omitting* the field (`UpdateProfileRequest` relies on the serializer skipping properties left at their default) and "clear the height" with a **separate body** (`UpdateHeightRequest`, whose `heightCm` has no default and is therefore always encoded). That way `null` is never ambiguous.
- A `username` that is already taken returns `409`. The app does not expose username or profile-image editing yet.
- The response is the **updated user**, and `AuthRepository` adopts it as the session state, so the UI can never drift from what was stored.

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

### Implemented (Phase 5 — progressive overload assistant)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/progression/exercises` | yes | the exercises I have logged, with counts and last date |
| GET | `/api/progression?exercise=&limit=` | yes | one exercise's recent sessions + a rule-based verdict |

**Progression flow**

```
GET /api/progression/exercises
      -> 200 { exercises: [ { name, timesLogged, lastPerformedAt } ] }
GET /api/progression?exercise=Bench%20Press&limit=10
      -> 200 { exercise, unit, sessionCount, sessions[], comparison }
```

The `comparison` is the interesting part:

```json
{
  "hasPrevious": true,
  "latest":   { "workoutDate": "…", "topWeight": 65, "totalReps": 24, "totalVolumeKg": 1560, "sets": [ … ] },
  "previous": { "workoutDate": "…", "topWeight": 60, "totalReps": 24, "totalVolumeKg": 1440, "sets": [ … ] },
  "topWeightDelta": 5,
  "totalRepsDelta": 0,
  "totalVolumeDeltaKg": 120,
  "verdict": "progressing",
  "suggestion": "Progress: your top set went from 60 to 65 kg. Next session, aim for 67.5 kg for the same reps — or add one rep per set first."
}
```

Rules the assistant applies (all in `src/services/progression.service.js`, **no machine learning**):

- A session is one **occurrence** of an exercise inside one workout. Sessions are compared newest-against-previous, so an exercise needs **two** sessions before it has a trend.
- The verdict is one of `progressing`, `holding`, `regressing`, or `insufficient_data`. It is decided in **kilograms**, so flipping the display unit never changes the verdict.
- It counts as **progressing** if the total tonnage rises by more than a small (2%) tolerance, **or** the heaviest set gets heavier, **or** more reps are done without losing volume. It is **regressing** when tonnage drops beyond the tolerance (or the top set gets lighter with no extra reps); everything else is **holding**.
- The `suggestion` is assembled from the numbers, in the user's unit, from a fixed set of sentences — it is derived, never generated by a model.
- Deltas are returned in the user's display unit (except `totalVolumeDeltaKg`, which — like all volume — is kilograms).
- An exercise that has **never** been logged is a `200` with an empty `sessions` array and an `insufficient_data` verdict, not a `404`: the app can always render the same shape.
- Exercise names are matched **exactly** (the same string the app shows as a suggestion chip). The collated index below keeps that lookup cheap; the trade-off is documented under *Known limitations*.

### Implemented (Phase 6a — friends)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/users/search?username=<text>` | yes | prefix-search other users (excludes me) — shipped in Phase 2, listed here because it is what the Friends tab uses |
| POST | `/api/friends/requests` | yes | send a friend request by username |
| GET | `/api/friends/requests` | yes | pending requests, split into `incoming` / `outgoing` |
| POST | `/api/friends/requests/:id/accept` | yes | accept a request sent **to me** |
| DELETE | `/api/friends/requests/:id` | yes | decline (recipient) or withdraw (sender) a pending request |
| GET | `/api/friends` | yes | my accepted friends, alphabetically |
| DELETE | `/api/friends/:userId` | yes | remove a friendship |

**Friendship flow**

```
POST /api/friends/requests          { "username": "bob_runs" }
      -> 201 { request: { id, direction: "outgoing", user: { … }, createdAt } }
GET  /api/friends/requests
      -> 200 { incoming: [ { id, direction, user, createdAt } ], outgoing: [ … ] }
POST /api/friends/requests/<id>/accept
      -> 200 { friend: { id, username, displayName, profileImageUrl } }
DELETE /api/friends/requests/<id>   -> 204 No Content
GET  /api/friends                   -> 200 { friends: [ { id, username, displayName, profileImageUrl } ] }
DELETE /api/friends/<userId>        -> 204 No Content
```

Design notes (`src/services/friendship.service.js`):

- A friendship is **one row per pair**, not two. The row records who asked (`requesterId`) and who was asked (`recipientId`) so a *pending* request has a clear direction; once accepted the relationship is symmetric and both users are simply friends.
- The pair is identified by a canonical **`pairKey`** — the two ids sorted and joined — which carries a **unique index**. That is what makes "A asks B" and "B asks A" impossible to end up as two rows, even if two requests race past the "does it already exist?" check.
- Rules enforced **server-side**: you cannot befriend yourself, you cannot request someone you are already friends with (or already have a pending request with → `409`), and **only the recipient may accept** a request.
- An unrelated user acting on a request gets a **`404`, not a `403`** — there is no reason to confirm that somebody else's request exists.
- `DELETE /api/friends/requests/:id` covers both *decline* (recipient) and *withdraw* (sender): either party may remove their own pending row, and both are the same operation underneath.
- A user who has since been **deleted** is skipped when their rows are read, so an orphaned request cannot crash a list.

### Implemented (Phase 6b — weekly leaderboard)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/leaderboard/weekly` | yes | me + my accepted friends, ranked by points earned this week |

**Leaderboard flow**

```
GET /api/leaderboard/weekly
      -> 200 {
           week:    { startDate, endDate },                       // Monday 00:00 UTC -> next Monday (end is EXCLUSIVE)
           scoring: { pointsPerDay, bonusDays, bonusPoints },
           entries: [ { rank, user, score, activeDays, workoutCount, isMe } ],
           me:      { rank, user, score, activeDays, workoutCount, isMe }
         }
```

An entry looks like this on the wire:

```json
{
  "rank": 1,
  "user": { "id": "…", "username": "sam_spotter", "displayName": "Sam Spotter", "profileImageUrl": null },
  "score": 25,
  "activeDays": 3,
  "workoutCount": 4,
  "isMe": false
}
```

Rules the server applies (`src/services/leaderboard.service.js` — everything here is computed, never stored):

- The window is **fixed and UTC-based**: this week's Monday `00:00` up to (but not including) the next Monday `00:00`, so `endDate` is *exclusive*.
- Only **distinct calendar days** on which at least one workout was logged count: `score = activeDays × pointsPerDay`, **plus `bonusPoints` once `activeDays` reaches `bonusDays`**. With the shipped constants that is `10` points per active day and a `+5` bonus from `3` days — so it rewards **consistency over volume**, and one huge session cannot out-rank several short ones.
- The board holds **you and your accepted friends** (`friendIdsOf`), so a brand-new user with no friends still sees a one-row board instead of a blank screen.
- `rank` uses **standard competition ranking** — equal scores share the better rank and the next rank is skipped — so genuine ties are shown as ties.
- `isMe` marks your own row, so the client never needs its own id to highlight it.
- Scores are **recomputed on every request**; there is deliberately no `points` collection that could drift from the workouts it summarises. **No new index is required** — the aggregation reuses `user_workout_recent`.

### Phase 7 — in progress

**Profile editing (shipped).** The four personal fields are written from the Profile tab with a real `PATCH /api/users/me`: the display name, the training goal, the preferred unit and the height. Each field has its own card, so only the card that acted shows the confirmation or the error.

> One detail is worth recording because it is invisible until it misbehaves. `RepRiseJson` leaves `encodeDefaults` at kotlinx's default of `false`, so a property still holding its declared default is **dropped from the request body**. That is exactly how "only send what changed" works — and it also means a `null` in `UpdateProfileRequest` can never mean *clear*, because it is indistinguishable from an omitted field. Clearing a height therefore uses a separate `UpdateHeightRequest`, whose `heightCm` has **no default** and is consequently always encoded. `ProfileRequestEncodingTest` pins both behaviours, and the server test `PATCH /api/users/me clears the height with an explicit null` proves the other half.

**Streaks (next).** `GET /api/dashboard` will gain a `streak` section (current and longest), following the same pattern as the sections added for weight/BMI in Phase 3 and training totals in Phase 4 — so the streak appears without the app having to be rewired.

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
One row per relationship. `_id`, `pairKey`, `requesterId`, `recipientId`, `status` (`pending`|`accepted`), `createdAt`, `updatedAt`, `respondedAt?`.
*`pairKey` is the two user ids sorted and joined (`a:b`); it is identical for A→B and B→A, and is what makes the unique index below forbid a duplicate relationship. Once `status` is `accepted` the direction stops mattering — both users are simply friends. A declined or withdrawn request is **deleted**, not kept as a `rejected` row, so the pair can try again later.*

### Indexes
| Collection | Index | Purpose |
|---|---|---|
| `users` | `usernameNormalized` **unique** | usernames are unique, case-insensitively |
| `users` | `emailNormalized` **unique** | emails are unique, case-insensitively |
| `weight_entries` | `{ userId: 1, measuredAt: -1 }` | "my weight history, newest first" |
| `workouts` | `{ userId: 1, workoutDate: -1 }` | "my workout history, newest first" |
| `workouts` | `{ userId: 1, 'exercises.name': 1, workoutDate: -1 }` (`user_exercise_recent`) | one exercise's history for the progressive-overload comparison (Phase 5) — used by both progression endpoints |
| `friendships` | `{ requesterId: 1, status: 1 }` (`friendship_requester_status`) | requests I sent / my friendships |
| `friendships` | `{ recipientId: 1, status: 1 }` (`friendship_recipient_status`) | requests I received |
| `friendships` | `{ pairKey: 1 }` **unique** (`uniq_friendship_pair`) | exactly **one** row per pair — a duplicate friendship is impossible, even under concurrent requests |

Indexes are created automatically at startup by `src/database/indexes.js` and are visible in Atlas under **Collections → Indexes**.

**Leaderboard scores and streaks are not stored** — there is no `points` or `streaks` collection. They are recomputed from `workouts` on every request so they can never drift out of sync with the sessions that justify them. The weekly leaderboard therefore adds **no new collection and no new index**: its single aggregation reuses the `user_workout_recent` index above.

---

## Testing

**Backend**

```bash
cd backend
npm test
```

Uses Node's built-in test runner (`node:test`) and built-in `fetch` — no extra test dependencies. **111 tests** currently pass.

- **Phase 1** — unknown-route `404` handling, and the health check in both the *degraded* and *ok* states.
- **Phase 2** — registration (incl. duplicate username/email → `409`, case-insensitive email), login (correct / wrong password / unknown user), JWT protection (`401` for missing or malformed tokens), profile updates, and username search (excludes the caller, leaks no private fields).
- **Phase 3** — weight schemas (per-unit range, future/back-dated timestamps, empty update bodies), `401` on every weight route, create/list/latest/read/update/delete, unit conversion after a preference change, ownership isolation (another user gets `404` and the owner's entry is untouched), BMI from height + latest weight, `bmiUnavailableReason`, and the 30-day change.
- **Phase 4** — workout schemas (nested exercises/sets, plausible loads, future dates, clearing a duration/note with an explicit `null`, empty update bodies), `401` on every workout route, create with a derived `summary`, the per-unit load check naming the exact set, the distinct-exercise catalogue, list/read/update (including replacing the exercise list)/delete, ownership isolation, and the dashboard's rolling training totals.
- **Phase 5** — the rule-based verdict in isolation (a heavier top set, more volume, more reps at equal volume, a wobble *inside* the tolerance reading as `holding`, a real drop reading as `regressing`, and one-or-zero sessions reporting `insufficient_data`), the suggestion text for kg and lb accounts, the query schema, `401` on both progression routes, and — end to end — an empty-but-valid trend for an unknown exercise, `progressing` from two real sessions, `regressing` from a lighter session, the exercise list's counts, and that one user cannot read another's progression.
- **Phase 6a** — the canonical pair key is order-independent, the request/param schemas, `401` on **every** friends route, and — end to end — a request visible to both sides, exactly **one** row per pair (a repeat, and the reverse direction while pending, are both `409` and add nothing), accepting making both users friends, only the recipient being able to accept (the sender and a stranger both get `404`), an already-answered request being `409`, decline and withdraw both removing the row, neither party being able to touch a request they are not part of, either friend being able to remove the friendship (and a second removal being `404`), re-requesting a friend being `409`, and friends being listed alphabetically.
- **Phase 6b** — the scoring rule in isolation (a day with nothing logged scores `0`, `10` per active day, and the `+5` consistency bonus landing **exactly** at three days and not before) and the week boundary (a Wednesday, a Monday and a Sunday night each resolving to the correct Monday `00:00` UTC). Then, end to end: `401` without a token, a friendless user seeing **only themselves on zero**, the board being **me and my friends and nobody else**, a second workout on the same day **not** scoring twice while both still count in `workoutCount`, last week's session not counting at all, a trainer ranking above an idle friend, and two equal scores **sharing a rank**.
- **Phase 7 (profile)** — on top of the existing "set and validate" coverage: an explicit `heightCm: null` **clears** the stored height (asserted in the response *and* in the database) while everything else is left untouched, which is the semantic the app's height editor depends on.

> **Tests never touch your real data.** `tests/helpers/setup.js` points the suite at a separate **`reprise_test`** database on the same cluster. Every test user is deleted before and after the run **along with everything that points at them** (`deleteUserDataByPrefix` — weight entries, workouts and friendships), so no orphaned documents accumulate. Tests that need the database are **skipped** (not failed) when MongoDB is unreachable, so the pure-validation tests run anywhere.

**Android**

```bash
cd android
./gradlew :app:testDebugUnitTest          # JVM unit tests (no device needed)
./gradlew :app:connectedDebugAndroidTest  # instrumented tests (needs a device/emulator)
```

The JVM suite (**117 tests**) covers the parts where a mistake is invisible until
runtime:

- **`ApiCallTest`** — every shape the API can answer with: a successful response,
  a `400` whose `details` array becomes per-field errors, a `409` whose
  `details` object names the offending field, a plain `401`, an unreadable body
  (falls back to a status-based message), and an unreachable server (no status
  code). These run with no network and no emulator — the `Response` objects are
  built in code.
- **`ApiCallTest` (204)** — `apiCallUnit` treats a bodyless `204` as success while
  still surfacing the server's message on a `404`.
- **`DomainMappingTest`** — pins the `fitnessGoal` / `weightUnit` / BMI-category /
  progression-verdict strings to the backend's contract, and checks the
  unknown-value fallbacks (including the verdict's deliberate fallback to
  `insufficient_data`) and the theme-mode resolution.
- **`WeightRepositoryTest`** — drives a hand-written fake `RepRiseApi` to prove the
  DTO ⇄ domain mapping: values, unit codes, the canonical `weightKg`, the BMI
  category, a `null` change, the dashboard's training section, the delete path,
  and that a rejection passes through with the server's message intact.
- **`WorkoutRepositoryTest`** — the same fake-API technique for the workout tree:
  exercises → sets, the display weight *and* the canonical `weightKg`, the derived
  summary, the `days`/`limit` query, the exercise catalogue, the request bodies the
  editor produces, and — as its own test — that a `null` duration/note really is
  **encoded** (not dropped) so a field can be cleared.
- **`ProgressionRepositoryTest`** — the progression payload maps faithfully:
  sessions newest-first, the deltas, every verdict string to its enum, the
  unknown-verdict fallback, the display unit and canonical `weightKg` for a pound
  account, an empty-but-valid trend, the `exercise`/`limit` query, the tracked-exercise
  list, and a rejection passing through with the server's message.
- **`FriendRepositoryTest`** — the friend list and search results map onto
  `UserSummary`, requests keep their direction and the other person, a direction
  the app does not know is **skipped rather than crashing**, each call reaches the
  right endpoint with the right argument (the username, the request id, the user
  id), a `409` and a `404` pass through with the server's message intact, and an
  empty friend list is still a success.
- **`LeaderboardRepositoryTest`** — the leaderboard payload maps faithfully: the
  week window, the scoring rule, every entry's rank/score/active-days/workout
  count and the person behind each row (not just their id), the derived `me` row
  picking the entry flagged `isMe`, an empty board having no `me` row, and a
  `401` passing through with the server's message.
- **`ProfileRequestEncodingTest`** — pins the wire shape of the two profile request
  bodies: an untouched field is **absent**, goal/unit travel under the server's
  names, a `null` height on the partial request is **dropped** (so it cannot clear
  anything), and the dedicated height request sends an explicit `null` — the trick
  that makes removing a height possible. It runs against the app's real `Json`
  configuration, so it fails if `encodeDefaults` is ever changed.
- **`AuthFormStateTest`** — the routing rules behind the profile cards: a field
  error shows only beside its own field, a generic failure lands on the field the
  user just acted on, a specific field error wins over the generic message, and
  only the field that was saved reports success.
- **`HeightDraftTest`** — the height box's pure parser: empty means *clear*,
  surrounding whitespace is ignored, the 50–272 cm bounds are inclusive, and text
  or decimals are rejected rather than silently rounded.
- **`FriendsUiStateTest`** — the pure helpers that decide what the search box
  shows next to a result: *FRIEND*, *REQUESTED*, *RESPONDS* or *NONE*, which id a
  row's spinner is keyed on (the request id for accepting, the user id for adding
  and removing), and finding the incoming request for a user.
- **`UserAvatarTest`** — the `initialOf` rule behind the avatar circle: the first
  letter of the display name (upper-cased), surrounding whitespace ignored, a
  blank display name falling back to the username, and both blank returning an
  empty string rather than throwing.
- **`WorkoutDraftParseTest`** — the editor's validation rules, which live in a pure
  function (`parseWorkoutDraft`) precisely so they can be tested without a device:
  blank names, reps/weights, "0 kg is fine but −5 is not", a comma decimal
  separator, a blank vs. nonsense duration, and the no-exercises case.
- **`FormattingTest`** — ISO-8601 parsing, checked against `java.time.Instant` as
  an independent oracle, plus signed weight/int/volume formatting, the
  editable-number helper (no floating-point noise leaking into a form), and
  volume grouping.

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

> _Add screenshots of the finished app here before the demonstration. Suggested shots: dark-mode Home, weight history chart, an active workout, the progressive-overload comparison, the Friends tab, and the weekly leaderboard._

---

## Professor demonstration procedure

The full sequence below is the target for Phase 7. **Currently steps 1–10 are runnable, in the app itself.**

1. Start the backend and show `GET /api/health` returning `database.connected: true`. ✅
2. Register a new user in the app → open Atlas → show the `users` document with `passwordHash` (**no** plain-text password). ✅
3. You are signed in automatically: rename yourself in Profile and switch **Appearance** between System / Light / Dark. ✅
4. Show the errors the app surfaces (wrong password, duplicate username, weak password), sign out, then sign back in — close and reopen the app to prove the session is remembered. ✅
5. Log a body weight from the **Weight** screen (Home → *Log weight*) → show the matching `weight_entries` document in Atlas. ✅
6. In **Profile**, switch the preferred unit to pounds → reload → the **same stored entry is displayed in lb**, proving conversion happens on read. Add a **height** in the same tab to show **BMI** calculated from it plus the latest weight, with its WHO category and disclaimer — then **clear the height** and show the BMI card honestly explaining what is missing. Change the **training goal** while you are there and point out that each card confirms its own save. ✅
7. Create a workout from the **Workouts** tab (*Log workout*): name it, add an exercise with several sets, and save → show the nested `workouts` document in Atlas, and the derived `summary` on the card. Edit it, then delete it. ✅
8. Log a **second** session with the same exercise, then open **Progress** (Workouts → *Progress*), tap the exercise and show the **progressive-overload comparison**: the latest-vs-previous numbers, the verdict, and the rule-based suggestion. Log a lighter session and show the verdict flip to *Regressing*. ✅
9. Register a second test account, then on the first account search its username in the **Friends** tab (*Find by username*), send a request, switch accounts, and accept it → show the `friendships` document with its `pairKey` and `status: "accepted"`, and the `uniq_friendship_pair` index. Remove the friendship to show the row disappear. ✅
10. Open **Friends → *Weekly leaderboard*** and show the week: the Monday→Monday UTC window, each friend's rank and points, the `+5` consistency bonus appearing once someone reaches three active days, and your own row marked. Explain that the score is a **live aggregation over `workouts`** — nothing is stored. ✅
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
| 5 | Progressive overload + unit tests | ✅ **complete & verified** (backend + Progress screen with rule-based verdicts) |
| 6a | Friends (search, requests, friend list, removal) | ✅ **complete & verified** (backend + full Friends tab) |
| 6b | Weekly leaderboard scored on the server | ✅ **complete & verified** (backend + a leaderboard screen reached from the Friends tab) |
| 7 | Streaks, settings, polish, demo prep | 🟡 **in progress** — in-app profile editing (name, goal, unit, height) shipped; streaks next |

---

## Known limitations & future improvements

- **Phases 1–6b complete, Phase 7 in progress** — authentication, profiles, body-weight tracking, BMI, the dashboard, workout logging, the progressive-overload assistant, friends, the weekly leaderboard and in-app profile editing (name, goal, unit, height) all work end to end. Streaks are still to come.
- The **username** and **profile image** are the only profile fields still read-only in the app: the API accepts both on `PATCH /api/users/me` (the username is normalised and uniqueness-checked, and a clash is a `409`), but there is no UI for them yet.
- There is no **privacy** setting yet. The only privacy guarantee shipped is on the server: another user can only ever see your display name, username and image (never your email, goal, height or unit).
- Weight entries and workouts are logged with the **current time** in the app (the common case). Back-dating is supported by the API (`measuredAt` / `workoutDate`) and documented above, but has no date picker in the UI yet.
- A workout's **training volume is always shown in kilograms**. It is a figure for comparing sessions across units, not a body weight the user chose a unit for, so it is deliberately not converted. Per-set weights *are* converted to the preferred unit.
- The workout editor does not offer a per-exercise **unit picker**: new sets are entered in the user's preferred unit (the server defaults and converts). Exercises that were originally logged in another unit are shown in the preferred unit and saved back in it.
- The weight history list is capped at the server's default page (100 entries); older entries are still counted in "All time" totals and can be paged through the API, but the app does not paginate yet. The Workouts list shows the 50 most recent sessions.
- Session tokens are **stateless JWTs with no server-side revocation list**, so logout clears the token locally. Acceptable for a single-device demo; production would add refresh/revocation.
- Leaderboard scoring, streaks and progressive overload are all **recomputed on demand** (no cached snapshots). Fine for an MVP; a snapshot collection could be added if leaderboards grow large.
- The weekly leaderboard is **friends-only** — there is no global board — and it scores **days trained**, not effort or strength, so a heavy session and a light one on the same day are worth the same. That is intentional (it rewards consistency), but it means the board is not a strength ranking. The week is **UTC Monday → Monday**, so a user in a distant time zone may see it roll over at an unexpected local hour.
- Progressive overload matches exercise names **exactly** (case- and spelling-sensitive), because the indexed lookup is a plain equality match — which keeps it fast and predictable. The editor's "previously logged" chips steer you onto the same spelling, so names converge in practice; a future version could store a normalised name alongside the display name.
- Progression compares only the **two most recent** sessions of an exercise. That answers "am I moving forward right now?" cleanly; longer-term trend lines would need a chart over `GET /api/progression`'s full session list (which the endpoint already returns).
- BMI is a **screening measure only** and is not a measure of muscle mass or body-fat percentage. Adult categories are not presented as definitive for users under 20.
- Progressive-overload suggestions are **advisory, rule-based** guidance — not medical advice — and never change a workout automatically.
- The Friends screen shows an avatar as the person's **initial**: the project deliberately has no image-loading library, so `profileImageUrl` is stored and returned by the API but not drawn. Friend requests carry **no message**, and the friends list is capped at the server's `FRIEND_LIST_MAX` (500).
- Optional future work: push notifications for streaks, profile images, friend workout sharing, data export.
