package com.balandman.pawgress

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.balandman.pawgress.coach.CoachCatalog
import com.balandman.pawgress.data.AndroidAppFileStorage
import com.balandman.pawgress.data.LiftRepository
import com.balandman.pawgress.sync.AndroidAuthProvider
import com.balandman.pawgress.sync.AndroidConsentLauncher
import com.balandman.pawgress.sync.SyncManager
import com.balandman.pawgress.ui.MascotCatalog
import com.balandman.pawgress.ui.theme.PawgressTheme
import org.jetbrains.compose.resources.painterResource

class MainActivity : ComponentActivity() {

    // Phase 4 smoke test: wires up the same registerForActivityResult +
    // StartIntentSenderForResult pattern LiftLog's own MainActivity uses,
    // bridged into a suspend function via AndroidConsentLauncher — proves
    // SyncManager/AndroidAuthProvider actually construct and link, before
    // any real "Sync Now" UI exists (that's Phase 5).
    private val consentLauncher = AndroidConsentLauncher()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result -> consentLauncher.onResult(result) }
        consentLauncher.attach(activityResultLauncher)

        // Phase 2 smoke test: proves LiftRepository + AndroidAppFileStorage
        // actually construct, load (or seed) the JSON file, and expose a
        // profile — before any real UI is wired to them in Phase 5.
        val repository = LiftRepository(AndroidAppFileStorage(this))
        val machineCount = repository.current().machines.size

        val syncManager = SyncManager(
            repo = repository,
            auth = AndroidAuthProvider(this, consentLauncher),
        )

        setContent {
            PawgressTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CoachRosterProof(machineCount, syncManager)
                }
            }
        }
    }
}

/**
 * Temporary placeholder screen — it exists only to prove the shared
 * Kotlin Multiplatform module (coach roster, data models, persistence, and
 * now the auth/sync wiring) links correctly into a real Android app target.
 * Replace with the ported UI screens from LiftLog once Phase 5 of
 * PORTING_PLAN.md is underway.
 */
@Composable
private fun CoachRosterProof(machineCount: Int, syncManager: SyncManager) {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text(
                "LiftRepository loaded $machineCount machines from local storage",
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        item {
            Text(
                "SyncManager ready: $syncManager",
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        item {
            // Phase 5 smoke test: proves the Compose Multiplatform resource
            // pipeline actually works end to end -- codegen (Res.drawable),
            // MascotCatalog's Res.allDrawableResources-based lookup, and
            // painterResource all the way through to a real rendered Image --
            // not just "compiles with unused files present."
            val mascot = MascotCatalog.forNumber(1)
            if (mascot != null) {
                Image(
                    painter = painterResource(mascot),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
            } else {
                Text(
                    "MascotCatalog.forNumber(1) returned null",
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        items(CoachCatalog.ALL) { coach ->
            Text(
                "${coach.name} (${coach.breed}) — unlock ${coach.unlockCost} 🐾",
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}
