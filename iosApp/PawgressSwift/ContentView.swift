import SwiftUI
import Shared

/// Smoke-test screen, mirroring androidApp's MainActivity.kt: proves the
/// shared Kotlin Multiplatform module links into a real iOS app target,
/// that the coach roster is reachable from Swift, that (Phase 2)
/// LiftRepository + IosAppFileStorage actually construct and load/seed the
/// local JSON file, and (Phase 4) that SyncManager + IosAuthProvider at
/// least compile and construct under Kotlin/Native — IosAuthProvider is
/// still a stub (see PORTING_PLAN.md), so this does not attempt a real
/// sync. Replace with the real ported UI once Phase 5 of PORTING_PLAN.md
/// is underway.
struct ContentView: View {
    private let machineCount: Int
    private let syncManagerDescription: String

    init() {
        let repository = LiftRepository(storage: IosAppFileStorage())
        machineCount = Int(repository.current().machines.count)

        let syncManager = SyncManager(repo: repository, auth: IosAuthProvider())
        syncManagerDescription = "\(syncManager)"
    }

    var body: some View {
        List {
            Text("LiftRepository loaded \(machineCount) machines from local storage")
            Text("SyncManager ready: \(syncManagerDescription)")
            ForEach(CoachCatalog.shared.ALL, id: \.id) { coach in
                Text("\(coach.name) (\(coach.breed)) — unlock \(coach.unlockCost) 🐾")
            }
        }
    }
}
