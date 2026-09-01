package com.balandman.pawgress.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * The whole app's navigation shell, shared between Android and iOS — the
 * single `@Composable` entry point each platform's real entry point
 * (Android's `MainActivity.kt`; iOS's `ContentView.swift`, once Phase 6
 * wires it up for real) hosts. Ported from LiftLog's Android-only `AppRoot`
 * (private composable inside `MainActivity.kt`), promoted to `commonMain`
 * since nothing in it is actually platform-specific anymore once two things
 * are pushed up as callbacks, the same pattern used everywhere else in this
 * port: launching the system account picker (Android's `AccountManager`
 * chooser vs. iOS's own sign-in page choosing an account inline) and opening
 * a URL (Android's `Intent.ACTION_VIEW` vs. iOS's own URL-opening API).
 *
 * The consent flow itself needs no callback here at all — unlike the
 * original, which had to bubble a `PendingIntent` all the way up to the
 * Activity via `onLaunchConsent`/`consentRequest`. Phase 4's `AuthProvider`
 * redesign moved that entirely behind `SyncManager`/`MainViewModel`, so this
 * navigation shell never needs to know consent UI exists.
 *
 * Two small, deliberate simplifications versus the original: screen
 * navigation state uses plain `remember` instead of `rememberSaveable` (so a
 * process death re-opens to the main screen rather than wherever the user
 * was — a real behavior change, but avoids pulling in another
 * not-yet-build-confirmed Compose Multiplatform artifact,
 * `runtime-saveable`, on top of everything else in this change), and the
 * system back button doesn't yet collapse a subscreen back to Main
 * (`BackHandler` is Android-only; cross-platform back handling in Compose
 * Multiplatform is still immature enough to defer rather than guess at,
 * same risk-tolerance as `IosAuthProvider`'s stub). Both are easy follow-ups
 * once there's a second platform to actually test against.
 */
@Composable
fun AppRoot(
    viewModel: MainViewModel,
    onLaunchAccountPicker: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val allMachines by viewModel.machines.collectAsState()
    val visibleMachines by viewModel.visibleMachines.collectAsState()
    val log by viewModel.log.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val message by viewModel.message.collectAsState()
    val accountPickerRequest by viewModel.accountPickerRequest.collectAsState()
    val pawprintsBalance by viewModel.pawprintsBalance.collectAsState()
    val unlockedCoachIds by viewModel.unlockedCoachIds.collectAsState()
    val selectedCoachId by viewModel.selectedCoachId.collectAsState()
    val unlockedOutfits by viewModel.unlockedOutfits.collectAsState()
    val equippedOutfits by viewModel.equippedOutfits.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // "main" | "settings" | "funfacts" | "trends" | "coach"
    var screen by remember { mutableStateOf("main") }
    var sheetMachineId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accountPickerRequest) {
        if (accountPickerRequest) {
            onLaunchAccountPicker()
            viewModel.accountPickerLaunched()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (screen) {
                "settings" -> SettingsScreen(
                    machines = allMachines,
                    syncState = syncState,
                    syncing = syncing,
                    pendingCount = pendingCount,
                    onBack = { screen = "main" },
                    onChooseAccount = viewModel::chooseAccount,
                    onDisconnectGoogle = viewModel::disconnect,
                    onSyncNow = viewModel::syncNow,
                    onSetVisible = viewModel::setVisible,
                    onSetAllVisible = viewModel::setAllVisible,
                    onRename = viewModel::rename,
                    onSetIcon = viewModel::setIcon,
                    onSetGroup = viewModel::setGroup,
                    onAddMachine = viewModel::addMachine,
                    onDeleteMachine = viewModel::deleteMachine,
                    onResetToday = viewModel::resetToday,
                    onFullReset = viewModel::fullReset,
                    onRestoreFromSheet = viewModel::restoreFromSheet,
                    onOpenUrl = onOpenUrl,
                    // DEBUG ONLY — remove along with the button in Settings before release.
                    onDebugGrantPawprints = viewModel::debugGrantPawprints,
                )

                "funfacts" -> FunFactsScreen(
                    log = log,
                    selectedCoachId = selectedCoachId,
                    equippedOutfits = equippedOutfits,
                    onBack = { screen = "main" },
                )

                "trends" -> TrendsScreen(
                    machines = allMachines,
                    log = log,
                    onBack = { screen = "main" },
                )

                "coach" -> CoachScreen(
                    pawprintsBalance = pawprintsBalance,
                    unlockedCoachIds = unlockedCoachIds,
                    selectedCoachId = selectedCoachId,
                    unlockedOutfits = unlockedOutfits,
                    equippedOutfits = equippedOutfits,
                    onBack = { screen = "main" },
                    onSelectCoach = viewModel::selectCoach,
                    onUnlockCoach = viewModel::unlockCoach,
                    onUnlockOutfit = viewModel::unlockOutfit,
                    onEquipOutfit = viewModel::equipOutfit,
                )

                else -> MainScreen(
                    machines = visibleMachines,
                    pawprintsBalance = pawprintsBalance,
                    onOpenSettings = { screen = "settings" },
                    onOpenFunFacts = { screen = "funfacts" },
                    onOpenTrends = { screen = "trends" },
                    onOpenCoach = { screen = "coach" },
                    onTapMachine = { sheetMachineId = it.id },
                )
            }
        }
    }

    // Read from the full list so the sheet reflects edits made while it is open.
    val sheetMachine = sheetMachineId?.let { id -> allMachines.firstOrNull { it.id == id } }
    if (sheetMachine != null) {
        LogSheet(
            machine = sheetMachine,
            onDismiss = { sheetMachineId = null },
            onConfirm = { weight, difficulty -> viewModel.logLift(sheetMachine.id, weight, difficulty) },
            onUndo = { viewModel.undoToday(sheetMachine.id) },
        )
    }
}
