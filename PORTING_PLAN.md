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

## Phase 0 — Infrastructure (done, confirmed by a real build)

- Gradle multiplatform project scaffolded (`settings.gradle.kts`, root
  `build.gradle.kts`, `shared/build.gradle.kts` with `androidTarget()` +
  `iosX64()`/`iosArm64()`/`iosSimulatorArm64()` targets).
- Originally pinned to the same Gradle/AGP/Kotlin versions as LiftLog
  (Gradle 8.14.5, AGP 8.13.2, Kotlin 2.0.21). Opening the project in Android
  Studio auto-upgraded these to **Gradle 9.5.0, AGP 9.3.2, Kotlin 2.2.10**
  (Android Studio's own defaults for a new-ish project) — Bennett accepted
  that, and it built successfully, so those newer versions are now what's
  pinned in the repo. Android Studio also added a block of `android.*`
  compatibility flags to `gradle.properties` (opting out of a few AGP 9
  behavior changes — e.g. `android.newDsl=false` keeps the old
  `android { kotlinOptions { ... } }` DSL working instead of requiring an
  immediate rewrite to the new `ApplicationExtension`/`compilerOptions` APIs)
  and a `gradle/gradle-daemon-jvm.properties` pinning the Gradle daemon to
  JDK 21 — both are Gradle/AGP-generated, not hand-written.
- **Confirmed working via two real `BUILD SUCCESSFUL` runs from Bennett** —
  `androidApp:assembleDebug` (and its test variants) after the version bump,
  and again on the plain `androidApp` run. Notably, this also downloaded and
  set up the Kotlin/Native macOS-arm64 toolchain (`commonizeNativeDistribution`,
  ~315MB) as a side effect of Gradle configuring the iOS targets during sync —
  so the iOS build toolchain is already in place on Bennett's Mac for when
  Phase 6 gets there, even though nothing iOS-specific has been compiled yet.

## Phase 1 — Pure data/logic layer (done, confirmed by a real build)

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

**Confirmed working**: all of this — including Phase 1's `kotlinx-datetime`
API usage in `Models.kt`/`GymDay.kt`/`DateCompat.kt`/`MotivationCatalog.kt` —
compiles as part of `shared`'s `androidTarget` in the real Android Studio
build described in Phase 0. No longer "written from documentation,
unverified"; it's been through a real Kotlin compiler on the JVM target. (The
iOS targets still haven't been compiled — see Phase 2 and Phase 6.)

## Phase 2 — Persistence (done, Android side confirmed by a real build)

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

**Confirmed working, with one exception**: the real Android Studio build
compiles `LiftRepository.kt`, `AppFileStorage.kt`, and `AndroidAppFileStorage.kt`
cleanly (same successful `androidApp` builds as Phase 0/1). The one piece
still unverified is `IosAppFileStorage.kt`'s Foundation interop calls
(`NSString.stringWithContentsOfFile`, `writeToFile`, `NSFileManager`) —
Kotlin/Native code isn't part of an Android build, so this still needs
Xcode/a real Kotlin/Native compile to check. If Xcode's error list flags
anything in Phase 6, that file is the first place to look.

## Phase 3 — Sync / networking (`SheetsApi.kt` done; `SyncManager.kt` deferred to Phase 4)

`sync/SheetsApi.kt` (now `shared/commonMain/.../sync/SheetsApi.kt`) is ported.
Replaced raw `OkHttp` with **Ktor Client** (`ktor-client-core:3.5.2` as an
`api` dependency in commonMain; `ktor-client-okhttp` in androidMain,
`ktor-client-darwin` in iosMain as the platform engines — `SheetsApi`'s
default `HttpClient()` takes no explicit engine argument, since Ktor
auto-discovers whichever single engine dependency is on that target's
classpath). Every method is now `suspend fun` instead of a blocking call —
the original's "call this from a background dispatcher" rule becomes "call
this from a coroutine," which is what `SyncManager` was already doing via
`withContext(Dispatchers.IO)`. Replaced `org.json` with dynamic
`kotlinx.serialization.json` building (`buildJsonObject`/`buildJsonArray`,
plus a few small private `optString`/`optInt`-style helpers on `JsonObject`
mirroring org.json's forgiving, never-throws style) rather than defining
typed DTOs for every Sheets request/response shape — this is a request/response
API with a dozen different ad-hoc JSON shapes, not a stable persisted format,
so keeping the original's "build/parse JSON dynamically" approach was the
lower-risk translation. Replaced `java.net.URLEncoder` with Ktor's
`String.encodeURLParameter()`. Replaced `java.time.format.DateTimeFormatter`
with two small hand-written functions — the sheet only ever uses two fixed
patterns (`yyyy-MM-dd`, `HH:mm`), so a manual formatter/parser was safer here
than adopting a bigger multiplatform date-formatting API this session can't
verify by compiling.

**`sync/SyncManager.kt` is deliberately NOT ported yet**, even though it's
nominally part of "sync/networking" — its public API is inseparable from
Android's auth types (`android.accounts.Account`, `android.app.PendingIntent`
in `SyncResult.NeedsConsent`, `android.content.Context`, and a direct call
into `GoogleAuth`). `PendingIntent` specifically has no iOS equivalent at
all — iOS's auth flow won't hand back an intent to launch, it'll hand back
either a token directly or a URL to open. Porting `SyncManager.kt` properly
means designing its cross-platform shape at the same time as Phase 4's auth
abstraction, not mechanically swapping types in isolation — so it waits for
that phase, where it belongs anyway.

**Android side confirmed by a real build, after one real fix**: the first
Android Studio build after this phase failed with 17 compile errors, all
"actual type is String/Int/Boolean, but JsonElement was expected" —
`kotlinx.serialization.json`'s `put(key, String/Number/Boolean)` and
`add(String/Number/Boolean)` convenience overloads are extension functions,
not members, so without importing them by name the compiler only saw the
base `put(String, JsonElement)`/`add(JsonElement)` members and rejected
every primitive argument passed to `buildJsonObject`/`buildJsonArray`. Fixed
by adding the two missing imports (`kotlinx.serialization.json.put`,
`kotlinx.serialization.json.add`) — no logic was wrong, and it built clean
after that. Also: that same sync auto-upgraded the Gradle wrapper again
(9.5.0 → 9.7.1) and, notably, added `gradlew`/`gradlew.bat`/
`gradle/wrapper/gradle-wrapper.jar` — previously this project (like LiftLog)
had no wrapper scripts checked in, relying on Android Studio to resolve the
wrapper itself; Android Studio generated them this time, so they're now
tracked. Harmless, just worth knowing they exist now.

The Kotlin/Native side (`ktor-client-darwin`) is still unverified — same
caveat as everything else iOS-side in this project.

## Phase 4 — Auth (not started)

`sync/GoogleAuth.kt`, `sync/AccountPicker.kt` use Android's
`play-services-auth` and system account picker — entirely Android-specific.
iOS needs its own path: either Google's iOS Sign-In SDK, or a generic OAuth
flow via `ASWebAuthenticationSession`. This will likely become a plain
interface + DI in `shared/` (the same pattern Phase 2 used for file storage,
rather than `expect`/`actual` — it worked out simpler there), something like
"give me an access token, or tell me you need the user to consent" — with
completely separate platform implementations behind it.

**`sync/SyncManager.kt` belongs to this phase too**, not Phase 3 — see the
note there. Porting it means redesigning `SyncResult.NeedsConsent` (currently
holds an Android `PendingIntent`, which has no iOS equivalent) into something
generic enough for both platforms' consent flows, alongside whatever shape
the new auth interface takes. Its actual sync orchestration logic (resolving
the account from the token, `ensureSpreadsheet`, push/pull via `SheetsApi`) is
otherwise plain Kotlin and should port with minimal change once the auth
interface exists.

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

**Current state**: Phases 0–2 done and confirmed by two real Android Studio
builds. Phase 3 is done for `SheetsApi.kt`; `SyncManager.kt` moved into
Phase 4's scope (see that phase's note) since its type signature is
inseparable from the auth mechanism. Everything from here on is still
unverified by a real compiler — this environment still has no network path
to Maven Central/Google's Maven repo, so all of Claude's own checking stays
structural/static; Bennett verifies by building for real. Given how cleanly
Phases 0–2 came back from that first real build, the next thing worth doing
is another Android Studio sync now that Ktor's in the mix, before going
further into Phase 4/5.
