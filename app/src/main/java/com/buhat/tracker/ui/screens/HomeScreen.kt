package com.buhat.tracker.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.Profile
import com.buhat.tracker.data.ProfileStore
import com.buhat.tracker.data.local.ProfileStats
import com.buhat.tracker.domain.HistorySession
import com.buhat.tracker.domain.WeightUnit
import com.buhat.tracker.domain.e1rmTrend
import com.buhat.tracker.domain.fourWeekStats
import com.buhat.tracker.domain.formatWeight
import com.buhat.tracker.ui.components.BuhatCard
import com.buhat.tracker.ui.components.BuhatChoiceChip
import com.buhat.tracker.ui.components.BuhatEmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine

private data class HomeData(val profile: Profile, val unit: WeightUnit, val history: List<HistorySession>)

@Composable
fun HomeScreen(
    repository: BuhatRepository,
    profileStore: ProfileStore,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: (String?) -> Unit,
) {
    var attempt by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf(false) }
    val data by produceState<HomeData?>(null, repository, profileStore, attempt) {
        error = false
        try {
            combine(profileStore.profile, repository.settingsStore.settings, repository.history()) { profile, settings, history ->
                HomeData(profile, settings.unit, history)
            }.collect { value = it }
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
            .padding(horizontal = 24.dp),
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
        val history = loaded.history
        val stats = ProfileStats(history.size, history.minOfOrNull { it.finishedAt })
        val recent = history.maxByOrNull { it.finishedAt }?.exercises.orEmpty().take(4)
        val exercise = recent.firstOrNull { it.exerciseId == selectedExercise } ?: recent.firstOrNull()
        val now = System.currentTimeMillis()
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
                        text = trainingSummary(stats),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("settings-button"),
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
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onOpenProgress(exercise?.exerciseId) }, modifier = Modifier.testTag("view-progress")) {
                Text("View progress")
            }
        }
        Spacer(Modifier.height(16.dp))
        BuhatCard {
            Text("Strength trend · 3 months", style = MaterialTheme.typography.titleLarge)
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
                StrengthTrend(e1rmTrend(history, exercise.exerciseId, 90, now), exercise.exerciseNameSnapshot,
                    "3M", loaded.unit, "home")
            }
        }
        Spacer(Modifier.height(16.dp))
        val analytics = fourWeekStats(history, now)
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
