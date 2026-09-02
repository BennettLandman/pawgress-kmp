# Pawgress (Kotlin Multiplatform)

Pawgress is an app for tracking how much weight you lift on each gym
machine — no reps, no sets, no rest timers, just one number per machine per
gym day. This repo is a **Kotlin Multiplatform (KMP)** rewrite of the
original Android-only app, sharing one Kotlin codebase between an Android
app and a real iPhone app via Compose Multiplatform.

The original, Android-only version lives in a separate repo
([`pawgress`](https://github.com/BennettLandman/pawgress)) and keeps
working exactly as it always has — nothing here touches it. This repo
started from scratch and re-implements the same app on top of a shared
`shared/` module, adding an iOS target the original never had.

## Status

**Both platforms build and run.** Android has run on-device since early in
the port. As of this writing, the iOS app also builds in Xcode
(`BUILD SUCCESSFUL`), installs, launches on the Simulator, and renders the
real ported UI — the same six screens and navigation shell Android uses.

The one thing that doesn't work yet on iOS is signing in with a Google
account — `IosAuthProvider` is a deliberate stub for now (see
[`PORTING_PLAN.md`](PORTING_PLAN.md)). Everything else — logging lifts, the
coach/pawprint economy, Fun Facts, Trends, hiding/renaming/adding machines —
works fully offline against local storage on both platforms.

`PORTING_PLAN.md` has the full phase-by-phase history if you want the
detail; this file is just the practical "how do I build this" reference.

## Repo layout

```
Pawgress/
├── shared/                  Kotlin Multiplatform module — almost all the app lives here
│   └── src/
│       ├── commonMain/      platform-independent code (UI, data, sync logic)
│       ├── androidMain/     Android-only implementations (file storage, Google auth)
│       └── iosMain/         iOS-only implementations (file storage, auth stub)
├── androidApp/               thin Android app module (just MainActivity.kt)
├── iosApp/
│   ├── PawgressSwift/        the two Swift source files the Xcode project uses
│   ├── iosApp/                the real Xcode project (iosApp.xcodeproj)
│   └── README.md              one-time Xcode project setup, if you ever need to redo it
└── PORTING_PLAN.md           full history of the Android → KMP port, phase by phase
```

Inside `shared/src/commonMain/kotlin/com/balandman/pawgress/`:

| Package | What's in it |
| --- | --- |
| `data/` | `Models.kt` (Machine, LogEntry, Profile), `MachineCatalog.kt` (the 36 preloaded machines), `LiftRepository.kt` (profiles + JSON persistence), `GymDay.kt` (the 4am-to-4am day), date/time compatibility helpers |
| `sync/` | `AuthProvider.kt` (cross-platform auth interface), `SheetsApi.kt` (Google Sheets REST calls), `SyncManager.kt` (what to push, when) |
| `coach/` | The coach gamification system — `CoachCatalog.kt` (20 coaches + costs), `CoachVoice.kt` (phrase library), `MotivationCatalog.kt`, `CoachOutfitQuotes.kt` |
| `ui/` | All six screens (`MainScreen`, `LogSheet`, `CoachScreen`, `FunFactsScreen`, `TrendsScreen`, `SettingsScreen`), `AppRoot.kt` (shared navigation shell), `MainViewModel.kt`, artwork lookup (`MachineArt`, `MascotCatalog`, `CoachOutfitArt`) |

`androidMain` and `iosMain` each hold just the platform-specific pieces
those `common` interfaces need: local file storage
(`AndroidAppFileStorage.kt` / `IosAppFileStorage.kt`), the current-time
helper, and Google auth (`AndroidAuthProvider.kt`, a real implementation;
`IosAuthProvider.kt`, the stub mentioned above).

## Building the Android app

1. Open the `Pawgress` folder in Android Studio.
2. Let Gradle sync — it will download the Android and Kotlin/Native
   toolchains it needs on first sync.
3. Run the `androidApp` configuration on a device or emulator.

Same Google Cloud OAuth setup as the original app is needed for sign-in to
work (enable the Sheets API, add test users, create an Android OAuth client
for package `com.balandman.pawgress` with your debug keystore's SHA-1) —
see the original repo's `SETUP.md` for the general steps; the package name
here is `com.balandman.pawgress`, not `com.balandman.liftlog`, so it needs
its **own** OAuth client even if you're reusing the same Google Cloud
project.

## Building the iOS app

You need a Mac with Xcode installed. The Xcode project is already checked
in at `iosApp/iosApp/iosApp.xcodeproj`, so in the common case:

1. Open `iosApp/iosApp/iosApp.xcodeproj` in Xcode.
2. Pick a Simulator (or a real device) as the run target.
3. Build and run (⌘R). The first build will take a while — it invokes
   Gradle to compile the shared Kotlin module for Kotlin/Native before
   Xcode compiles the Swift wrapper around it.

If the `.xcodeproj` is ever missing or needs to be recreated from scratch
(for example, after a clean checkout that excludes it, or if Xcode's
project format changes enough to need regenerating), `iosApp/README.md`
has the full one-time setup steps — creating the project, wiring the
Gradle run-script phase that builds `shared/`, and the build settings it
needs.

Signing in with Google won't do anything yet on iOS (see Status above);
everything else should work.

## Toolchain versions

Gradle 9.5.0, AGP 9.3.2, Kotlin 2.3.21, Compose Multiplatform 1.12.0. The
Kotlin version specifically needs to stay at 2.3.21 or newer — some of this
project's dependencies (Ktor's Darwin artifacts, Compose Multiplatform's
iOS resources) publish klibs built with newer Kotlin than earlier versions
of this project used, and an older compiler can't read a newer klib. See
the comment block above the plugin versions in the root `build.gradle.kts`
for the full story if a future dependency bump hits this again.

## Known caveats

- **`IosAuthProvider` is a stub.** Tapping "Connect Google Account" on iOS
  currently just fails with "No account chosen." Real Google sign-in on iOS
  needs an OAuth Authorization Code + PKCE flow against
  `ASWebAuthenticationSession`, which is the next piece of work now that
  the app is confirmed running.
- **No network access to Maven Central or Google's Maven repo was
  available while most of this port was written and reviewed**, so a lot
  of it was checked structurally (static analysis, careful reading against
  library source) rather than by compiling. Both platforms now build and
  run for real, but if you hit a compile error that looks like a stale
  assumption from that period, it's worth checking `PORTING_PLAN.md` for
  context before assuming it's a new regression.
- **Sync is one-directional**, same as the original app: editing the
  Google Sheet by hand won't flow back into the app unless you use
  Settings → "Restore from Google Sheet", which merges by Entry ID.
