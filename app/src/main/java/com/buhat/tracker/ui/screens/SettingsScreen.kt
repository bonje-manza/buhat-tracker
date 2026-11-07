package com.buhat.tracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.Settings
import com.buhat.tracker.domain.AppTheme
import com.buhat.tracker.domain.PreviousSetSource
import com.buhat.tracker.domain.REST_SECONDS_MAX
import com.buhat.tracker.domain.WeightUnit
import com.buhat.tracker.domain.formatRestSeconds
import com.buhat.tracker.ui.BackupController
import com.buhat.tracker.ui.components.BuhatCard
import com.buhat.tracker.ui.components.BuhatScreen
import com.buhat.tracker.ui.components.BuhatSegmentedControl
import com.buhat.tracker.ui.components.BuhatStepper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Composable
fun SettingsScreen(
    repository: BuhatRepository,
    backupController: BackupController,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val profileStore = repository.profileStore
    val settingsStore = repository.settingsStore
    var loadError by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    val profile by remember(profileStore, attempt) { profileStore.profile.catch { loadError = true } }.collectAsState(null)
    val settings by remember(settingsStore, attempt) {
        settingsStore.settings.catch { loadError = true }
    }.collectAsState<Settings, Settings?>(null, Dispatchers.Main.immediate)
    val hasActiveSession by remember(repository, attempt) {
        repository.activeSession().map { it != null }.catch { loadError = true }
    }.collectAsState<Boolean, Boolean?>(null)
    val context = LocalContext.current

    val busy = backupController.busy
    val message = backupController.message
    val pendingImport = backupController.pendingImport
    val importError = backupController.importError
    BackHandler(enabled = busy) {}

    fun update(change: (Settings) -> Settings) = backupController.updateSettings(change)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        backupController.export(context.contentResolver, uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        backupController.importFile(context.contentResolver, uri)
    }

    val currentSettings = settings
    BuhatScreen(title = "Settings", onBack = { if (!busy) onBack() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("screen-Settings"),
        ) {
            BuhatCard(Modifier.clickable(enabled = !busy, onClick = onOpenProfile).testTag("settings-profile")) {
                Text(
                    text = "Profile",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = profile?.name ?: "Set up your profile",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))

            if (loadError) {
                Text("Could not load settings.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { loadError = false; attempt++ }, modifier = Modifier.testTag("settings-retry")) {
                    Text("Retry")
                }
            } else if (currentSettings == null) {
                Text(
                    text = "Loading settings…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("settings-loading"),
                )
            } else {
                BuhatCard(Modifier.testTag("settings-appearance")) {
                    SectionTitle("Appearance")
                    BuhatSegmentedControl(
                        options = listOf("Dark", "Light"),
                        selectedIndex = if (currentSettings.theme == AppTheme.DARK) 0 else 1,
                        onSelect = { index ->
                            update { it.copy(theme = if (index == 0) AppTheme.DARK else AppTheme.LIGHT) }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))

                BuhatCard(Modifier.testTag("settings-units")) {
                    SectionTitle("Units")
                    Text(
                        text = "Weight steps are 2.5 kg. Example: 135 lb shows as 61.2 kg.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    BuhatSegmentedControl(
                        options = listOf("kg", "lb"),
                        selectedIndex = WeightUnit.entries.indexOf(currentSettings.unit),
                        onSelect = { index ->
                            update { it.copy(unit = WeightUnit.entries[index]) }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))

                BuhatCard(Modifier.testTag("settings-previous-set")) {
                    SectionTitle("Previous set")
                    Text(
                        text = "The same set number from the most recent session.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    BuhatSegmentedControl(
                        options = listOf("Any workout", "Same template"),
                        selectedIndex = PreviousSetSource.entries.indexOf(currentSettings.previousSetSource),
                        onSelect = { index ->
                            update { it.copy(previousSetSource = PreviousSetSource.entries[index]) }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))

                BuhatCard(Modifier.testTag("settings-rest")) {
                    SectionTitle("Rest timer")
                    RestRow(
                        label = "Warmup",
                        hint = "Default rest",
                        seconds = currentSettings.restWarmupSeconds,
                        tag = "rest-warmup",
                        increment = currentSettings.restIncrementSeconds,
                        onSecondsChange = { seconds -> update { it.copy(restWarmupSeconds = seconds) } },
                    )
                    RestRow(
                        label = "Working",
                        hint = "Default rest",
                        seconds = currentSettings.restWorkingSeconds,
                        tag = "rest-working",
                        increment = currentSettings.restIncrementSeconds,
                        onSecondsChange = { seconds -> update { it.copy(restWorkingSeconds = seconds) } },
                    )
                    RestRow(
                        label = "Failure",
                        hint = "Default rest",
                        seconds = currentSettings.restFailureSeconds,
                        tag = "rest-failure",
                        increment = currentSettings.restIncrementSeconds,
                        onSecondsChange = { seconds -> update { it.copy(restFailureSeconds = seconds) } },
                    )
                    RestRow(
                        label = "Timer increment",
                        hint = "How much the + and − buttons add or remove",
                        seconds = currentSettings.restIncrementSeconds,
                        tag = "rest-increment",
                        increment = 5,
                        min = 5,
                        max = 60,
                        onSecondsChange = { seconds -> update { it.copy(restIncrementSeconds = seconds) } },
                        format = { "${it.toInt()}s" },
                    )
                }
                Spacer(Modifier.height(16.dp))

                BuhatCard(Modifier.testTag("settings-data")) {
                    SectionTitle("Data")
                    Text(
                        text = "Backups are plain JSON and are not encrypted.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (hasActiveSession == true) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Finish or discard the workout first.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("settings-active-hint"),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { exportLauncher.launch("buhat-backup-${LocalDate.now()}.json") },
                        enabled = hasActiveSession == false && !busy,
                        modifier = Modifier.fillMaxWidth().testTag("export-data"),
                    ) {
                        Text("Export data")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        enabled = hasActiveSession == false && !busy,
                        modifier = Modifier.fillMaxWidth().testTag("import-data"),
                    ) {
                        Text("Import data")
                    }
                    if (busy) Text("Working…", modifier = Modifier.testTag("backup-busy"))
                    message?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("settings-message").semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    pendingImport?.let { (_, summary) ->
        AlertDialog(
            onDismissRequest = { backupController.cancelImport() },
            title = { Text("Replace all local data?") },
            text = {
                Column {
                    Text("Exported ${formatExportDate(summary.exportedAt)}")
                    Text(
                        "${summary.exercises} exercises, ${summary.templates} templates, " +
                            "${summary.sessions} workouts, ${summary.sets} sets.",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("This replaces all local data. The file is not encrypted.")
                    if (busy) Text("Importing…")
                    importError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("import-error").semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    modifier = Modifier.testTag("confirm-import"),
                    onClick = { backupController.confirmImport() },
                ) {
                    Text("Replace all local data")
                }
            },
            dismissButton = {
                TextButton(onClick = { backupController.cancelImport() }, enabled = !busy) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
    )
}

private fun formatExportDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

@Composable
private fun RestRow(
    label: String,
    hint: String,
    seconds: Int,
    tag: String,
    increment: Int,
    onSecondsChange: (Int) -> Unit,
    min: Int = 0,
    max: Int = REST_SECONDS_MAX,
    format: (Double) -> String = { formatRestSeconds(it.toInt()) },
) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        BuhatStepper(
            value = seconds.toDouble(),
            onValueChange = { onSecondsChange(it.toInt()) },
            step = increment.toDouble(),
            min = min.toDouble(),
            max = max.toDouble(),
            format = format,
            tagPrefix = tag,
            modifier = Modifier.weight(1.5f),
        )
    }
}
