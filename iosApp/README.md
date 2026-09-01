# iOS app shell

This folder deliberately does **not** contain a checked-in `.xcodeproj` yet.
Xcode project files are fragile to hand-write outside of Xcode itself, so the
one-time project creation is a manual step — everything else (the actual
Kotlin logic, and now the actual UI) already lives in `shared/` and needs no
changes for iOS.

## One-time setup (do this once, in Xcode, on your Mac)

1. Open Xcode → File → New → Project → iOS → **App**.
2. Product Name: `iosApp`. Interface: **SwiftUI**. Language: **Swift**.
3. Save it *inside this `iosApp/` folder* (so the project ends up at
   `Pawgress/iosApp/iosApp.xcodeproj`).
4. Delete the auto-generated `ContentView.swift` and `iosAppApp.swift` Xcode
   created, and instead add the two files from `PawgressSwift/` in this
   folder (drag them into the Xcode project navigator, "Copy items if
   needed").
5. Link the shared Kotlin framework: select the `iosApp` target → **Build
   Phases** → **+** → **New Run Script Phase**, and paste:

   ```sh
   cd "$SRCROOT/.."
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```

   Drag this new script phase to run *before* "Compile Sources". This is the
   standard Kotlin Multiplatform Gradle plugin task — it builds `shared/` for
   whichever iOS architecture Xcode is currently targeting (simulator vs
   device) and copies the resulting `Shared.framework` into the app bundle.

6. Add these Build Settings (target → Build Settings → search for each):
   - `FRAMEWORK_SEARCH_PATHS`: `$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
   - `OTHER_LDFLAGS`: `-framework Shared`
7. Add `CADisableMinimumFrameDurationOnPhone` (type Boolean, value **YES**)
   to `Info.plist`. This isn't optional cosmetics — it's a documented
   Compose Multiplatform iOS requirement (unlocks ProMotion's higher refresh
   rates on supported devices; without it, Compose's own rendering can look
   stuttery on an iPhone that actually has a 120Hz display).
8. Build (⌘B). The first build will take a while — it's compiling the
   Kotlin/Native backend and the shared module for iOS for the first time,
   including a Kotlin/Native compile of `IosAppFileStorage.kt`,
   `IosAuthProvider.kt`, and the new `MainViewController.kt`, none of which
   has ever been through a real compiler before this. If it fails, that's
   useful information, not a setback — paste the exact Xcode build log/error
   back so it can be fixed and re-checked, the same rhythm used for every
   Android build so far in this project.

## What you'll see

Once it builds and runs (Simulator or device), you should see the actual
ported app — the real main grid, Settings, Coach, Fun Facts, and Trends
screens, navigated via the same shared `AppRoot` Android already uses — not
a placeholder. `ContentView.swift` wraps `MainViewController.kt`'s
`MainViewController()` (a `UIViewController` built via Compose
Multiplatform's `ComposeUIViewController`) in a `UIViewControllerRepresentable`
and hosts it as the whole window's content.

Signing in with Google **will not work yet** — `IosAuthProvider.kt` is a
deliberate stub (see its own doc comment and `PORTING_PLAN.md`'s Phase 4/6
notes): tapping "Connect Google Account" in Settings will just show a "No
account chosen." message. Everything that doesn't need Google sign-in
(logging lifts, the coach/pawprint economy, Fun Facts, Trends, hiding/
renaming/adding machines) should work against local storage exactly like the
Android app, backed by `IosAppFileStorage.kt` writing into this app's
Documents directory. Real Google sign-in (an OAuth Authorization Code + PKCE
flow against `ASWebAuthenticationSession`) is the next piece of work, once
this baseline is confirmed running for real.

## Status

All of `shared/commonMain` is ported — see `../PORTING_PLAN.md` for full
phase-by-phase detail. This is the **first real attempt to compile any of it
for iOS** — nothing in `shared/src/iosMain` (`IosAppFileStorage.kt`,
`IosAuthProvider.kt`, `MainViewController.kt`) has been through a
Kotlin/Native compiler yet, since neither the cloud environment nor the
Mac's sandboxed helper used for editing this project can reach Maven
Central/Google's Maven repo or run Xcode. This one-time Xcode setup, and the
build log it produces, is what actually checks all of it for the first
time.
