# Porting Pawgress to Kotlin Multiplatform

This is a fresh project (separate from the original `LiftLog` Android repo)
that reuses as much of that app's logic as possible while adding a real iOS
target via Kotlin Multiplatform (KMP) + Compose Multiplatform. Nothing here
touches the original repo, and the original Android app keeps working
untouched.

## Layout

- `shared/` — the KMP module. `commonMain` holds code that runs identically
  on Android and iOS; `androidMain`/`iosMain` hold platform-specific pieces
  (currently both empty placeholders — nothing has needed them yet).
- `androidApp/` — a thin Android application module. Currently just a
  placeholder `MainActivity` that lists the coach roster from `shared/`, to
  prove the wiring works end to end.
- `iosApp/` — Swift/Xcode side. **The `.xcodeproj` itself is not checked in
  yet** — see `iosApp/README.md` for the one-time manual setup (Xcode
  project files are too fragile to hand-write outside Xcode). The Swift
  source that goes into it once created is ready to go in
  `iosApp/PawgressSwift/`.

## Phase 0 — Infrastructure (done)

- Gradle multiplatform project scaffolded (`settings.gradle.kts`, root
  `build.gradle.kts`, `shared/build.gradle.kts` with `androidTarget()` +
  `iosX64()`/`iosArm64()`/`iosSimulatorArm64()` targets).
- Reused the same Gradle wrapper version, AGP version (8.13.2), and Kotlin
  version (2.0.21) as the original LiftLog project, since those are already
  known to work in Android Studio here.

## Phase 1 — Pure data/logic layer (done)

Ported with no behavior changes, only package renames
(`com.balandman.liftlog.*` → `com.balandman.pawgress.*`):

- `data/MachineCatalog.kt` — copied verbatim.
- `coach/CoachCatalog.kt`, `coach/CoachOutfitQuotes.kt` (all 180 catchphrases,
  20 base + 160 per-outfit), `coach/CoachVoice.kt` — copied verbatim into a
  new `coach` package (previously part of the Android `ui` package, even
  though they're pure text/logic with no UI code).

Rewritten, because `java.time.*` (used throughout the original app) is
JVM-only and doesn't exist on Kotlin/Native (iOS):

- `data/Models.kt` — `java.time.LocalDate` → `kotlinx.datetime.LocalDate`.
  `java.time.MonthDay` has no multiplatform equivalent, so there's now a
  small hand-rolled `MonthDay(month, day)` data class used only for
  `CoachTheme`'s yearly windows. `outfitKey()` moved here from
  `CoachCatalog.kt` (it's a `data/` concept, and previously lived oddly next
  to the catalog rather than the model it keys).
- `data/GymDay.kt` — same date-API swap; logic unchanged.
- `data/DateCompat.kt` — **new file**. Adds `.plusDays()`/`.minusDays()`
  extensions on `kotlinx.datetime.LocalDate` so files ported later that
  called those (originally free on `java.time.LocalDate`) don't need to
  change their call sites, only their imports.
- `coach/MotivationCatalog.kt` — same date-API swap; logic unchanged.

**Not yet checked**: this hasn't been built with Gradle anywhere (Maven/
Google's servers are blocked from this session's shell access to your Mac,
same as they always have been for the Android project). The `kotlinx-datetime`
API calls here are written from documentation, not verified by a compiler.
**The first thing to do when you open this in Android Studio is let Gradle
sync and fix whatever it flags** — expect maybe a handful of small API-name
issues in `GymDay.kt`/`DateCompat.kt`, nothing structural.

## Phase 2 — Persistence (not started)

`LiftRepository.kt` (808 lines) is next. It currently uses:
- `android.content.Context` for the app's files directory,
- `org.json.JSONObject`/`JSONArray` (Android-bundled, not available on iOS),
- `java.io.File` for atomic tmp-file-rename writes.

Plan: define an `expect`/`actual` file-access abstraction in `shared/`
(`androidMain` implements it via `Context.filesDir`, `iosMain` via
`NSFileManager`), and replace `org.json` with `kotlinx.serialization` (fully
multiplatform, and arguably nicer than hand-rolled JSON parsing anyway). The
actual repository logic (profile switching, mutation functions, pawprint
math) is otherwise plain Kotlin and should port with minimal changes once
those two swaps are in place.

## Phase 3 — Sync / networking (not started)

`sync/SheetsApi.kt`, `sync/SyncManager.kt` use raw `OkHttp` (JVM/Android
only). Plan: swap to **Ktor Client** (official Kotlin Multiplatform HTTP
client — Darwin engine on iOS, OkHttp engine on Android, so Android's runtime
behavior barely changes). `sync/SheetsApi.kt` also uses
`java.time.format.DateTimeFormatter` for date/time formatting — that needs a
manual multiplatform-friendly formatter (kotlinx-datetime doesn't have a
drop-in `DateTimeFormatter` equivalent at the version pinned here).

## Phase 4 — Auth (not started)

`sync/GoogleAuth.kt`, `sync/AccountPicker.kt` use Android's
`play-services-auth` and system account picker — entirely Android-specific.
iOS needs its own path: either Google's iOS Sign-In SDK, or a generic OAuth
flow via `ASWebAuthenticationSession`. This will likely become an
`expect`/`actual` "give me an access token" interface in `shared/`, with
completely separate platform implementations behind it.

## Phase 5 — UI (not started, the biggest remaining chunk)

All of `ui/*.kt` (MainScreen, CoachScreen, SettingsScreen, TrendsScreen,
FunFactsScreen, LogSheet, MainViewModel, the art/icon lookup catalogs) needs
to move into `shared/commonMain` using **Compose Multiplatform** instead of
plain Jetpack Compose. Most of the Foundation/Material3 usage in these files
carries over close to unchanged. Two things don't:

- **Asset lookup.** `MascotCatalog.kt` and `CoachOutfitArt.kt` find drawables
  by reflecting over the generated `R.drawable` class by name pattern —
  deliberately, so new art needs zero code changes. Compose Multiplatform's
  resource system generates a typed `Res.drawable` accessor per target
  instead, which doesn't support that same reflection trick. This needs a
  redesigned (still zero-config, ideally) lookup approach.
- **Date formatting.** `MainScreen.kt`, `LogSheet.kt`, `SettingsScreen.kt`,
  `TrendsScreen.kt` all use `java.time.format.DateTimeFormatter` — needs
  hand-written multiplatform formatting (kotlinx-datetime's formatting API,
  or manual string building for the handful of patterns actually used:
  `"EEEE, MMM d"`, `"h:mm a"`, `"MMM d"`, `"EEE"`, `"MMM"`).

## Phase 6 — iOS app wiring (not started)

Create the actual Xcode project per `iosApp/README.md`, wire up the shared
framework, and get the smoke-test screen running in Simulator, then on a
real iPhone via Xcode (free with any Apple ID, but the provisioning profile
needs re-signing every 7 days without a paid Apple Developer Program
membership — $99/year unlocks TestFlight and removes that limit).

## Phase 7 — Distribution (only if wanted)

TestFlight beta, and/or App Store submission. Needs the paid Apple Developer
Program membership from Phase 6.

---

**Current state**: Phases 0–1 done. Everything else is untouched Android-only
code still living only in the original `LiftLog` repo, to be ported
phase-by-phase in future sessions.
