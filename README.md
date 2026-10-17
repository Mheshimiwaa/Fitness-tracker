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
10. [Deploying the backend (so it works off your network)](#deploying-the-backend-so-it-works-off-your-network)
11. [Android — setup & run](#android--setup--run)
12. [API base URL (emulator vs. physical device)](#api-base-url-emulator-vs-physical-device)
13. [API endpoints](#api-endpoints)
14. [Database schema & indexes](#database-schema--indexes)
15. [Testing](#testing)
16. [Common errors & solutions](#common-errors--solutions)
17. [Screenshots](#screenshots)
18. [Professor demonstration procedure](#professor-demonstration-procedure)
19. [Implementation phases](#implementation-phases)
20. [Known limitations & future improvements](#known-limitations--future-improvements)

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
| Activity streaks (current + longest) | ✅ Phase 7 |
| Profile picture, taken with the camera or picked from the gallery, stored by the API | ✅ Phase 7 |
| Settings (theme, unit, height, goal, reminder + its sound, logout) | ✅ theme, unit, goal, height, logout, the gym reminder and its sound done; privacy still to come |

### Optional improvements
- Pagination beyond the first page of history
- Friend workout-sharing toggle
- Data export

---

## Technology stack

**Android**
- Kotlin, Jetpack Compose, Material 3
- Navigation Compose, ViewModel, Coroutines & Flow
- Retrofit + OkHttp (Kotlinx Serialization)
- Coil (loading profile pictures from the API)
- DataStore (theme/reminder) + EncryptedSharedPreferences (JWT)
- WorkManager (the gym-reminder notification)
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
   (theme, reminder, JWT)                       │  weight_entries      │
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
   │  └─ two kinds of request deliberately bypass the JSON path:
   │     PUT /api/users/me/photo is parsed as a raw buffer (up to PHOTO_MAX_BYTES),
   │     and GET /uploads/… is served from the photos collection by its own route.
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
services (pure logic: auth, profiles, BMI, volume, progression, friendships, leaderboard, streaks, photos)
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

### Request flow (Phase 7 — the profile picture)

The picture is the one profile field that is **not** JSON: it is a file, and it
travels as raw bytes in both directions.

```
Profile tab · ProfilePhotoCard   (Take a photo · Choose from gallery · Remove)
   │
   │  camera: ActivityResultContracts.TakePicture  → FileProvider URI in cache/avatar/
   │  gallery: ActivityResultContracts.PickVisualMedia        (no permission either way)
   ▼
AuthViewModel   uploadProfilePhoto(uri) / removeProfilePhoto()
   │  AvatarImage.toJpeg(context, uri)  ← bounds-only decode → inSampleSize →
   │                                        exact scale to 512 px → EXIF rotation →
   │                                        JPEG at 85 %, metadata dropped
   ▼
AuthRepository ──► Retrofit ──► OkHttp
   │                ├─ uploadProfilePhoto(@Body RequestBody)  → PUT /api/users/me/photo
   │                └─ deleteProfilePhoto()                   → DELETE /api/users/me/photo
   ▼
backend · express.raw({ type: PHOTO_CONTENT_TYPES })  →  photo.service.sniff(bytes)
   │  the format comes from the MAGIC BYTES, never from the filename or Content-Type
   │  stored in the `photos` collection as _id = <userId>-<random>.<jpg|png|webp>;
   │  the doc it replaced is deleted, and the URL is built from the request's host
   ▼
AuthState.SignedIn(user)   ← profileImageUrl refreshed from the server's reply
   │
   ▼
UserAvatar   (Coil AsyncImage with ContentScale.Crop, initial letter drawn underneath)
   │  rendered on the profile card, in the friends list and on the leaderboard
   ▼
GET /uploads/<name>   ← read from MongoDB, unauthenticated by design (an <img> URL cannot
                        carry a bearer token, and the name is unguessable)
```

- **The image is re-encoded on the phone, not sent as-is.** A modern camera produces several megabytes and a 4000-pixel edge; the avatar is 512. Decoding only the bounds first, then a power-of-two `inSampleSize`, then an exact scale, keeps a large photo from ever being held in memory at full size. The result is a small JPEG, so uploads are quick on mobile data.
- **EXIF metadata is deliberately dropped**, and the rotation is baked into the pixels instead. A photo of a person carries a capture time, often a GPS position, and a camera model; none of that belongs on a shared server, and the rotation has to be applied to display the photo the right way up regardless.
- **The camera needs no `CAMERA` permission.** A photo is taken by the phone's own camera app through `ACTION_IMAGE_CAPTURE`, which writes the file through our `FileProvider`; declaring `CAMERA` would only make Android demand the permission before the camera app may even be asked. "Take a photo" is hidden entirely on a device with no camera app, rather than opening a chooser that cannot work.
- **The format is decided by the file's magic bytes.** A filename is a label the client controls and a `Content-Type` is a header it also controls; the first bytes are neither. Unrecognised bytes are rejected as 415, and an oversized body as 413, before anything is stored.
- **Stored in the database, not on the server's disk.** A hosted deployment's filesystem is wiped on every deploy and restart, which would silently break every avatar; the database is the part of a deployment that survives. The bytes live in `photos` as BSON binary — byte-identical in and out — and only the URL is kept on the user's profile.
- **The URL is built from the request's own host**, so the same code serves an emulator (`10.0.2.2`), a LAN address and a public https domain with no configuration. Behind a hosting proxy that is what makes the stored URL `https` rather than `http` (see `app.set('trust proxy', 1)`), which matters because Android refuses cleartext to a domain that is not explicitly allow-listed.
- **Deleting is guarded.** Only names matching the app's own generated pattern are ever looked up or deleted, so a bug can never reach anything but the image it meant to.
- **There is no `androidTest`.** The one piece with real logic — turning an arbitrary picked file into a correctly oriented, correctly sized JPEG — is written as a pure function of a `Bitmap` and an EXIF orientation, so it is unit-tested on the JVM (`AvatarImageTest`) without a device.

### Android request flow (Phase 7 — streaks)

```
Home tab
   │  DashboardViewModel → one GET /api/dashboard
   ▼
WeightRepository.dashboard()   (the dashboard call already lived here)
   │  maps DashboardStreakDto → StreakStats
   ▼
HomeScreen · StreakCard(dashboard.streak)
   │  streakStatus(streak)  ← pure function, no I/O
   ▼
NOT_STARTED / AT_RISK / ACTIVE / ENDED  →  the number, the nudge and the record
```

- The streak rides along on the **existing** dashboard call, so the Home tab still makes exactly one request: a new section on an aggregate is cheaper than a new endpoint, and the app needed only a mapper.
- **No new loading, empty or error state was added.** A dashboard that fails still shows the existing error banner, and a brand-new account reaches the card as real zeros — `NOT_STARTED`, not an error — because the server reports honest zeros rather than an absent section.
- The **status decision is a pure function** (`streakStatus`), not a `when` buried in the composable, so the four states are unit-tested without a device. The card's wording lives in `streakMessageRes`, which is trivial by comparison.
- The card sits directly under the training card so the two training-related cards read together, and it deliberately carries **no second "Log workout" button** — the nudge points at the button immediately above it rather than offering a duplicate action.

### Android request flow (Phase 7 — the gym reminder)

The reminder is the one feature that makes **no HTTP request at all**: it is a
local notification, scheduled on the phone and stored on the phone.

```
Profile tab · ReminderCard   (switch · time wheel · day chips · sound)
   │
   ▼
SettingsViewModel   setReminderEnabled / setReminderTime / toggleReminderDay /
   │                setReminderSound / importReminderSound
   │  write, then re-read  (an imported sound is copied into the shared sound library first)
   ▼
SettingsStore (DataStore)  ──┐
   │  reminder_enabled       │  the single source of truth:
   │  reminder_time "18:30"  │  the job is always scheduled from
   │  reminder_days {"1","3","5"}  what was actually persisted
   │  reminder_sound_uri / reminder_sound_name   (absent = the phone's default)
   ▼
GymReminderScheduler.sync(context, reminder)
   │  ONE unique job, replaced on every change
   ▼
WorkManager · OneTimeWorkRequest(delay = millisUntilNextReminder(...))
   │
   │  … later …
   ▼
GymReminderWorker   re-reads the setting → posts the notification → re-arms
   │
   ▼
GymReminderNotifications.show(context, sound)
   │  reminderChannelId(sound)  ← pure: default · silent · a hash of the sound's URI
   ▼
NotificationChannel(id, name, IMPORTANCE_DEFAULT)   with the chosen sound set on it
```

- **Local, not server-side.** The reminder is about *this phone's* notifications, exactly like the theme, so it lives in DataStore. It therefore works with no network and no account, and there is no new endpoint — but it also means the setting does not follow the user to a second device. That is the honest trade, and it is the same one the theme already makes.
- **One-shot work that re-arms itself, not a repeating job.** A repeating job drifts: if the system defers one run by an hour to save battery, every later run is an hour late too. Re-computing the next exact occurrence each time it fires returns the reminder to the chosen time instead of sliding later every day.
- **WorkManager, not an exact alarm.** A gym reminder may legitimately be a few minutes late, and WorkManager survives a reboot by itself — no `BOOT_COMPLETED` receiver to write and no `SCHEDULE_EXACT_ALARM` permission to ask for. The cost is that Android may delay it (see the limitations).
- **`millisUntilNextReminder` is a pure function** taking the wall clock and a `TimeZone`, so "the next Monday at 18:00" is tested exactly (including the ISO-vs-`Calendar` weekday numbering, which is the classic off-by-one here) without waiting for a real alarm or touching a device. `java.time` is deliberately avoided: the app supports API 24, where it would need core-library desugaring.
- **The worker re-reads the setting at fire time.** A job can sit queued for a day, so if the user has since turned the reminder off — or the system ran it so late that the day no longer matches — nothing is posted, and the next occurrence is scheduled anyway so the reminder is never silently lost.
- **The day chips cannot all be turned off.** `toggleReminderDay` refuses to remove the last one, because a reminder with no days could never fire and would leave the user with a switch that does nothing.
- **The time is picked on a wheel, not typed.** Two scrollable columns snap to the middle row, so there is no format to get wrong and no keyboard over the card. The wheel is a `LazyColumn` with `SnapPosition.Center` and a padding row at each end (without them `00` and `59` could not be centred), and the chosen value is read from the **layout** rather than from the scroll offset — asking the layout which row's midpoint is nearest the viewport's is right at the padded ends where offset arithmetic is not. The dialog only commits on **Set**, so scrolling past a value does not save it.
- **A corrupt preferences file degrades instead of crashing.** DataStore's `ReplaceFileCorruptionHandler` swaps an unreadable file for empty preferences, so a damaged file means "theme follows the system, reminder off" rather than an app that cannot start.
- **`POST_NOTIFICATIONS` is requested only when the switch is turned on**, and the card says so plainly if Android is blocking notifications, rather than showing an "on" switch for a notification that will never appear.
- **The sound is played by Android, not by the app**, which decides the whole design. The system reads the sound when it posts the notification, so it must be able to open it — and it cannot open a file this app picked with a document picker. Pointing the reminder at the picked file would have installed a reminder that quietly makes no sound on some devices, so the file is first **copied into the phone's shared sound library** (`ReminderSoundImporter`), which is exactly where every other notification sound already lives. As a side effect the imported sound also becomes selectable in Android's own sound settings, so it is not locked inside RepRise.
- **Android freezes a channel's sound when the channel is created**, so the sound cannot simply be changed on an existing channel — and doing so would also overwrite the volume and importance the user had tuned. Each choice therefore gets **its own channel**: the original `gym_reminder` for the phone's default (re-used deliberately, since the user may already have allowed or tuned it), `gym_reminder_silent`, and `gym_reminder_<hash>` for an imported sound. Channels the user has moved on from are deleted, so Android's notification settings do not fill up with entries named after sounds that are no longer set.
- **The whole choice is one stored string**, and the three cases are told apart without a schema: absent means the phone's default, the word `silent` means silence, and anything else is the URI of the imported copy. An absent value is the default so that installing the app and never touching this setting leaves no trace — and the default path behaves byte-for-byte as it did before the choice existed.
- **`reminderChannelId` is a pure function** of the sound, so the mapping that decides *which* channel carries *which* tone is unit-tested (`ReminderChannelsTest`) — worth testing precisely because a mistake there is invisible until the day of the reminder, when one sound would silently play another's tone.
- **Android 9 and older need storage access** to add a file to the shared library, so `WRITE_EXTERNAL_STORAGE` is capped at API 28 and asked for only at the moment the user reaches for a sound. From Android 10 the copy needs no permission at all.
- **A file that cannot be used says so.** Importing is the one reminder setting that can fail for a reason worth explaining, so the card shows a specific message (too big to be a notification sound, or unreadable) instead of silently leaving the old sound in place.

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
│               │                          # Progression, Friend, Leaderboard, GymReminder,
│               │                          # ReminderSound, enums + the pure rules
│               │                          # (streakStatus, parseStoredReminderSound, …)
│               ├── data/
│               │   ├── remote/            # RepRiseApi, ApiClient, AuthInterceptor,
│               │   │                      # ApiResult + ApiCall (error parsing),
│               │   │                      # RepRiseJson (the one JSON config), dto/
│               │   ├── local/             # TokenStore (encrypted), SettingsStore
│               │   │                      # (theme + gym reminder, both device-local)
│               │   ├── image/             # AvatarImage: decode → scale → rotate → JPEG
│               │   ├── audio/             # ReminderSoundImporter: copy a picked sound
│               │   │                      # into the phone's shared sound library
│               │   └── repository/        # AuthRepository (session source of truth),
│               │                          # WeightRepository (weight + dashboard),
│               │                          # WorkoutRepository (workout logging),
│               │                          # ProgressionRepository (progressive overload),
│               │                          # FriendRepository (friends + requests),
│               │                          # LeaderboardRepository (weekly leaderboard)
│               ├── util/                  # Formatting (ISO dates, kg/lb numbers, volume)
│               ├── notifications/         # GymReminderWorker, GymReminderScheduler,
│               │                          # GymReminderNotifications (channel + posting),
│               │                          # ReminderChannels (sound → channel id, pure)
│               ├── ui/theme/              # Color, Type, Theme, SettingsViewModel
│               ├── ui/auth/               # LoginScreen, RegisterScreen, AuthViewModel
│               ├── ui/home/               # HomeScreen (dashboard) + DashboardViewModel
│               ├── ui/weights/            # WeightsScreen + WeightsViewModel
│               ├── ui/workouts/           # WorkoutsScreen, WorkoutEditorScreen + ViewModels
│               ├── ui/progression/        # ProgressionScreen + ProgressionViewModel
│               ├── ui/friends/            # FriendsScreen + FriendsViewModel
│               ├── ui/leaderboard/        # LeaderboardScreen + LeaderboardViewModel
│               ├── ui/profile/…           # ProfileScreen + the ProfilePhotoCard and the
│               │                          # local ReminderCard / AppearanceCard editors
│               └── components/            # reusable Compose widgets (ScreenContainer,
│                                          # DetailScreen, RepRiseCard, WeightChart,
│                                          # UserAvatar, …)
└── backend/
    ├── package.json
    ├── .env.example                       # placeholders only — never real secrets
    ├── src/
    │   ├── start.js                       # startup: connect DB → ensure indexes → listen
    │   ├── app.js                         # Express app + middleware + routes
    │   ├── config/
    │   │   ├── env.js                     # central env loading & validation
    │   │   └── constants.js               # shared rules (goals, units, ranges, BMI bands)
    │   ├── database/                      # connection, collections, indexes
    │   ├── middleware/                    # ensureDatabase, requestLogger, notFound,
    │   │                                  # errorHandler, authenticate, validate, rateLimiter
    │   ├── routes/                        # index, health, auth, users, weights, dashboard,
    │   │                                  # workouts, progression, friends, leaderboard, photoFiles
    │   ├── controllers/                   # auth, users, weights, workouts, dashboard, progression,
    │   │                                  # friendship, leaderboard
    │   ├── services/                      # auth, user, weight, workout, bmi, dashboard, progression,
    │   │                                  # friendship, leaderboard, streak, photo
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
        ├── leaderboard.test.js
        ├── streaks.test.js
        ├── photos.test.js
        └── ensureDatabase.test.js
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

> **Whose IP do you allow?** The IP in *Network Access* is the address of whatever **connects** to the database. For local development that is your own machine; for a deployed API it is the **host's** servers, not you. A free host has no fixed outbound IP, so a deployment needs `0.0.0.0/0` — see [Deploying the backend](#deploying-the-backend-so-it-works-off-your-network).

> **If Atlas seems unreachable from your own network** (the connection hangs, or fails with `tlsv1 alert internal error`), it is worth knowing that the cause can be the **network you are on**, not the cluster: some ISPs and campus/office networks interfere with port `27017`. A deployed API on a datacenter host is not affected by that, and neither is a phone on mobile data.

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
| `PHOTO_MAX_BYTES` | no | `5242880` | largest accepted profile-picture body, in bytes (5 MB) |

> There is no upload-directory variable: profile pictures are stored in the **`photos` collection** of the database rather than on disk, so they survive a redeploy on a host whose filesystem does not (see [Deploying the backend](#deploying-the-backend-so-it-works-off-your-network)).

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

## Deploying the backend (so it works off your network)

Everything above runs the API on **your own machine**, which means it is only reachable by devices on **your** network: `http://10.0.2.2:4000/` is an emulator alias, and a LAN address like `http://192.168.8.102:4000/` means nothing to a phone on mobile data. To share the app with someone else, the API needs a **public https URL**, and that URL is compiled into the APK.

The database side is always **MongoDB Atlas** (a free M0 cluster). For the API itself this repository supports two free hosts — pick whichever suits you:

| | **Vercel** (recommended) | **Render** |
|---|---|---|
| Credit card / bank details required | **No** | Yes |
| Configuration | automatic — Vercel detects the Express app | `render.yaml` (Blueprint) |
| Free-tier behaviour | does not sleep; an idle instance wakes in well under a second | sleeps after 15 minutes, and waking takes tens of seconds |

### 1. MongoDB Atlas

1. Create a free **M0** cluster at <https://cloud.mongodb.com> (see [MongoDB Atlas setup](#mongodb-atlas-setup)).
2. **Database Access** → create a database user, and put the password in the connection string. If it contains any of `` : / ? # [ ] @ ``, [percent-encode it](https://www.mongodb.com/docs/manual/reference/connection-string/#std-label-connection-string-special-characters) (`@` → `%40`).
3. **Network Access** → allow `0.0.0.0/0`. Neither free host has a static outbound IP, so the usual "allow only my IP" rule cannot be used — and your *own* IP would never be the one connecting anyway, because the requests come from the host's servers, not from your laptop.
   *This is the one place the project trades a little security for practicality: the database is protected by its password and by TLS, but it is not IP-restricted. Nothing secret is ever stored in the app's URLs.*
4. **Connect → Drivers** → copy the string and add the database name, so it ends `/reprise?retryWrites=true&w=majority`.

### 2. Deploy the API

Either way, push this repository to GitHub first — both hosts build from the repository, not from your working copy.

#### Option A — Vercel (no credit card)

1. <https://dashboard.vercel.com> → **Add New… → Project** → import the repository.
2. **Settings → Root Directory** → set it to **`backend`**. The repository also contains the Android project, and Vercel must build only the API. *(Vercel detects the Express app and its Node version by itself; there is no build or start command to fill in.)*
3. **Settings → Environment Variables** → add:
   - **`MONGODB_URI`** — the Atlas string from step 1.
   - **`JWT_SECRET`** — a long random one: `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`
   - **`NODE_ENV`** = `production` — Vercel usually sets this itself, but pinning it makes the behaviour explicit rather than assumed. It is not cosmetic: in production the error handler answers unexpected `500`s with a generic message instead of leaking internal details.
4. **Deploy**, and let the first build finish without an error.
5. Your URL is shown on the deployment page — for this project it is `https://fitness-tracker-eight-delta.vercel.app`. Confirm the API *and* the database are live:
   ```bash
   curl https://fitness-tracker-eight-delta.vercel.app/api/health
   ```
   It must return `"status":"ok"` with `"database":{"connected":true …}`. If Vercel gave your project a different domain, note the real URL — it goes into the app (next step) and into `build.gradle.kts`.

> **How it runs there.** Vercel finds the Express app by looking for a file named `app`, `index` or `server` — at the project root or in `src/` — and `src/app.js` is the only one this project has. (The startup file is deliberately called `src/start.js`, *not* `server.js`: if Vercel could also pick that one, it might run the fail-fast startup inside a function, where a database hiccup exits the process and every request answers Vercel's bare "FUNCTION_INVOCATION_FAILED" instead of this API's JSON.) So the usual "connect, then listen" startup may never run, and `src/middleware/ensureDatabase.js` covers exactly that case: on the first request it opens the connection (once per instance, shared by every concurrent request), so the API connects either way, and an unreachable database is reported as an honest `503 degraded` on `/api/health` rather than as a crash. `app.set('trust proxy', 1)` is what then makes `req.protocol` read as `https`, so photo URLs come out as `https://…` instead of `http://…`.

#### Option B — Render (needs a credit card)

1. <https://dashboard.render.com> → **New → Blueprint** → pick the repository → **Apply**. `render.yaml` supplies the runtime, the start command, the health check and most of the environment, and sets `rootDir: backend` so the Android app is never built.
2. When prompted, fill in the two values the blueprint deliberately leaves blank:
   - **`MONGODB_URI`** — the Atlas string from step 1.
   - **`JWT_SECRET`** — Render generates a long random one for you (leave the generated value).
3. Watch the log. A successful start ends with:
   ```
   INFO  - Connected to MongoDB (db="reprise", host="…")
   INFO  - MongoDB indexes ensured.
   INFO  - RepRise API listening on http://localhost:10000
   ```
   The service is only marked **Live** once `/api/health` answers `200`, and that endpoint reports the database — so a green deploy really means the API *and* the database are working.
4. Your URL is `https://<service-name>.onrender.com`. Confirm it with the same `curl` as above. If the name was already taken, Render appends a suffix — make a note of the real URL, because it goes into the app (next step) and into `render.yaml` and `build.gradle.kts` so the two agree.

### 3. Point the app at it

`android/app/build.gradle.kts` has the deployed URL baked into the **release** build type, which is the one you hand to other people. If your URL differs, change that one line. Then:

```bash
cd android
./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

That APK is signed with the **debug keystore** on purpose (it is sideloaded, not published to the Play Store), and it is the *same* key as the debug build — so it installs **over** an existing debug build as an update instead of colliding with it.

> The URL must match **exactly**. An APK built against `https://fitness-tracker-eight-delta.vercel.app/` reports "can't reach the server" against any other address — including a LAN one — which is the most common reason a shared APK fails on a friend's phone.

### Things worth knowing once it is hosted

- **Vercel does not sleep.** An idle instance is woken by the next request, which costs well under a second, so an app nobody has touched for a day still answers promptly. *(Render's free instance behaves differently: it sleeps after 15 minutes and the next request waits tens of seconds for it to wake, which looks exactly like "can't reach the server" to the app. Keep it awake with a free uptime monitor — UptimeRobot, cron-job.org — pinging `https://<service>.onrender.com/api/health` every 5 minutes, or use a paid instance.)*
- **4.5 MB is the platform's request and response ceiling**, which is below this API's `PHOTO_MAX_BYTES` (5 MB). The app downscales a picture to a 512-pixel JPEG of a few dozen kilobytes, so this never bites in practice — but a hand-crafted upload larger than 4.5 MB is refused by Vercel before the API ever sees it, and the error returned is Vercel's rather than this API's JSON shape.
- **Rate limiting is per instance and in memory.** A serverless host runs several instances and they do not share counters, so the limit is approximate: it blunts a burst from one client, but a determined attacker could spread across instances. Production would use a shared store such as Redis.
- **Photo bytes live in the database** (`photos`), not on the server's disk, because a hosted filesystem is wiped on every deploy and restart. That is why this project stores them there.
- **Everyone must use the same base URL.** A photo URL is absolute and built from the request's own host, so a picture uploaded through one address is only reachable through that address. If two people use two different URLs (one LAN, one public), one of them sees a broken avatar.
- **`ENABLE_HTTP_LOGS=true`** is worth keeping on the host: the host's log view is where you watch a friend's registration actually arrive.
- **Local development is unaffected.** Your `.env` keeps pointing at local `mongod`, and debug builds keep pointing at `10.0.2.2`. The deployed instance uses Atlas, so it has its own separate data.

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
| **Any phone, any network** | `https://fitness-tracker-eight-delta.vercel.app/` | the deployed API — the **release** build type, and the only one that works away from your own network |

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
- A `username` that is already taken returns `409`. The app does not expose username editing yet, and the profile image has its own upload route instead of being written as a field — see **Profile picture (shipped)** below.
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
                     -> 200 { weight:{...}, workouts:{...}, streak:{...}, bmi, bmiUnavailableReason }
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

**Profile picture (shipped).** The picture is the one profile field that is not JSON, so it has its own two routes and travels as raw bytes in both directions. It is stored server-side rather than on the phone, because it is part of the social features: friends and the leaderboard have to be able to see it.

```
PUT    /api/users/me/photo    raw image body  ->  200 { user }   (upload or replace)
DELETE /api/users/me/photo                     ->  200 { user }   (remove)
GET    /uploads/<name>                         ->  200 image/…    (public, see below)
```

- The body is parsed with `express.raw({ type: PHOTO_CONTENT_TYPES, limit })` — **only** `image/jpeg`, `image/png` and `image/webp` — and the limit is enforced by the parser, so an oversized upload is refused with `413` **before anything is stored**. Authentication runs first, so an anonymous request cannot make the server buffer anything.
- The format is decided by the file's **magic bytes**, never by the filename or the `Content-Type`: a client controls both of those, and the bytes are what will actually be served back. Anything else is `415` (`unsupportedMediaType`), and a `PUT` with no body is `415` too.
- `DELETE` returns **`200` with the updated user**, not `204`, so the app can render the empty avatar from the same reply it already knows how to apply — and deleting when there is no photo is a harmless success rather than a `404`.
- The picture is stored in the **`photos` collection** under the name `<24-hex userId>-<16-hex random>.<jpg|png|webp>` — the `_id` **is** that name — and the URL is built from the **request's own host**, so the same deployment works from an emulator, a LAN address or a real domain with no configuration. Replacing a photo deletes the document it replaced; the delete path only ever touches names matching the app's own generated pattern, so a bug cannot remove anything else.
- `GET /uploads/…` is **unauthenticated, by design**: it is an `<img src>` URL, which cannot carry a bearer token, and the random name is what makes it unguessable. It is served with `immutable` and a one-week `max-age` (a picture never changes — a replacement gets a new name), and with `Cross-Origin-Resource-Policy: cross-origin` so that a web client on another origin can still display it. A name that is not one of ours, or one that no longer exists, is a plain `404`.

**Streaks (shipped).** `GET /api/dashboard` gained a `streak` section, exactly as the phases before it added `weight`, `workouts` and `bmi` — so the Home card appeared without the app needing any rewiring beyond mapping the new section.

```
GET /api/dashboard
    -> 200 { weight:{…}, workouts:{…}, streak:{…}, bmi, bmiUnavailableReason }
```

```json
"streak": {
  "current": 5,          // consecutive days trained, ending today or yesterday
  "longest": 12,         // the best run this user has ever had
  "trainedToday": false, // false => the streak is still alive but needs today
  "activeDays": 34       // total distinct days with at least one workout
}
```

The rules, and the reasoning behind them:

- **A streak is counted in days, not sessions.** Two workouts on one day are one active day, and a day counts if it holds at least one workout of any size. The feature rewards turning up, which is the habit a beginner needs, not volume.
- **Days are UTC calendar days**, matching the leaderboard's Monday→Monday window, so the two features never disagree about which day a workout belongs to. `$dateToString` in UTC does the grouping inside a single aggregation, so the whole streak is computed in one round trip rather than by fetching every workout into Node.
- **Today is not required.** A streak that ended yesterday still shows as *current* until the day is out; the card says so instead (`trainedToday: false`) rather than pretending the run is over. That distinction is what makes a nudge honest — nagging somebody whose streak is genuinely broken is a dark pattern, and being wrong the other way ("your streak is over") is simply a lie.
- **`longest` outlives the streak.** Breaking a run does not erase the record, so there is always something to beat.
- **The arithmetic is a pure function** (`currentStreak`, `longestStreak`, `utcDayNumber`) exported from `streak.service.js`, which is why it can be unit-tested without a database — the aggregation that feeds it is tested end to end against real documents.

The app turns that payload into one of four states via another pure function, `streakStatus`:

| `current` | `trainedToday` | State | What the card says |
|---|---|---|---|
| `0`, never trained | — | `NOT_STARTED` | invite to log the first workout |
| `0`, but has trained | — | `ENDED` | the run is over; start a new one |
| `> 0` | `false` | `AT_RISK` | train today to keep it alive |
| `> 0` | `true` | `ACTIVE` | done for today; come back tomorrow |

> `StreakStatusTest` pins all four, including the two that are easy to get wrong: never having trained is *not* a broken streak, and a zero after training is not "at risk" — it is over.

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

### `photos`
Uploaded profile pictures, **one document per photo** — the bytes themselves, not a path.
`_id` (the file name, `<24-hex userId>-<16-hex random>.<jpg|png|webp>`), `userId`, `contentType`, `size`, `data` (BSON binary), `createdAt`.
*The `_id` **is** the file name: the name is unique by construction, so MongoDB's built-in unique index on `_id` both prevents any collision and makes serving a photo a single indexed point lookup. `contentType` is what the server sniffed at upload time (never what the client claimed), and `data` is stored as binary so the bytes come back exactly as they went in. Keeping the bytes in the database rather than on disk is what lets the API run on a host whose filesystem is wiped on every deploy.*

### Indexes
| Collection | Index | Purpose |
|---|---|---|
| `users` | `usernameNormalized` **unique** | usernames are unique, case-insensitively |
| `users` | `emailNormalized` **unique** | emails are unique, case-insensitively |
| `weight_entries` | `{ userId: 1, measuredAt: -1 }` | "my weight history, newest first" |
| `workouts` | `{ userId: 1, workoutDate: -1 }` | "my workout history, newest first" |
| `workouts` | `{ userId: 1, 'exercises.name': 1, workoutDate: -1 }` (`user_exercise_recent`) | one exercise's history for the progressive-overload comparison (Phase 5) — used by both progression endpoints |
| `photos` | `_id` (built in) | serving `GET /uploads/<name>` — one point lookup per picture |
| `photos` | `{ userId: 1 }` (`user_photos`) | sweeping **one user's** pictures rather than one at a time (account deletion, and the test suite's cleanup) |
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

Uses Node's built-in test runner (`node:test`) and built-in `fetch` — no extra test dependencies. **147 tests** currently pass.

- **Phase 1** — unknown-route `404` handling, and the health check in both the *degraded* and *ok* states.
- **Phase 2** — registration (incl. duplicate username/email → `409`, case-insensitive email), login (correct / wrong password / unknown user), JWT protection (`401` for missing or malformed tokens), profile updates, and username search (excludes the caller, leaks no private fields).
- **Phase 3** — weight schemas (per-unit range, future/back-dated timestamps, empty update bodies), `401` on every weight route, create/list/latest/read/update/delete, unit conversion after a preference change, ownership isolation (another user gets `404` and the owner's entry is untouched), BMI from height + latest weight, `bmiUnavailableReason`, and the 30-day change.
- **Phase 4** — workout schemas (nested exercises/sets, plausible loads, future dates, clearing a duration/note with an explicit `null`, empty update bodies), `401` on every workout route, create with a derived `summary`, the per-unit load check naming the exact set, the distinct-exercise catalogue, list/read/update (including replacing the exercise list)/delete, ownership isolation, and the dashboard's rolling training totals.
- **Phase 5** — the rule-based verdict in isolation (a heavier top set, more volume, more reps at equal volume, a wobble *inside* the tolerance reading as `holding`, a real drop reading as `regressing`, and one-or-zero sessions reporting `insufficient_data`), the suggestion text for kg and lb accounts, the query schema, `401` on both progression routes, and — end to end — an empty-but-valid trend for an unknown exercise, `progressing` from two real sessions, `regressing` from a lighter session, the exercise list's counts, and that one user cannot read another's progression.
- **Phase 6a** — the canonical pair key is order-independent, the request/param schemas, `401` on **every** friends route, and — end to end — a request visible to both sides, exactly **one** row per pair (a repeat, and the reverse direction while pending, are both `409` and add nothing), accepting making both users friends, only the recipient being able to accept (the sender and a stranger both get `404`), an already-answered request being `409`, decline and withdraw both removing the row, neither party being able to touch a request they are not part of, either friend being able to remove the friendship (and a second removal being `404`), re-requesting a friend being `409`, and friends being listed alphabetically.
- **Phase 6b** — the scoring rule in isolation (a day with nothing logged scores `0`, `10` per active day, and the `+5` consistency bonus landing **exactly** at three days and not before) and the week boundary (a Wednesday, a Monday and a Sunday night each resolving to the correct Monday `00:00` UTC). Then, end to end: `401` without a token, a friendless user seeing **only themselves on zero**, the board being **me and my friends and nobody else**, a second workout on the same day **not** scoring twice while both still count in `workoutCount`, last week's session not counting at all, a trainer ranking above an idle friend, and two equal scores **sharing a rank**.
- **Phase 7 (profile)** — on top of the existing "set and validate" coverage: an explicit `heightCm: null` **clears** the stored height (asserted in the response *and* in the database) while everything else is left untouched, which is the semantic the app's height editor depends on.
- **Phase 7 (streaks)** — the day arithmetic in isolation (`utcDayNumber` counting whole days, and `dayNumberFromKey` reading a `$dateToString` key back) and the two streak helpers over hand-built day lists: a run counts consecutive days, a **gap breaks it**, a duplicate day counts once, and `longestStreak` finds the best run anywhere in the history rather than only the run at the end. Then end to end against real documents: an empty account reporting honest zeros, a three-day run reading `current: 3`, a second session on the same day **not** extending it (`activeDays` unchanged), an older, longer run still reported as `longest` after a break, and deleting the recent days dropping `current` to `0` while `longest` **survives**.
- **Phase 7 (profile picture)** — the format sniffer in isolation (`pngBytes`/`jpegBytes`/`webpBytes` are each identified, while plain text, an **empty** buffer and a RIFF container that is not a WebP are all `null`), the name guard (`storedFileName` accepts only the app's own generated names and refuses a `null`/empty value, a foreign URL, the right directory with a name it would never write, path traversal both raw and percent-encoded, an unsupported extension and a malformed id), and `deletePhoto` reporting `false` for anything not ours. Then end to end over the real routes: an anonymous upload refused, no body / a non-image / a **JSON** body all `415`, a body over the limit `413`, an upload stored **and served back byte-identical** with the right `Content-Type` and reflected on the profile, the stored extension coming from the **bytes rather than the `Content-Type`** (a JPEG uploaded as `image/png` is stored `.jpg`), WebP accepted, replacing a photo deleting the one it replaced with nothing left behind, another user able to see the URL through search, a **URL that was never stored** answering `404`, a name that is not one of ours (including a traversal attempt) being refused **before any lookup reaches the database**, and deleting clearing both the profile and the stored bytes — a second delete being harmless, and a removal leaving a URL some *other* user set alone and touching no stored photo.
- **Deployment** — `middleware/ensureDatabase.js` (the connection a serverless host relies on instead of `src/start.js`'s startup) stays off during tests: it passes a request straight through, calls `next()` exactly once and synchronously, and provably opens no connection of its own — otherwise every test that deliberately runs without MongoDB would first wait ten seconds for a connection that can never succeed. The shared connection attempt itself was verified by hand against both a live and a refused database: one attempt for five concurrent requests, `200` + `database.connected: true` when healthy, and `503 degraded` rather than a hang or a crash when not.

> **Tests never touch your real data.** `tests/helpers/setup.js` points the suite at a separate **`reprise_test`** database on the same cluster. Every test user is deleted before and after the run **along with everything that points at them** (`deleteUserDataByPrefix` — weight entries, workouts, **photos** and friendships), so no orphaned documents accumulate. Tests that need the database are **skipped** (not failed) when MongoDB is unreachable, so the pure-validation tests run anywhere.

**Android**

```bash
cd android
./gradlew :app:testDebugUnitTest          # JVM unit tests (no device needed)
```

There are **no instrumented tests**: every rule worth testing is a pure function or
a repository mapping, both of which run on the JVM in seconds. The parts that
genuinely need a device — the camera, the document picker, the wheels' scrolling
and the notification itself — are exercised by hand (see the demo procedure).

The JVM suite (**175 tests**) covers the parts where a mistake is invisible until
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
  and that a rejection passes through with the server's message intact. It also
  covers the dashboard's **streak** section — and that a dashboard *without* one
  reads as never having trained (`NOT_STARTED`) rather than as a `0`-day streak
  that ended, which is the difference between an invite and an accusation.
- **`StreakStatusTest`** — the four states the streak card can be in, derived by a
  pure function: never having trained is **not** a broken streak, a run is *at
  risk* while today is still open, training today makes it active, and a current
  streak of `0` **after** training means the run really is over.
- **`GymReminderTest`** — the reminder's scheduling arithmetic, with a fixed clock
  and time zone so every answer is exact: a time later today, a time that has
  already passed waiting for tomorrow, skipping to the chosen weekday (including
  Sunday-from-Monday, which a search that gave up after a few days would miss), a
  job firing *exactly* on the minute scheduling the next day rather than
  re-firing, an empty day set never firing, and "18:00" meaning 18:00 on the
  phone's clock in a non-UTC zone. It also pins the ISO-vs-`Calendar` weekday
  translation in both directions, and that the **last** chosen day cannot be
  un-chosen.
- **`ReminderWheelTest`** — the centring arithmetic behind the reminder's time
  wheel: the first value (`00`) and the last ones (`23`, `59`) can all reach the
  middle row, a value in between reads back unchanged, a flick past the halfway
  point reads whichever row is nearer, and an un-laid-out wheel (or a middle row
  that is still padding) reports **nothing** rather than guessing a number.
- **`ReminderSoundTest`** — how the three sound choices are stored as one string,
  which is worth pinning because "no value" and "silence" are one mistake apart:
  the phone's default stores **nothing**, silence stores the keyword rather than
  an empty value (an empty one would read back as the default), an imported sound
  keeps its URI and name while a blank name is dropped, and every choice survives
  a round trip. Reading back gives the default for an absent, empty or
  whitespace-only value, recognises the silence keyword even with a stale name
  beside it, and treats anything else as an imported sound — including storage
  written before the name was kept.
- **`ReminderChannelsTest`** — the mapping from a sound to the channel that will
  carry it, which is invisible until the day of the reminder: the default keeps
  the channel that already existed, silence and each imported sound get their own
  (two different sounds must never share one, because a channel's tone is frozen
  at creation, while the same URI always maps to the same channel), an imported
  channel's id is rediscoverable and hex-only, the prefix test recognises the
  app's channels and leaves a foreign one alone, and the three choices are named
  differently in Android's settings.
- **`AvatarImageTest`** — turning a picked photo into the JPEG that is uploaded:
  a landscape, portrait or square photo is capped **on its longest edge** with the
  shape kept (the edge landing exactly on the limit, never above it), a photo that
  is already small enough is left untouched rather than being enlarged, a
  nonsensical size never produces a zero-pixel image, the bounds-only decode is
  shrunk by powers of two without going below the target, every EXIF orientation
  maps to the rotation that makes the photo upright (an unknown one being left
  alone), and the upload body is the JPEG itself, labelled as a JPEG. The scale and
  rotation functions take a `Bitmap` and an orientation, so none of this needs a
  device.
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
| `MongooseServerSelectionError`-style timeout | your network blocks port 27017 | allow it, or use a different network — a deployed API is not affected |
| Android: `SDK location not found` | `local.properties` missing | create it with `sdk.dir=/path/to/Android/sdk`, or open in Android Studio |
| Android: `Cleartext HTTP traffic not permitted` | plain-HTTP URL not allow-listed | add your host to `res/xml/network_security_config.xml`, or use the deployed `https` API |
| Android: cannot reach the backend (your own device) | wrong base URL | emulator → `10.0.2.2`; device on the same Wi-Fi → your LAN IP, or `adb reverse tcp:4000 tcp:4000` |
| **A friend gets "can't reach the server"** | the APK points at a **LAN** address, which means nothing on his network | give him the **release** APK, built against the deployed `https` API — see [Deploying the backend](#deploying-the-backend-so-it-works-off-your-network) |
| Deployed API: `Failed to connect to MongoDB` in the host's log | Atlas has not allow-listed the host, or the URI/password is wrong | Atlas → Network Access → `0.0.0.0/0` (neither free host has a fixed IP), and percent-encode special characters in the password |
| Deployed API: every route answers `404` (Vercel shows "404: NOT_FOUND") | the project's **Root Directory** is not `backend`, so no Express app was found to deploy | Vercel → Settings → Root Directory → `backend`, then redeploy |
| Deployed API: Vercel returns `500 FUNCTION_INVOCATION_FAILED` on **every** route, even a wrong one | the function crashed before it could route anything, which is almost always an environment variable — most often `MONGODB_URI` still holding the **local** `mongodb://127.0.0.1:27017` value, so the API cannot reach a database (and Atlas is not allow-listed) | copy the **Atlas** `mongodb+srv://…/reprise…` string into the host's env vars, confirm `JWT_SECRET` is set, check Atlas → Network Access → `0.0.0.0/0`, then **redeploy** — env vars only take effect on a new deployment |
| Deployed API: you changed an env var and nothing changed | variables are injected at deploy time, not read live | redeploy (Vercel: Deployments → ⋯ → Redeploy) |
| Deployed API is slow on the first request | a cold start, or a sleeping Render free instance | expected and brief on Vercel; on Render keep it awake with an uptime monitor, or use a paid instance |
| `gradlew: Permission denied` | lost the executable bit | `chmod +x android/gradlew` |
| Android: `Failed to find Platform SDK ... android-37` | SDK Platform 37 not installed | Android Studio → SDK Manager → install **Android SDK Platform 37 (Android 17)**, or `sdkmanager "platforms;android-37.2"` |
| Android: *"requires ... compile against version 37 or later"* | `compileSdk` too low for the current AndroidX libraries | keep the `compileSdk { version = release(37) { minorApiLevel = 2 } }` block as-is |
| Android: `Failed to apply plugin 'org.jetbrains.kotlin.android'` | AGP 9 has built-in Kotlin | do **not** apply that plugin; see the note in [Prerequisites](#prerequisites) |

---

## Screenshots

> _Add screenshots of the finished app here before the demonstration. Suggested shots: dark-mode Home, weight history chart, an active workout, the progressive-overload comparison, the Friends tab, and the weekly leaderboard._

---

## Professor demonstration procedure

The full sequence below is the target for Phase 7. **Currently steps 1–12 are runnable, in the app itself** — including the profile picture and the reminder's sound.

1. Start the backend and show `GET /api/health` returning `database.connected: true`. ✅
2. Register a new user in the app → open Atlas → show the `users` document with `passwordHash` (**no** plain-text password). ✅
3. You are signed in automatically: rename yourself in Profile and switch **Appearance** between System / Light / Dark. ✅
4. Show the errors the app surfaces (wrong password, duplicate username, weak password), sign out, then sign back in — close and reopen the app to prove the session is remembered. ✅
5. Log a body weight from the **Weight** screen (Home → *Log weight*) → show the matching `weight_entries` document in Atlas. ✅
6. In **Profile**, switch the preferred unit to pounds → reload → the **same stored entry is displayed in lb**, proving conversion happens on read. Add a **height** in the same tab to show **BMI** calculated from it plus the latest weight, with its WHO category and disclaimer — then **clear the height** and show the BMI card honestly explaining what is missing. Change the **training goal** while you are there and point out that each card confirms its own save. Finally, set the **profile picture** — take a photo or pick one from the gallery — and show that the avatar appears on the profile, in the **Friends** list and on the **leaderboard**. Point out that the picture is stored **in the `photos` collection** (the `_id` is the user id plus random hex, *not* the original filename), the `PUT /api/users/me/photo` request in the server log, and that the picture served back is the app's own 512-pixel JPEG with the EXIF data stripped — the original photo is never uploaded as-is. ✅
7. Create a workout from the **Workouts** tab (*Log workout*): name it, add an exercise with several sets, and save → show the nested `workouts` document in Atlas, and the derived `summary` on the card. Edit it, then delete it. ✅
8. Log a **second** session with the same exercise, then open **Progress** (Workouts → *Progress*), tap the exercise and show the **progressive-overload comparison**: the latest-vs-previous numbers, the verdict, and the rule-based suggestion. Log a lighter session and show the verdict flip to *Regressing*. ✅
9. Register a second test account, then on the first account search its username in the **Friends** tab (*Find by username*), send a request, switch accounts, and accept it → show the `friendships` document with its `pairKey` and `status: "accepted"`, and the `uniq_friendship_pair` index. Remove the friendship to show the row disappear. ✅
10. Open **Friends → *Weekly leaderboard*** and show the week: the Monday→Monday UTC window, each friend's rank and points, the `+5` consistency bonus appearing once someone reaches three active days, and your own row marked. Explain that the score is a **live aggregation over `workouts`** — nothing is stored. ✅
11. Back on **Home**, show the **Streak** card: the current run in days, the personal record, and the state it is in — *at risk* before you train today, *active* once you have. Log a workout and watch it flip. Explain that it is computed from the same `workouts` collection with one UTC-day aggregation, that two sessions in a day count once, and that `longest` survives a broken run. ✅
12. In **Profile**, turn on the **Gym reminder**, **scroll the wheels** to a time a couple of minutes away and pick the days, and accept the notification permission. Then tap **Change sound** and either **choose a file from the phone** or set it to *No sound*. Explain why a chosen file is copied into the phone's **shared sound library** first — Android plays a notification's sound, not the app, and it cannot read a file this app picked — and that Android freezes a channel's sound the moment the channel is created, so each sound is posted on its own channel (which is also why the imported sound shows up in Android's own sound settings). Explain that this one is deliberately **local** — it is stored in DataStore on the phone, not on the server, so it needs no network and there is no endpoint for it — and that it is scheduled with WorkManager as a one-shot job that re-arms itself for the next occurrence, so it cannot drift the way a fixed daily repeat would. Let it fire so the chosen sound is heard. ✅
13. Explain the architecture, REST API, schema, authentication and scoring rules.

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
| 7 | Streaks, settings, polish, demo prep | 🟡 **in progress** — in-app profile editing, the **profile picture**, **activity streaks**, the **gym-reminder notification** and its **sound** shipped; privacy setting, screenshots and demo polish still to come |

---

## Known limitations & future improvements

- **Phases 1–6b complete, Phase 7 in progress** — authentication, profiles, body-weight tracking, BMI, the dashboard, workout logging, the progressive-overload assistant, friends, the weekly leaderboard, in-app profile editing (name, goal, unit, height), the profile picture, activity streaks and the gym-reminder notification (with a choosable sound) all work end to end.
- The **gym reminder is stored on the phone, not the account**, so it does not follow the user to a second device, and it is scheduled with WorkManager rather than an exact alarm — which means Android may deliver it a few minutes late (or, if the phone is in deep doze, later than that). That is a deliberate trade for needing no `SCHEDULE_EXACT_ALARM` permission and for surviving reboots for free; a reminder is not an alarm clock. The reminder also depends on the app not being **force-stopped**: a force-stop cancels pending work on many devices, so the app re-arms the job on every launch. The time is read on the phone's own clock, so travel changes when it fires.
- The reminder's **time is set on a two-wheel picker**, the way an alarm clock's is — hours and minutes scroll and snap to the middle row, so nothing has to be typed and no invalid time can be entered. Because the wheel only reports whichever row is centred, it is also the one part of the reminder the JVM tests can only cover through its arithmetic (`ReminderWheelTest`); the scrolling itself needs a device.
- **Preferences are replaced, not repaired, if the DataStore file is corrupted.** A `ReplaceFileCorruptionHandler` swaps a file that cannot be read for empty preferences, so the app degrades to "theme follows the system, reminder off" instead of crashing on launch. That is the recoverable failure, but it does silently lose the settings, so it is a safety net rather than a fix.
- The **username** is the only profile field still read-only in the app: the API accepts it on `PATCH /api/users/me` (normalised and uniqueness-checked, with a clash as a `409`), but there is no UI for it yet.
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
- **Profile pictures are stored in MongoDB** (the `photos` collection, BSON binary), not on the server's disk and not in object storage. That is the honest choice for a project that must also run on a free host: the database already exists, it survives a redeploy, and a hosted filesystem does not (a file written to disk disappears on the next deploy or restart, which would silently break every avatar). The trade-off is that the bytes count against the database's storage quota — an M0 cluster has 512 MB — and that a picture is fetched with a database round-trip rather than by a static file handler; at the app's 512-pixel/JPEG size (tens of kilobytes each) that is thousands of pictures before either matters. Object storage (S3 and the like) is what a production system would use instead. The API does **not** re-check or re-encode the image beyond sniffing its format, so it trusts the app's downscaling; a hostile client could upload a large-but-valid image right up to `PHOTO_MAX_BYTES`. There is **no moderation** — a picture is shown to friends and on the leaderboard as soon as it is uploaded — and `GET /uploads/…` is deliberately unauthenticated (see the endpoints section).
- The **gym reminder's sound lives on the phone too**, like the rest of the reminder. A sound chosen from the user's own files is **copied into the phone's shared sound library** so that Android can play it — which also means it stays there (and appears in Android's own notification-sound list) even if the user later changes the reminder or uninstalls the app. RepRise does not delete it, because the user may have set it as a device-wide sound; the copies are small (capped at 5 MB each).
- The reminder's **sound** is chosen from the phone's files, not uploaded to the account, so it too does not follow the user to a second device — the same trade the reminder's time and days already make.
- The Friends screen and the leaderboard show each person's **photo** (via Coil), falling back to their **initial** when they have none — the same avatar the profile shows. Friend requests still carry **no message**, and the friends list is capped at the server's `FRIEND_LIST_MAX` (500).
- Optional future work: **push** notifications (the gym reminder is local-only, so nothing is sent from a server), friend workout sharing, data export, image moderation, moving photos from the database to object storage.
- **On a free host the API is not always already running.** Vercel wakes an idle instance in well under a second, so this is invisible in practice; Render's free instance *sleeps* after 15 minutes and the first request afterwards waits tens of seconds, which the app reports as an unreachable server. Vercel's serverless model brings two more honest caveats: the platform caps a request or response at **4.5 MB** (above this API's own 5 MB photo limit, and far above the few dozen kilobytes the app actually sends), and the rate limiter keeps its counters **in memory per instance**, so its limit is approximate rather than a hard global one. See [Deploying the backend](#deploying-the-backend-so-it-works-off-your-network).
- **The deployed API and a local development setup are two separate worlds**: the deployed instance uses Atlas, your machine uses local `mongod`, and each keeps its own data. A photo URL is absolute and built from the host that received the upload, so mixing the two (one user on the LAN address, another on the public URL) results in a broken avatar for one of them. Everyone must use the same base URL.
