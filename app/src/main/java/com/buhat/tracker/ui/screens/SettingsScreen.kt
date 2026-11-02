package com.buhat.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.ProfileStore
import com.buhat.tracker.data.Settings
import com.buhat.tracker.data.SettingsStore
import com.buhat.tracker.domain.REST_SECONDS_MAX
import com.buhat.tracker.domain.formatRestSeconds
import com.buhat.tracker.ui.components.BuhatCard
import com.buhat.tracker.ui.components.BuhatScreen
import com.buhat.tracker.ui.components.BuhatStepper
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    profileStore: ProfileStore,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val profile by remember(profileStore) { profileStore.profile }.collectAsState(null)
    val settings by remember(settingsStore) { settingsStore.settings }
        .collectAsState<Settings, Settings?>(null)
    val scope = rememberCoroutineScope()

    fun save(next: Settings) {
        scope.launch { settingsStore.save(next) }
    }

    BuhatScreen(title = "Settings", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("screen-Settings"),
        ) {
            BuhatCard(Modifier.clickable(onClick = onOpenProfile).testTag("settings-profile")) {
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

            val currentSettings = settings
            if (currentSettings == null) {
                Text(
                    text = "Loading settings…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("settings-loading"),
                )
            } else {
                BuhatCard(Modifier.testTag("settings-rest")) {
                    Text(
                        text = "Rest timer",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    RestRow(
                        label = "Warmup",
                        hint = "Default rest",
                        seconds = currentSettings.restWarmupSeconds,
                        tag = "rest-warmup",
                        increment = currentSettings.restIncrementSeconds,
                        onSecondsChange = { save(currentSettings.copy(restWarmupSeconds = it)) },
                    )
                    RestRow(
                        label = "Working",
                        hint = "Default rest",
                        seconds = currentSettings.restWorkingSeconds,
                        tag = "rest-working",
                        increment = currentSettings.restIncrementSeconds,
                        onSecondsChange = { save(currentSettings.copy(restWorkingSeconds = it)) },
                    )
                    RestRow(
                        label = "Failure",
                        hint = "Default rest",
                        seconds = currentSettings.restFailureSeconds,
                        tag = "rest-failure",
                        increment = currentSettings.restIncrementSeconds,
                        onSecondsChange = { save(currentSettings.copy(restFailureSeconds = it)) },
                    )
                    RestRow(
                        label = "Timer increment",
                        hint = "How much the + and − buttons add or remove",
                        seconds = currentSettings.restIncrementSeconds,
                        tag = "rest-increment",
                        increment = 5,
                        min = 5,
                        max = 60,
                        onSecondsChange = { save(currentSettings.copy(restIncrementSeconds = it)) },
                        format = { "${it.toInt()}s" },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

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
    Column(Modifier.padding(top = 12.dp)) {
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
        BuhatStepper(
            value = seconds.toDouble(),
            onValueChange = { onSecondsChange(it.toInt()) },
            step = increment.toDouble(),
            min = min.toDouble(),
            max = max.toDouble(),
            format = format,
            tagPrefix = tag,
        )
    }
}
