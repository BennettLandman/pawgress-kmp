# Porting Pawgress to Kotlin Multiplatform

This is a fresh project (separate from the original `LiftLog` Android repo)
that reuses as much of that app's logic as possible while adding a real iOS
target via Kotlin Multiplatform (KMP) + Compose Multiplatform. Nothing here
touches the original repo, and the original Android app keeps working
untouched.

## Layout

- `shared/` — the KMP module. `commonMain` holds code that runs identically
  on Android and iOS; `androidMain`/`iosMain` hold platform-specific pieces
  (now used for the first time in Phase 2 — see below).
- `androidApp/` — a thin Android application module. Currently just a
  placeholder `MainActivity` that lists the coach roster and loads the
  persisted profile from `shared/`, to prove the wiring works end to end.
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

## Phase 2 — Persistence (done)

`LiftRepository.kt` (now `shared/commonMain/.../data/LiftRepository.kt`,
ported at ~820 lines) replaced its three Android/JVM-only dependencies:

- **File access.** `android.content.Context` + `java.io.File` became a small
  `AppFileStorage` interface (`read(): String?`, `writeAtomic(content: String)`)
  in `commonMain`, with two implementations: `AndroidAppFileStorage`
  (`androidMain`, backed by `Context.filesDir`, same atomic tmp-rename write
  as before) and `IosAppFileStorage` (`iosMain`, backed by `NSFileManager`'s
  Documents directory). This is a plain interface + platform classes rather
  than `expect`/`actual` (the originally planned approach) — each app target
  just constructs the right one and passes it into `LiftRepository`'s
  constructor at startup (see `MainActivity.kt` / `ContentView.swift`). Same
  end result, less machinery.
- **JSON.** `org.json.JSONObject`/`JSONArray` (Android-bundled, not available
  on iOS) became `kotlinx.serialization` with a small set of private `@Serializable`
  DTOs (`ProfileDto`, `MachineDto`, `LogEntryDto`, `RootDto`,
  `LegacyRootDto`) at the bottom of `LiftRepository.kt`, mirroring the
  original file's own "JSON conversions" section. `Profile`/`Machine`/`LogEntry`
  themselves are deliberately left un-annotated — the DTOs are the only place
  that knows about the wire format. The version-1 legacy single-profile file
  format is still handled exactly as before.
- **IDs.** `java.util.UUID` (JVM-only) became `kotlin.uuid.Uuid` — new in the
  Kotlin 2.0.20+ common stdlib, `@OptIn(ExperimentalUuidApi::class)`, works
  identically on Android and iOS.
- **Time.** `System.currentTimeMillis()` became `kotlinx.datetime.Clock.System.now().toEpochMilliseconds()`,
  matching the Phase 1 date-API swap.
- **Dispatcher.** The background persistence write used `Dispatchers.IO`
  originally; this port uses `Dispatchers.Default` instead, only because this
  session can't compile for Kotlin/Native to confirm `Dispatchers.IO`'s
  exact availability/behavior at this exact Kotlin+coroutines version pair.
  For a few-KB JSON write the difference is not meaningful — if you'd rather
  match the original exactly, it's a one-word change in `LiftRepository.kt`.
- Added dependencies to `shared/build.gradle.kts`: `kotlinx-coroutines-core`
  (as `api`, not `implementation` — `LiftRepository`'s public `StateFlow`
  properties expose it to `androidApp`/`iosApp`, same reasoning applied to
  `kotlinx-datetime`, which was quietly `implementation`-only before and has
  been switched to `api` too, since `CoachTheme.isActiveOn(LocalDate)` etc.
  already needed it downstream) and `kotlinx-serialization-json` (internal
  detail, stayed `implementation`). Added the
  `org.jetbrains.kotlin.plugin.serialization` Gradle plugin alongside it.
- All business logic — profile activation/adoption, every mutation method,
  the pawprint/coach/outfit economy, the Sheets-restore merge — is unchanged
  from the original, since it was already plain Kotlin.

**Not yet checked, same caveat as Phase 1**: nothing here has been compiled.
The single piece with the most real uncertainty is `IosAppFileStorage.kt`'s
Foundation interop calls (`NSString.stringWithContentsOfFile`, `writeToFile`,
`NSFileManager`) — those signatures are written from documented Kotlin/Native
↔ Objective-C bridging behavior, not verified by Xcode. If Xcode's error list
flags anything in Phase 6, that file is the first place to look; everything
else in Phase 2 is ordinary multiplatform Kotlin and much less likely to need
changes.

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
completely separate platform implementations behind it. (Note: Phase 2 used
a plain interface + DI instead of `expect`/`actual` for file storage — the
same approach is worth considering here too, since it worked out simpler.)

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

**Current state**: Phases 0–2 done. Everything else is untouched Android-only
code still living only in the original `LiftLog` repo, to be ported
phase-by-phase in future sessions. **The first real test of any of this is
opening `~/dev/Pawgress` in Android Studio and letting Gradle sync** — no
build has been run anywhere yet (Phases 0–2 alike), since this environment
has no network path to Maven Central/Google's Maven repo.
