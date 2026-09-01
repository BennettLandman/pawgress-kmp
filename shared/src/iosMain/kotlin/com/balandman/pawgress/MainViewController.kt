package com.balandman.pawgress

import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.ComposeUIViewController
import com.balandman.pawgress.data.IosAppFileStorage
import com.balandman.pawgress.data.LiftRepository
import com.balandman.pawgress.sync.IosAuthProvider
import com.balandman.pawgress.sync.SyncManager
import com.balandman.pawgress.ui.AppRoot
import com.balandman.pawgress.ui.MainViewModel
import platform.UIKit.UIViewController

/**
 * The iOS entry point Swift calls to get a `UIViewController` hosting the
 * shared Compose Multiplatform UI -- the iOS equivalent of Android's
 * `MainActivity.kt`. A plain top-level function (rather than a class or
 * object), because Kotlin/Native's Objective-C interop turns top-level
 * functions in a file into static members of a synthesized `<FileName>Kt`
 * class -- for this file, `MainViewControllerKt.MainViewController()` is
 * exactly what `iosApp/PawgressSwift/ContentView.swift` calls.
 *
 * Two platform-specific callbacks `AppRoot` needs, resolved here instead of
 * with any hand-written Foundation/UIKit interop:
 *
 * - `onOpenUrl` uses Compose Multiplatform's own `LocalUriHandler` rather
 *   than calling `UIApplication.openURL` directly -- the direct call is
 *   deprecated as of iOS 18 in favor of `open(_:options:completionHandler:)`,
 *   which Kotlin/Native's UIKit bindings don't expose at all yet.
 *   `LocalUriHandler` is Compose's own cross-platform abstraction over
 *   exactly this (opens a URL the platform-appropriate way on every
 *   target), already available for free since `compose.ui` is already a
 *   `commonMain` dependency -- no new API surface to get wrong.
 * - `onLaunchAccountPicker` calls `viewModel.onAccountChosen(null)`
 *   directly, per `MainViewModel`'s own doc comment: iOS has no native
 *   "choose an account" picker the way Android's `AccountManager` does --
 *   once `IosAuthProvider` is implemented for real, Google's own sign-in
 *   page handles account selection inline, so there's no separate picker
 *   step to launch here at all. Until then this is honest about the current
 *   stub state: Settings' "Connect Google Account" shows "No account
 *   chosen." on iOS rather than pretending to do something it can't yet.
 *
 * `IosAuthProvider`/`IosAppFileStorage` are the two pieces of this app that
 * have never been through a Kotlin/Native compiler -- see their own doc
 * comments and `PORTING_PLAN.md`'s Phase 6 section. This function, and the
 * fact that it constructs both of them, is the first real test of that.
 */
fun MainViewController(): UIViewController {
    val repository = LiftRepository(IosAppFileStorage())
    val syncManager = SyncManager(repo = repository, auth = IosAuthProvider())
    val viewModel = MainViewModel(repository, syncManager)

    return ComposeUIViewController {
        val uriHandler = LocalUriHandler.current
        AppRoot(
            viewModel = viewModel,
            onLaunchAccountPicker = { viewModel.onAccountChosen(null) },
            onOpenUrl = { url -> uriHandler.openUri(url) },
        )
    }
}
