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

## Phase 3 — Sync / networking (done for `SheetsApi.kt`, confirmed by a real build; `SyncManager.kt` deferred to Phase 4)

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

A follow-up `assembleDebug` (after the import fix landed) came back
`BUILD SUCCESSFUL` with `:shared:compileDebugKotlinAndroid` actually
executing (not up-to-date/cached) — confirming the fix holds under a real
compile, not just a sync. The Kotlin/Native side (`ktor-client-darwin`,
and `IosAppFileStorage.kt`'s Foundation interop from Phase 2) is still
unverified — a separate Gradle sync did successfully resolve and download
the full iOS dependency graph (Ktor 3.5.2's darwin artifacts for all three
iOS targets, kotlinx-coroutines-core/kotlinx-serialization-core at 1.11.0,
the Kotlin/Native macOS toolchain), which proves the versions are real and
fetchable, but a sync task never invokes the Kotlin/Native compiler — so
that's not compile confirmation, just dependency resolution. Still need an
actual iOS compile/link task (or Phase 6's Xcode wiring) before treating
the iOS side as verified.

## Phase 4 — Auth (commonMain + Android done, confirmed by a real build; iOS stubbed)

`sync/GoogleAuth.kt`, `sync/AccountPicker.kt` use Android's
`play-services-auth` and system account picker — entirely Android-specific.
iOS needs its own path. This became a plain interface + DI in `shared/`
(the same pattern Phase 2 used for file storage, rather than
`expect`/`actual`), shaped as:

```kotlin
sealed interface AuthOutcome {
    data class Success(val accessToken: String) : AuthOutcome
    data class Failure(val message: String) : AuthOutcome
}

interface AuthProvider {
    // Silent path: returns a token if consent was already granted, no UI.
    // Returns null (not Failure) specifically when UI is required, so the
    // caller knows to decide whether showing that UI is appropriate right now.
    suspend fun silentAuthorize(accountHint: String? = null): AuthOutcome?

    // Always runs whatever UI the platform needs (consent screen, sign-in
    // page) and suspends until it resolves to a token or a failure.
    suspend fun requestConsent(accountHint: String? = null): AuthOutcome
}
```

This is a bigger win than it looks: `PendingIntent` (Android's "here's a UI
flow to launch and get a result back from") has no iOS equivalent at all, and
trying to model "a launchable, resumable UI flow" generically across both
platforms is the wrong abstraction to reach for. Instead, *launching and
awaiting that platform UI* is pushed entirely behind `requestConsent()` — so
`shared/` never needs to know what a `PendingIntent` or an
`ASWebAuthenticationSession` even is. That in turn means `SyncManager` no
longer needs a `SyncResult.NeedsConsent` case at all — see below.

**`sync/SyncManager.kt` belongs to this phase too**, not Phase 3 — see the
note there. It's now ported into `shared/commonMain/.../sync/SyncManager.kt`,
unchanged in its actual orchestration logic (resolving the account from the
token, `ensureSpreadsheet`, push/pull via `SheetsApi`) but redesigned at the
auth boundary: `allowConsentUi = true` now calls `requestConsent()` directly
instead of bubbling a `PendingIntent` up to the UI layer to launch itself, so
`SyncResult.NeedsConsent` is gone entirely — a real simplification, not just
a type swap, made possible by moving "show and await the platform UI" fully
behind the interface.

**Android implementation (`shared/androidMain/.../sync/AndroidAuthProvider.kt`)**:
ports `GoogleAuth.kt`'s existing Identity Authorization API logic
(`play-services-auth:21.3.0`, same version already proven in the original
LiftLog app) almost unchanged, adapted to the new interface. The one new
piece is `ConsentLauncher` — an Android-only interface (not exposed to
commonMain) that bridges Android's callback-based
`ActivityResultLauncher<IntentSenderRequest>` into a suspend function via a
`CompletableDeferred`, matching the exact `registerForActivityResult` +
`StartIntentSenderForResult` pattern already used and working in LiftLog's
own `MainActivity.kt`. `AccountPicker.kt` ports over unchanged — it stays
Android-only since forcing a specific account choice is already handled
differently on the two platforms (Android's system chooser vs. Google's own
sign-in page doing account selection inline), and its output (an email) just
becomes the `accountHint` argument to the shared interface, so no interface
change was needed for it.

**iOS implementation is stubbed, not real yet.** A `IosAuthProvider.kt`
exists and conforms to the interface so the shared module compiles for iOS,
but every method currently returns `AuthOutcome.Failure` with a clear
"not implemented yet" message. The real iOS path — a generic OAuth 2.0
Authorization Code + PKCE flow against Google's endpoints directly
(`accounts.google.com/o/oauth2/v2/auth` → `oauth2.googleapis.com/token`),
with `ASWebAuthenticationSession` supplying only the "show this URL, give me
back the redirect" piece — was deliberately not attempted blind this
session. Unlike `IosAppFileStorage.kt`'s Foundation interop (simple,
low-risk to get slightly wrong), this involves genuine security-relevant
code (PKCE code-verifier/challenge generation needs a real SHA-256, which
has no pure-Kotlin-common stdlib implementation and would mean either a new
multiplatform crypto dependency or hand-rolled crypto) and Kotlin/Native
interop with `AuthenticationServices` that can't be checked here at all —
no Xcode, no simulator, no way to catch a wrong Swift/Kotlin bridging call
short of Bennett hitting it in a real build. That combination is worth
doing for real once Phase 6 gives an actual Xcode project and Simulator to
test against, rather than shipping a guess now. The design is written down
here so implementing it later is a known shape, not a fresh investigation.

**Android side confirmed by a real build**, including this phase's new
code specifically: `assembleDebug` came back `BUILD SUCCESSFUL` with
`:shared:compileDebugKotlinAndroid` actually executing, so `AuthProvider`,
`SyncManager`, `AndroidAuthProvider`, `ConsentLauncher`/
`AndroidConsentLauncher`, `AccountPicker`, and the new
`play-services-auth`/`androidx.activity:activity-ktx` dependencies on
`shared` all compile for real. This is only a compile check, not a runtime
one — nothing has actually called `sync()`/`requestConsent()` yet, since
the smoke-test screen only constructs the objects. `play-services-auth`
also needs the OAuth client's SHA-1 registered per LiftLog's own
`SETUP.md` step 4, but this KMP project uses a different package name
(`com.balandman.pawgress` vs. `com.balandman.liftlog`) — a **new** Android
OAuth client (or an added package name + SHA-1 on the existing one) will
need registering in Google Cloud Console before real sign-in works, even
though the code compiles. That's a one-time manual step for Bennett, not
something Claude can do.

## Phase 5 — UI (in progress: infra + asset lookup + MainViewModel + MainScreen done)

All of `ui/*.kt` (MainScreen, CoachScreen, SettingsScreen, TrendsScreen,
FunFactsScreen, LogSheet, MainViewModel, the art/icon lookup catalogs) needs
to move into `shared/commonMain` using **Compose Multiplatform** instead of
plain Jetpack Compose. Most of the Foundation/Material3 usage in these files
carries over close to unchanged. Two things don't, and both are now designed
and implemented (not yet build-confirmed):

- **Asset lookup.** The Android-only originals found drawables by reflecting
  over the generated `R.drawable` class's fields by name pattern (e.g. every
  field starting with `mascot_`) — deliberately, so new art needs zero code
  changes. Compose Multiplatform's generated `Res` object turns out to have
  a direct equivalent: `Res.allDrawableResources: Map<String, DrawableResource>`,
  a compile-time-generated map of every resource's filename to its handle —
  not reflection, but the same "scan everything, filter by name pattern"
  shape. `MascotCatalog.kt` and `CoachOutfitArt.kt` are ported onto this with
  their logic otherwise unchanged; still zero-config for new art. Confirmed
  this API actually exists via web research (not guessed) before designing
  around it — see the doc comments in those two files for the exact mechanism.
- **Date formatting** — not yet reached (no screens ported yet). Still
  planned as hand-written multiplatform formatting for the handful of fixed
  patterns actually used (`"EEEE, MMM d"`, `"h:mm a"`, `"MMM d"`, `"EEE"`,
  `"MMM"`), once `MainScreen.kt`/`LogSheet.kt`/`SettingsScreen.kt`/
  `TrendsScreen.kt` are actually ported.

**What's done so far, checkpointed here deliberately before porting the
~2,500 remaining lines of screens/ViewModel on top of it** — this is the
single biggest new piece of unverified toolchain introduced in this project
(Compose Multiplatform's resource codegen has never been exercised by a
real build), so it's worth confirming the foundation compiles before
building a lot more on it, same reasoning as verifying Ktor before Phase 4:

- `shared/build.gradle.kts`: applied `org.jetbrains.compose` (1.12.0) and
  `org.jetbrains.kotlin.plugin.compose`, added `compose.runtime`/
  `compose.foundation`/`compose.material3`/`compose.ui` (implementation) and
  `compose.components.resources` (**api**, not implementation — androidApp's
  own code calls `painterResource()`/references `DrawableResource` directly,
  and unlike `compose.ui`, this CMP-only artifact has no other route onto
  androidApp's classpath the way real `androidx.compose.ui` classes do).
  Pinned `compose.resources { packageOfResClass = "com.balandman.pawgress.resources" }`
  explicitly rather than relying on the default `{group}.{module}.generated.resources`
  derivation, which depends on this project's (unset) Gradle `group` and has
  a documented history of inconsistent output
  (JetBrains/compose-multiplatform#4320).
- Root `build.gradle.kts`: bumped `org.jetbrains.kotlin.multiplatform` from
  2.0.21 to 2.2.10 — left behind by an earlier Android Studio upgrade that
  only bumped `kotlin.android`/`kotlin.plugin.compose`, and Compose
  Multiplatform 1.12.0 needs Kotlin 2.1.0+ regardless. Added
  `org.jetbrains.compose` version 1.12.0.
- All 271 image assets (227 WebP illustrations/mascots/outfits + 44 XML line
  icons, everything under LiftLog's `res/drawable`/`res/drawable-nodpi`
  except the two app-icon-only files, which stay Android-only) copied into
  `shared/src/commonMain/composeResources/drawable/` — flattening away the
  `-nodpi` qualifier, since Compose Multiplatform's plain `drawable/` folder
  doesn't density-scale by default either, which was the whole point of
  `-nodpi` on the Android side. Checked every XML icon uses only plain
  `<vector>`/`<path>` elements first (no gradients/groups/clip-paths), which
  Compose Multiplatform's resource parser is confirmed to support.
- `ui/theme/Theme.kt` ported unchanged (renamed `LiftLogTheme` →
  `PawgressTheme`) — it's pure `androidx.compose.foundation`/`material3`/
  `runtime`, no Android-specific API at all, so it needed no redesign.
- `ui/MachineIcons.kt`, `ui/MachineArt.kt`, `ui/CoachArt.kt` ported
  mechanically (`Int`/`@DrawableRes` → `DrawableResource`,
  `androidx.compose.ui.res.painterResource` →
  `org.jetbrains.compose.resources.painterResource`, `java.time.LocalDate` →
  `kotlinx.datetime.LocalDate` in `CoachArt.kt`). Every key in
  `MachineIcons`'s two maps checked against the original by count (44 line
  icons, 45 illustrations including `shuttle_run`) — matches exactly.
- `MainActivity.kt`'s smoke-test screen extended to actually call
  `MascotCatalog.forNumber(1)` and render it via `painterResource` + `Image`
  — exercises the full pipeline (codegen → lookup → render), not just
  "compiles with unused files present."

**Not yet touched (at that checkpoint)**: `MainViewModel.kt`, `MainScreen.kt`,
`LogSheet.kt`, `CoachScreen.kt`, `FunFactsScreen.kt`, `TrendsScreen.kt`,
`SettingsScreen.kt` — roughly 2,500 lines across the biggest screens, plus
the real `MainActivity.kt`/iOS entry-point wiring to actually show them.
Picked up after a real build confirmed the resource pipeline above.

**First real build after this checkpoint failed at the AAR metadata check**
(not a compile error): Compose Multiplatform 1.12.0's Android artifacts
(`androidx.compose.*:1.12.0`, e.g. `foundation-android`, `ui-android`,
`runtime-saveable-android`) require compiling against API 37, and both
`shared` and `androidApp` were still on `compileSdk = 35`. Fixed by bumping
`compileSdk` to 37 in both modules' `build.gradle.kts` (`targetSdk`/`minSdk`
left untouched — those are independent of `compileSdk` per Android's own
docs, and nothing here needs either to change).

**Confirmed working by a real build after that fix.** `BUILD SUCCESSFUL`,
and critically the task list shows the actual Compose resource pipeline
running for the first time — `generateComposeResClass`,
`generateResourceAccessorsForCommonMain`,
`generateActualResourceCollectorsForAndroidMain`,
`copyDebugComposeResourcesToAndroidAssets`, `parseDebugLocalResources`,
`generateDebugRFile` — followed by `:shared:compileDebugKotlinAndroid` and
`:androidApp:compileDebugKotlin` both actually executing. This confirms the
full chain end to end: 271 assets under `composeResources/drawable/`
codegen correctly, `Res.allDrawableResources`-based lookup
(`MascotCatalog`/`CoachOutfitArt`) compiles, `painterResource` renders a
real image in the Android smoke test, and the explicit
`packageOfResClass` pin resolved without the ambiguity the default
derivation is known for. The single biggest unverified-toolchain risk in
this phase is now cleared — screens can be ported onto this with
confidence.

**Since that build confirmation (not yet re-verified by a build):**

- A comprehensive audit of every `R.drawable.X` reference across all of
  LiftLog's `ui/*.kt` + `MainActivity.kt` (92 unique references via
  `grep -rhoE 'R\.drawable\.[a-zA-Z0-9_]+'`) cross-checked against
  `composeResources/drawable/` turned up exactly one gap:
  `ic_trend_graph.xml` lives directly under `res/drawable/`, not matched by
  the `ic_m_*` glob used for the other line icons during the original
  migration sweep. Copied it in (checked first for gradient/group/clip-path
  elements, same as every other icon) — the audit now shows zero missing
  assets before porting any more screens.
