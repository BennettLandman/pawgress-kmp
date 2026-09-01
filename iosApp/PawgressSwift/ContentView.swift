import SwiftUI
import Shared

/// Smoke-test screen, mirroring androidApp's MainActivity.kt: proves the
/// shared Kotlin Multiplatform module links into a real iOS app target and
/// that the coach roster is reachable from Swift. Replace with the real
/// ported UI once Phase 5 of PORTING_PLAN.md is underway.
struct ContentView: View {
    var body: some View {
        List(CoachCatalog.shared.ALL, id: \.id) { coach in
            Text("\(coach.name) (\(coach.breed)) — unlock \(coach.unlockCost) 🐾")
        }
    }
}
