import SwiftUI
import Shared

/// Smoke-test screen, mirroring androidApp's MainActivity.kt: proves the
/// shared Kotlin Multiplatform module links into a real iOS app target,
/// that the coach roster is reachable from Swift, and (Phase 2) that
/// LiftRepository + IosAppFileStorage actually construct and load/seed the
/// local JSON file. Replace with the real ported UI once Phase 5 of
/// PORTING_PLAN.md is underway.
struct ContentView: View {
    private let machineCount: Int

    init() {
        let repository = LiftRepository(storage: IosAppFileStorage())
        machineCount = Int(repository.current().machines.count)
    }

    var body: some View {
        List {
            Text("LiftRepository loaded \(machineCount) machines from local storage")
            ForEach(CoachCatalog.shared.ALL, id: \.id) { coach in
                Text("\(coach.name) (\(coach.breed)) — unlock \(coach.unlockCost) 🐾")
            }
        }
    }
}