- `MainViewModel.kt` ported onto `androidx.lifecycle:lifecycle-viewmodel`
  (`2.11.0`, `api` in commonMain + `export()`ed from each iOS
  `binaries.framework` block, since Swift/Xcode in Phase 6 will need to see
  the `ViewModel` type through the compiled framework). Constructor
  injection (`repo: LiftRepository, syncManager: SyncManager`) replaces
  `AndroidViewModel(application)`'s `(application as LiftLogApp).repository`
  pattern, since `AndroidViewModel` has no multiplatform equivalent — the
  same plain-interface-plus-DI approach used everywhere else in this port.
  Phase 4's `AuthProvider` redesign pays off concretely here: with
  `SyncResult.NeedsConsent` gone, the entire
  `_consentRequest`/`onConsentResult(context, resultOk, data: Intent?)`/
  `pendingRestore`/`GoogleAuth.resultFromIntent` state machine from the
  original ViewModel simply doesn't exist in the port.
- `DateFormats.kt` (new, `ui/` package): hand-written replacement for
  `java.time.format.DateTimeFormatter`, which has no multiplatform
  equivalent — `kotlinx-datetime`'s `LocalDate`/`DayOfWeek`/`Month` enums
  have no locale-aware display-name API in common code. Hardcodes English
  month/weekday abbreviation and full-name tables (there was never any other
  locale in play) covering all six exact `DateTimeFormatter.ofPattern(...)`
  calls found via `grep -rn "DateTimeFormatter\.ofPattern"` across every
  `ui/*.kt` file: `"h:mm a"`, `"MMM d"`, `"EEEE, MMM d"`,
  `"MMM d 'at' h:mm a"`, `"EEE"`, `"MMM"`. Only the first three are consumed
  yet (by `MainScreen.kt`); the last three (`"EEE"`/`"MMM"` for
  `TrendsScreen.kt`, the `'at'` combo for `SettingsScreen.kt`) wait on those
  screens' own ports.
