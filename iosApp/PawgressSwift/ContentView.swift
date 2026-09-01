import SwiftUI
import Shared

/// Bridges the shared Compose Multiplatform `AppRoot` into SwiftUI.
///
/// `MainViewControllerKt.MainViewController()` is the generated
/// Objective-C/Swift accessor for `MainViewController.kt`'s top-level
/// `MainViewController()` function (see that file's doc comment): Kotlin's
/// top-level functions become static members of a synthesized
/// `<FileName>Kt` class, so a function named the same as its file is not
/// actually ambiguous -- `MainViewController.kt` -> `MainViewControllerKt`.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // No SwiftUI-side state currently feeds into the Compose UI, so
        // there's nothing to push down on update.
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea() // AppRoot's own Scaffold handles insets.
    }
}
