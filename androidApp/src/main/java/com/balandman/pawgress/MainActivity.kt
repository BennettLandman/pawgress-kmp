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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CoachRosterProof()
                }
            }
        }
    }
}

/**
 * Temporary placeholder screen — it exists only to prove the shared
 * Kotlin Multiplatform module (coach roster, data models) links correctly
 * into a real Android app target. Replace with the ported UI screens from
 * LiftLog once Phase 5 of PORTING_PLAN.md is underway.
 */
@Composable
private fun CoachRosterProof() {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(CoachCatalog.ALL) { coach ->
            Text(
                "${coach.name} (${coach.breed}) — unlock ${coach.unlockCost} 🐾",
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}