- `MainScreen.kt` ported: the main grid (`MachineTile`, `EmptyState`,
  `buildSubtitle`) carries over onto the already-ported `MachineArt`/
  `DifficultyColors`/`GroupColors`/`LocalTileColors`/`GymDay`/
  `Res.drawable.ic_trend_graph`, plus `DateFormats.weekdayMonthDay()` for the
  `"EEEE, MMM d"` header. The only real gap: `Icons.Filled.CheckCircle`/
  `Icons.Filled.Settings` come from `androidx.compose.material.icons`, which
  Compose Multiplatform ships as a separate artifact
  (`org.jetbrains.compose.material:material-icons-extended`) under the same
  `compose.materialIconsExtended` Gradle accessor used for the other
  `compose.*` aliases already in this file — confirmed the accessor exists
  and its coordinates via the JetBrains plugin source before adding it, same
  as every other new dependency in this port. `androidApp`'s smoke-test
  `MainActivity.kt` has not been touched — `MainScreen.kt` is not wired up
  to actually render yet, so this is compile-level progress only until the
  next `assembleDebug`.

**Not yet touched**: `LogSheet.kt`, `CoachScreen.kt`, `FunFactsScreen.kt`,
`TrendsScreen.kt`, `SettingsScreen.kt`, plus wiring the real `MainActivity.kt`
(and iOS's entry point) to actually construct `MainViewModel` and show the
ported screens instead of the current smoke test. `compose.materialIconsExtended`
is new, unverified toolchain (like the resource pipeline was before it),
so this is a natural point to get another real `assembleDebug` before
porting the remaining ~2,200 lines on top of it.

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

**Current state**: Phases 0–4 all confirmed by real Android Studio builds on
the Android side — most recently `assembleDebug` succeeding with
`:shared:compileDebugKotlinAndroid` actually executing after the new
`AuthProvider`/`SyncManager`/`AndroidAuthProvider` code and its two new
Gradle dependencies landed. That's compile confirmation only — no runtime
path (an actual `sync()` call, real Google consent) has been exercised yet,
since the smoke-test screen only constructs the objects. The iOS side has
confirmed dependency resolution (Ktor + kotlinx iOS artifacts, Kotlin/Native
toolchain all download and commonize cleanly) but no confirmed compile —
that stays open until a real iOS link/compile task runs, or Phase 6's Xcode
wiring happens, and `IosAuthProvider` is a deliberate stub either way (see
Phase 4). Everything Phase 5 on is unverified by a real compiler — this
environment still has no network path to Maven Central/Google's Maven repo,
so all of Claude's own checking stays structural/static; Bennett verifies by
building for real. Next up: Phase 5 (Compose Multiplatform UI port) — the
biggest remaining chunk — unless Bennett would rather wire up real sign-in
UI first to exercise Phase 4's auth code end to end.
