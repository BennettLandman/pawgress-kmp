# iOS app shell

This folder deliberately does **not** contain a checked-in `.xcodeproj` yet.
Xcode project files are fragile to hand-write outside of Xcode itself, so the
one-time project creation is a manual step — everything else (the actual
Kotlin logic) already lives in `shared/` and needs no changes for iOS.

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
7. Build (⌘B). The first build will take a while — it's compiling the
   Kotlin/Native backend and the shared module for iOS for the first time.

## Once that's done

`import Shared` in any Swift file gives you everything currently ported:
`CoachCatalog.shared.ALL`, `MonthDay`, `CoachTheme`, etc. — Kotlin `object`s
become `.shared` singletons in the generated Objective-C/Swift interop layer,
and top-level Kotlin functions land in a synthesized class named after their
file (e.g. `ModelsKt.outfitKey(...)`).

## Status

Only the pure data/catalog layer (`shared/src/commonMain`) is ported so far —
see `../PORTING_PLAN.md` for what's done and what's still Android-only.
There's no real iOS UI yet; `ContentView.swift` here is just a smoke test
that lists the coach roster from the shared module, mirroring what
`androidApp`'s `MainActivity.kt` does on the Android side.
