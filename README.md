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

Because of that gap, the two platforms get a user's data off the phone by
different routes, and Settings shows only the one that applies:

| | Android | iOS |
| --- | --- | --- |
| Backup | Google Sheets sync — a spreadsheet in the user's own Drive, a Log tab (one row per lift) and a Settings tab (grid setup + weight ranges) | The Files app — `pawgress.json` under **On My iPhone → Pawgress** |
| Restore | Settings → "Restore from Google Sheet", merged by Entry ID | Copy the saved `pawgress.json` back into that folder and reopen the app |
| Covers | Lifts and grid setup. Pawprints/coaches/outfits are deliberately excluded | Everything, since it *is* the save file |
| Automatic? | Yes — every lift uploads as it's logged | No — the user copies the file out |
| Needs | A Google account and a network | Nothing |

The iOS route is two `Info.plist` keys and no code: `UIFileSharingEnabled`
plus `LSSupportsOpeningDocumentsInPlace` publish the Documents directory
that `IosAppFileStorage` already writes to. The second key is what makes
restore actually work — without it the system hands apps a temporary copy,
so a replaced file wouldn't be the one the app reads.

Which card Settings renders comes from `AuthProvider.isSupported`, surfaced
as `SyncManager.isAvailable` → `MainViewModel.syncAvailable`. When iOS
sign-in lands, flipping that one flag to `true` is what switches the UI
over.

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
| `data/` | `Models.kt` (Machine, LogEntry, Profile, WeightRange), `MachineCatalog.kt` (39 preloaded machines and 33 free-weight exercises), `LiftRepository.kt` (profiles + JSON persistence), `GymDay.kt` (the 4am-to-4am day), date/time compatibility helpers |
| `sync/` | `AuthProvider.kt` (cross-platform auth interface), `SheetsApi.kt` (Google Sheets REST calls, Log and Settings tabs), `SettingsRows.kt` (the Settings tab's row format), `SyncManager.kt` (what to push, when) |
| `coach/` | The cosmetic coach gamification system (entertainment only — randomized affirmation lines from a fixed library, not training advice) — `CoachCatalog.kt` (20 coaches + costs), `CoachVoice.kt` (phrase library), `MotivationCatalog.kt`, `CoachOutfitQuotes.kt` |
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

Signing in with Google isn't offered on iOS yet (see Status above) —
Settings shows the Files-app backup card there instead. Everything else
should work.

## Toolchain versions

Gradle 9.5.0, AGP 9.3.2, Kotlin 2.3.21, Compose Multiplatform 1.12.0. The
Kotlin version specifically needs to stay at 2.3.21 or newer — some of this
project's dependencies (Ktor's Darwin artifacts, Compose Multiplatform's
iOS resources) publish klibs built with newer Kotlin than earlier versions
of this project used, and an older compiler can't read a newer klib. See
the comment block above the plugin versions in the root `build.gradle.kts`
for the full story if a future dependency bump hits this again.

## License

Two licenses, split the conventional way:

- **Source code** — [MIT](LICENSE). Short, permissive, and it carries the
  warranty disclaimer a CC license doesn't.
- **Documentation and artwork** — [CC BY 4.0](LICENSE-CC-BY-4.0.md). The coach
  and mascot portraits, seasonal outfits, machine icons, app icon, the pages
  under `docs/`, and this README. Reuse them for anything, including
  commercially, as long as you credit us and note any changes.

Creative Commons themselves recommend against applying CC licenses to
software, which is why the code isn't under one. `LICENSE-CC-BY-4.0.md` has a
table showing exactly which paths fall under which license, plus a suggested
attribution line.

The Pawgress **name** isn't licensed by either — build on the work freely,
just don't present the result as the official app.

## Known caveats

- **`IosAuthProvider` is a stub.** It reports `isSupported = false`, so
  Settings no longer offers a sign-in button on iOS at all — it shows the
  Files-app backup instructions instead. Real Google sign-in on iOS needs an
  OAuth Authorization Code + PKCE flow against `ASWebAuthenticationSession`,
  which is the next piece of work now that the app is confirmed running.
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
