package com.balandman.pawgress

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.balandman.pawgress.data.AndroidAppFileStorage
import com.balandman.pawgress.data.LiftRepository
import com.balandman.pawgress.sync.AccountPicker
import com.balandman.pawgress.sync.AndroidAuthProvider
import com.balandman.pawgress.sync.AndroidConsentLauncher
import com.balandman.pawgress.sync.SyncManager
import com.balandman.pawgress.ui.AppRoot
import com.balandman.pawgress.ui.MainViewModel
import com.balandman.pawgress.ui.theme.PawgressTheme

class MainActivity : ComponentActivity() {

    // Bridges Google's PendingIntent-based consent screen into a suspend
    // function -- see AndroidConsentLauncher's own doc comment. Built here
    // (rather than inside the ViewModel factory below) because
    // registerForActivityResult must be called during Activity
    // initialization, before onCreate even runs.
    private val consentLauncher = AndroidConsentLauncher()

    private lateinit var accountLauncher: ActivityResultLauncher<Intent>

    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                // One repository instance shared between the ViewModel and
                // SyncManager, matching the original LiftLog wiring.
                val repository = LiftRepository(AndroidAppFileStorage(applicationContext))
                val syncManager = SyncManager(
                    repo = repository,
                    auth = AndroidAuthProvider(applicationContext, consentLauncher),
                )
                MainViewModel(repository, syncManager)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val consentActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result -> consentLauncher.onResult(result) }
        consentLauncher.attach(consentActivityLauncher)

        // The system account chooser, used whenever the user is deliberately
        // picking or changing which Google account the app is working as.
        accountLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val email = if (result.resultCode == RESULT_OK) {
                AccountPicker.accountFrom(result.data)?.name
            } else {
                null
            }
            viewModel.onAccountChosen(email)
        }

        setContent {
            PawgressTheme {
                AppRoot(
                    viewModel = viewModel,
                    onLaunchAccountPicker = { accountLauncher.launch(AccountPicker.intent()) },
                    onOpenUrl = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                )
            }
        }
    }
}
