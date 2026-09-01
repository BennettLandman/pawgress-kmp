package com.balandman.pawgress

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.balandman.pawgress.coach.CoachCatalog
import com.balandman.pawgress.data.AndroidAppFileStorage
import com.balandman.pawgress.data.LiftRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Phase 2 smoke test: proves LiftRepository + AndroidAppFileStorage
        // actually construct, load (or seed) the JSON file, and expose a
        // profile — before any real UI is wired to them in Phase 5.
        val repository = LiftRepository(AndroidAppFileStorage(this))
        val machineCount = repository.current().machines.size

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CoachRosterProof(machineCount)
                }
            }
        }
    }
}

/**
 * Temporary placeholder screen — it exists only to prove the shared
 * Kotlin Multiplatform module (coach roster, data models, and now the
 * persistence layer) links correctly into a real Android app target.
 * Replace with the ported UI screens from LiftLog once Phase 5 of
 * PORTING_PLAN.md is underway.
 */
@Composable
private fun CoachRosterProof(machineCount: Int) {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text(
                "LiftRepository loaded $machineCount machines from local storage",
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        items(CoachCatalog.ALL) { coach ->
            Text(
                "${coach.name} (${coach.breed}) — unlock ${coach.unlockCost} 🐾",
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}
