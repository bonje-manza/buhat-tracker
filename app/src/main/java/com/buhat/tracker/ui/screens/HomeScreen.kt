package com.buhat.tracker.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.HomeDashboard
import com.buhat.tracker.data.Profile
import com.buhat.tracker.data.ProfileStore
import com.buhat.tracker.data.local.ProfileStats
import com.buhat.tracker.domain.BACKUP_SNOOZE_DAYS
import com.buhat.tracker.domain.DAY_MILLIS
import com.buhat.tracker.domain.HOME_WINDOW_DAYS
import com.buhat.tracker.domain.WeightUnit
import com.buhat.tracker.domain.e1rmTrend
import com.buhat.tracker.domain.fourWeekStats
import com.buhat.tracker.domain.formatWeight
import com.buhat.tracker.domain.homeAnalyticsNow
import com.buhat.tracker.domain.shouldRemindBackup
import com.buhat.tracker.ui.BackupController
import com.buhat.tracker.ui.backupFileName
import com.buhat.tracker.ui.components.BuhatCard
import com.buhat.tracker.ui.components.BuhatChoiceChip
import com.buhat.tracker.ui.components.BuhatEmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class HomeData(
    val profile: Profile,
    val unit: WeightUnit,
    val dashboard: HomeDashboard,
    val now: Long,
)

private data class BackupStatus(
    val loaded: Boolean = false,
    val lastExportAt: Long? = null,
    val snoozedUntil: Long? = null,
)

