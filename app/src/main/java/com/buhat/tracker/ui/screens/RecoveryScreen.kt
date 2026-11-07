package com.buhat.tracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.buhat.tracker.DatabaseFailure
import com.buhat.tracker.data.BackupSummary
import com.buhat.tracker.data.decodeBackup
import com.buhat.tracker.data.readBackupText
import com.buhat.tracker.data.summary
import com.buhat.tracker.diagnostics.DiagnosticsController
import com.buhat.tracker.ui.components.BuhatCard
import com.buhat.tracker.ui.diagnosticsFileName
import com.buhat.tracker.ui.formatEpochDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecoveryScreen(
    reason: DatabaseFailure,
    diagnosticsController: DiagnosticsController,
    onReset: () -> Unit,
    onImport: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<Pair<String, BackupSummary>?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val diagnosticsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri?.let { diagnosticsController.export(context.contentResolver, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val (text, summary) = withContext(Dispatchers.IO) {
                    val text = readBackupText(context.contentResolver, uri)
                    text to decodeBackup(text).summary()
                }
                pendingImport = text to summary
                importError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                importError = e.message ?: "Could not read the backup file."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("recovery-screen"),
    ) {
        Text(
            text = "Buhat could not open its data",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = when (reason) {
                DatabaseFailure.DOWNGRADE ->
                    "This data was created by a newer version of Buhat. Install the newer version again, or export diagnostics, reset local data, and import a backup."
                DatabaseFailure.CORRUPT_OR_UNREADABLE ->
                    "The local database is unreadable."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        BuhatCard {
            Text(
                text = "Recover your data",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "A valid backup can be imported after a reset. Reset deletes the local database and settings. The diagnostics log is kept.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth().testTag("recovery-import"),
            ) { Text("Import backup") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth().testTag("recovery-reset"),
            ) { Text("Reset local data") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { diagnosticsLauncher.launch(diagnosticsFileName()) },
                modifier = Modifier.fillMaxWidth().testTag("recovery-export"),
            ) { Text("Export diagnostics") }
            importError?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("recovery-error"),
                )
            }
            diagnosticsController.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    pendingImport?.let { (text, summary) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace local data with this backup?") },
            text = {
                Column {
                    Text("Exported ${formatEpochDate(summary.exportedAt)}")
                    Text(
                        "${summary.exercises} exercises, ${summary.templates} templates, " +
                            "${summary.sessions} workouts, ${summary.sets} sets.",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("The database is reset first. Keep the backup file until the import succeeds.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { pendingImport = null; onImport(text) },
                    modifier = Modifier.testTag("recovery-confirm-import"),
                ) { Text("Reset and import") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset local data?") },
            text = {
                Text(
                    "This deletes all workouts, templates, profile, and settings. " +
                        "The diagnostics log is kept. Export diagnostics first if you need the failure record.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmReset = false; onReset() },
                    modifier = Modifier.testTag("recovery-confirm-reset"),
                ) { Text("Reset local data") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
