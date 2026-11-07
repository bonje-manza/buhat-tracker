package com.buhat.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import com.buhat.tracker.domain.AppTheme
import com.buhat.tracker.ui.BuhatApp
import com.buhat.tracker.ui.screens.RecoveryScreen
import com.buhat.tracker.ui.theme.BuhatTheme
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BuhatApplication
        setContent {
            val generation by app.containerGeneration.collectAsState()
            val container = remember(generation) { app.container }
            val settings by remember(container) {
                container.settingsStore.settings
                    .map { it as com.buhat.tracker.data.Settings? }
                    .catch { emit(null) }
            }.collectAsState(initial = null)
            val startup by container.startupState.collectAsState()
            val recoveryError by app.recoveryError.collectAsState()
            val dark = settings?.theme != AppTheme.LIGHT
            LaunchedEffect(dark) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            BuhatTheme(darkTheme = dark) {
                when (val state = startup) {
                    is StartupState.DatabaseError -> RecoveryScreen(
                        reason = state.reason,
                        diagnosticsController = container.diagnosticsController,
                        onReset = { app.resetLocalData() },
                        onImport = { app.recoverWithBackup(it) },
                    )
                    is StartupState.Checking -> StartupLoading()
                    else -> {
                        BuhatApp(
                            repository = container.repository,
                            profileStore = container.profileStore,
                            restTimer = container.restTimer,
                            backupController = container.backupController,
                            diagnosticsController = container.diagnosticsController,
                        )
                        if (state is StartupState.SeedError) {
                            AlertDialog(
                                onDismissRequest = {},
                                title = { Text("Setup could not finish") },
                                text = { Text(state.message) },
                                confirmButton = {
                                    TextButton(
                                        onClick = { container.start() },
                                        modifier = Modifier.testTag("seed-retry"),
                                    ) { Text("Retry") }
                                },
                            )
                        }
                    }
                }
                recoveryError?.let { message ->
                    AlertDialog(
                        onDismissRequest = { app.dismissRecoveryError() },
                        title = { Text("Backup did not import") },
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = { app.dismissRecoveryError() }) { Text("Dismiss") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StartupLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("startup-loading"),
        contentAlignment = Alignment.Center,
    ) {
        Text("Loading…", color = MaterialTheme.colorScheme.onBackground)
    }
}