@Composable
fun HomeScreen(
    repository: BuhatRepository,
    profileStore: ProfileStore,
    backupController: BackupController,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: (String?) -> Unit,
) {
    var attempt by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf(false) }
    val backupStatus by remember(repository) {
        combine(
            repository.settingsStore.lastExportAt,
            repository.settingsStore.backupSnoozedUntil,
        ) { last, snooze -> BackupStatus(loaded = true, lastExportAt = last, snoozedUntil = snooze) }
            .catch { emit(BackupStatus(loaded = true)) }
    }.collectAsState(initial = BackupStatus())
    val hasActiveSession by remember(repository) {
        repository.activeSession().map { it != null }.catch { emit(false) }
    }.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { backupController.export(context.contentResolver, it) }
    }
    @SuppressLint("ProduceStateDoesNotAssignValue") // Lint cannot see the assignment inside collect.
    val data by produceState<HomeData?>(null, repository, profileStore, attempt) {
        error = false
        try {
            // One clock reading per collection keeps the query cutoffs and the analytics windows aligned.
            val now = System.currentTimeMillis()
            combine(
                profileStore.profile,
                repository.settingsStore.settings,
                repository.homeDashboard(now),
            ) { profile, settings, dashboard -> HomeData(profile, settings.unit, dashboard, now) }
                .collect { value = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = true
        }
    }
    var selectedExercise by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("screen-Home")
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        if (error) {
            Text("Could not load dashboard.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { attempt++ }, modifier = Modifier.testTag("home-retry")) { Text("Retry") }
            return@Column
        }
        val loaded = data
        if (loaded == null) {
            Text("Loading...", modifier = Modifier.testTag("home-loading"))
            return@Column
        }
        val profile = loaded.profile
        val dashboard = loaded.dashboard
        val recent = dashboard.latest?.exercises.orEmpty().take(4)
        val exercise = recent.firstOrNull { it.exerciseId == selectedExercise }
            ?: recent.firstOrNull { it.sets.isNotEmpty() }
            ?: recent.firstOrNull()
        // A session can finish after the collection clock snapshot, so the windows reach just past it.
        val now = homeAnalyticsNow(loaded.now, dashboard.window.maxOfOrNull { it.finishedAt })
        BuhatCard(Modifier.testTag("profile-card").clickable(onClick = onOpenProfile)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = profile.name?.trim()?.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.onSecondary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.name ?: "Set up profile",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag("profile-card-name"),
                    )
                    Text(
                        text = trainingSummary(dashboard.profileStats),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilledTonalButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("settings-button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("Settings")
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileTile(
                    "Gender / age", profile.gender ?: "—", Modifier.weight(1f),
                    profile.age?.let { "$it years" } ?: "Age —",
                )
                ProfileTile(
                    "Weight",
                    profile.weightKg?.let { formatWeight(it, loaded.unit) } ?: "—",
                    Modifier.weight(1f),
                )
                ProfileTile("Height", profile.heightCm?.let { "$it cm" } ?: "—", Modifier.weight(1f))
            }
        }
        if (backupStatus.loaded &&
            shouldRemindBackup(
                backupStatus.lastExportAt,
                backupStatus.snoozedUntil,
                loaded.now,
                dashboard.profileStats.totalWorkouts > 0,
            )
        ) {
            Spacer(Modifier.height(16.dp))
            BackupReminderCard(
                lastExportAt = backupStatus.lastExportAt,
                now = loaded.now,
                active = hasActiveSession == true,
                busy = backupController.busy,
                onExport = { exportLauncher.launch(backupFileName()) },
                onSnooze = {
                    scope.launch {
                        runCatching {
                            repository.settingsStore.snoozeBackupReminder(
                                System.currentTimeMillis() + BACKUP_SNOOZE_DAYS * DAY_MILLIS,
                            )
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onOpenProgress(exercise?.exerciseId) }, modifier = Modifier.testTag("view-progress")) {
                Text("View progress")
            }
        }
        Spacer(Modifier.height(16.dp))
        BuhatCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Strength trend · 3 months",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Est. 1RM",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (exercise == null) {
                BuhatEmptyState("No strength history", "Finish a workout to see your progress.", Modifier.testTag("home-empty"))
            } else {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { item ->
                        BuhatChoiceChip(item.exerciseNameSnapshot, item.exerciseId == exercise.exerciseId,
                            Modifier.testTag("home-exercise-${item.exerciseId}")) { selectedExercise = item.exerciseId }
                    }
                }
                Spacer(Modifier.height(16.dp))
                StrengthTrend(e1rmTrend(dashboard.window, exercise.exerciseId, HOME_WINDOW_DAYS, now),
                    exercise.exerciseNameSnapshot, "3M", loaded.unit, "home")
            }
        }
        Spacer(Modifier.height(16.dp))
        val analytics = fourWeekStats(dashboard.window, now, dashboard.bestBefore)
        BuhatCard {
            Text("Analytics · last 4 weeks", style = MaterialTheme.typography.titleLarge)
            Text("vs prior 4 weeks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Workouts", analytics.workouts.current.toInt().toString(), "home-workouts", Modifier.weight(1f), analytics.workouts)
                StatTile("Working sets", analytics.workingSets.current.toInt().toString(), "home-working-sets", Modifier.weight(1f), analytics.workingSets)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Volume", analyticsWeight(analytics.volumeKg.current, loaded.unit), "home-volume", Modifier.weight(1f), analytics.volumeKg)
                StatTile("New PRs", analytics.newPrs.current.toInt().toString(), "home-prs", Modifier.weight(1f), analytics.newPrs)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun trainingSummary(stats: ProfileStats): String {
    val first = stats.firstFinishedAt ?: return "No workouts yet"
    val month = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(first))
    val workouts = if (stats.totalWorkouts == 1) "1 workout" else "${stats.totalWorkouts} workouts"
    return "Training since $month · $workouts"
}

@Composable
private fun ProfileTile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun BackupReminderCard(
    lastExportAt: Long?,
    now: Long,
    active: Boolean,
    busy: Boolean,
    onExport: () -> Unit,
    onSnooze: () -> Unit,
) {
    BuhatCard(Modifier.testTag("backup-reminder")) {
        Text(
            text = "Back up your data",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (lastExportAt == null) {
                "No backup yet. Your data is only on this phone."
            } else {
                val days = ((now - lastExportAt) / DAY_MILLIS).toInt().coerceAtLeast(0)
                if (days == 1) "Last backup was 1 day ago." else "Last backup was $days days ago."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (active) {
            Text(
                text = "Finish or discard the workout first.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onExport,
                enabled = !active && !busy,
                modifier = Modifier.weight(1f).testTag("backup-reminder-export"),
            ) { Text("Export now") }
            TextButton(
                onClick = onSnooze,
                modifier = Modifier.testTag("backup-reminder-snooze"),
            ) { Text("Remind me in 7 days") }
        }
    }
}
