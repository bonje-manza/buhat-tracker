package com.buhat.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.local.Exercise
import com.buhat.tracker.domain.HistoryExercise
import com.buhat.tracker.domain.HistorySession
import com.buhat.tracker.domain.LoggedSetRecord
import com.buhat.tracker.domain.TrendPoint
import com.buhat.tracker.domain.WeightUnit
import com.buhat.tracker.domain.WindowStat
import com.buhat.tracker.domain.countsForProgression
import com.buhat.tracker.domain.e1rm
import com.buhat.tracker.domain.e1rmTrend
import com.buhat.tracker.domain.exerciseStats
import com.buhat.tracker.domain.formatWeight
import com.buhat.tracker.domain.kgToLb
import com.buhat.tracker.domain.prSetIds
import com.buhat.tracker.ui.components.BuhatCard
import com.buhat.tracker.ui.components.BuhatEmptyState
import com.buhat.tracker.ui.components.BuhatLineChart
import com.buhat.tracker.ui.components.BuhatSegmentedControl
import com.buhat.tracker.ui.components.PrBadge
import com.buhat.tracker.ui.components.SetTypeBadge
import com.buhat.tracker.ui.theme.BuhatGreen
import com.buhat.tracker.ui.theme.BuhatLightGreen
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private data class ProgressData(val history: List<HistorySession>, val exercises: List<Exercise>, val unit: WeightUnit)

@Composable
fun ProgressScreen(
    repository: BuhatRepository,
    selectedExerciseId: String?,
    onSelectExercise: (String) -> Unit,
    rangeIndex: Int,
    onSelectRange: (Int) -> Unit,
) {
    var attempt by remember { mutableStateOf(0) }
    var loadError by remember { mutableStateOf(false) }
    val data by produceState<ProgressData?>(null, repository, attempt) {
        loadError = false
        try {
            combine(repository.history(), repository.exercises(), repository.settingsStore.settings) { history, exercises, settings ->
                ProgressData(history, exercises, settings.unit)
            }.collect { value = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadError = true
        }
    }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedId by rememberSaveable(selectedExerciseId) { mutableStateOf<String?>(null) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val loaded = data
    val history = loaded?.history.orEmpty()
    // Snapshots keep archived/deleted exercises selectable without consulting mutable library names.
    val choices = remember(history, loaded?.exercises) {
        (history.asReversed().flatMap { it.exercises }.map { it.exerciseId to it.exerciseNameSnapshot } +
            loaded?.exercises.orEmpty().map { it.id to it.name }).distinctBy { it.first }
    }
    val selected = choices.firstOrNull { it.first == selectedExerciseId } ?: choices.firstOrNull()
    val exerciseId = selected?.first.orEmpty()
    val unit = loaded?.unit ?: WeightUnit.KG
    val ranges = listOf("1M", "3M", "1Y", "All")
    val rangeDays = listOf(30, 90, 365, null)[rangeIndex]
    val now = System.currentTimeMillis()
    val summary = remember(history, exerciseId) { exerciseStats(history, exerciseId) }
    val prs = remember(history) { prSetIds(history) }
    val workouts = remember(history, exerciseId) {
        history.asReversed().filter { session -> session.exercises.any { it.exerciseId == exerciseId && it.sets.isNotEmpty() } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen-Progress"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Strength trends", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text("Progress", style = MaterialTheme.typography.headlineLarge)
        }
        if (loadError) {
            item {
                Text("Could not load progress.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempt++ }, modifier = Modifier.testTag("progress-retry")) { Text("Retry") }
            }
        } else if (loaded == null) {
            item { Text("Loading...", modifier = Modifier.testTag("progress-loading")) }
        } else {
            item {
                OutlinedButton(
                    onClick = { pickerOpen = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progress-exercise-picker"),
                ) { Text(selected?.second ?: "Choose exercise", maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            item {
                BuhatCard {
                    Text("Estimated 1RM", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StrengthTrend(e1rmTrend(history, exerciseId, rangeDays, now), selected?.second.orEmpty(), ranges[rangeIndex], unit, "progress") {
                        BuhatSegmentedControl(ranges, rangeIndex, onSelectRange)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Best e1RM", summary.bestE1rm?.let { analyticsWeight(it, unit) } ?: "—", "progress-best", Modifier.weight(1f))
                    StatTile("Heaviest set", summary.heaviestSet?.let { "${formatWeight(it.weightKg, unit)} × ${it.reps}" } ?: "—", "progress-heaviest", Modifier.weight(1f))
                    StatTile("Sessions", summary.sessionCount.toString(), "progress-sessions", Modifier.weight(1f))
                }
            }
            item {
                Text("Past workouts", style = MaterialTheme.typography.titleLarge)
                Text("All time · tap to expand", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (workouts.isEmpty()) {
                item { BuhatEmptyState("No workouts for this exercise", "Log it in a finished workout.", Modifier.testTag("progress-empty")) }
            }
            items(workouts, key = { it.id }) { workout ->
                val exercise = workout.exercises.first { it.exerciseId == exerciseId }
                val expanded = expandedId == workout.id
                WorkoutHistoryCard(workout, exercise, unit, prs, expanded,
                    onToggle = { expandedId = if (expanded) null else workout.id },
                    onDelete = { pendingDeleteId = workout.id; deleteError = null })
            }
        }
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Choose exercise") },
            text = {
                Column {
                    OutlinedTextField(query, { query = it }, label = { Text("Search exercises") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("progress-exercise-search"))
                    val matches = choices.filter { it.second.contains(query, ignoreCase = true) }.sortedBy { it.second }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        if (matches.isEmpty()) item { Text("No matching exercises", Modifier.padding(16.dp)) }
                        items(matches, key = { it.first }) { (id, name) ->
                            TextButton(onClick = { onSelectExercise(id); pickerOpen = false; query = "" },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("progress-pick-$id")) {
                                Text(name)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickerOpen = false }, modifier = Modifier.testTag("progress-picker-close")) { Text("Close") } },
        )
    }
    val pending = history.firstOrNull { it.id == pendingDeleteId }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDeleteId = null },
            title = { Text("Delete workout?") },
            text = {
                Column {
                    Text("Delete ${pending.templateNameSnapshot} and all its sets? This cannot be undone.")
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("history-delete-error")) }
                }
            },
            confirmButton = {
                TextButton(enabled = !deleting, modifier = Modifier.testTag("confirm-delete-workout"), onClick = {
                    deleting = true
                    deleteError = null
                    scope.launch {
                        try {
                            repository.deleteFinishedSession(pending.id)
                            pendingDeleteId = null
                            expandedId = null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            deleteError = "Could not delete workout. Try again."
                        } finally {
                            deleting = false
                        }
                    }
                }) { Text(if (deleting) "Deleting..." else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }, enabled = !deleting, modifier = Modifier.testTag("cancel-delete-workout")) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WorkoutHistoryCard(
    workout: HistorySession,
    exercise: HistoryExercise,
    unit: WeightUnit,
    prs: Set<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val working = exercise.sets.filter { it.setType.countsForProgression }
    val best = working.filter { e1rm(it.weightKg, it.reps) != null }.maxByOrNull { e1rm(it.weightKg, it.reps)!! }
        ?: working.maxWithOrNull(compareBy<LoggedSetRecord> { it.weightKg }.thenBy { it.reps })
    BuhatCard {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .testTag("history-${workout.id}")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(best?.let { "${formatWeight(it.weightKg, unit)} × ${it.reps}" } ?: "Warmup sets only",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (exercise.sets.any { it.id in prs }) PrBadge(Modifier.testTag("history-pr-${workout.id}"))
            }
            Text("${workoutDate(workout.finishedAt)} · ${workout.templateNameSnapshot}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            Text("e1RM ${best?.let { e1rm(it.weightKg, it.reps) }?.let { analyticsWeight(it, unit) } ?: "—"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            exercise.sets.forEach { set ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}
                    .testTag("history-set-${set.id}"), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SetTypeBadge(set.setType)
                    Column(Modifier.weight(1f)) {
                        Text("${formatWeight(set.weightKg, unit)} × ${set.reps}")
                        set.rir?.let { Text("RIR $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (set.id in prs) PrBadge()
                }
            }
            TextButton(onClick = onDelete, modifier = Modifier.testTag("delete-workout-${workout.id}")) { Text("Delete workout") }
        }
    }
}

@Composable
internal fun StrengthTrend(
    points: List<TrendPoint>,
    exerciseName: String,
    rangeLabel: String,
    unit: WeightUnit,
    tagPrefix: String,
    controls: @Composable () -> Unit = {},
) {
    val first = points.firstOrNull()
    val last = points.lastOrNull()
    val change = if (first != null && last != null) last.e1rm - first.e1rm else 0.0
    val changeText = "${if (change > 0) "+" else ""}${analyticsWeight(change, unit)} (${signedPercent(if (first != null) change / first.e1rm * 100 else 0.0)})"
    if (last != null) {
        Text(analyticsWeight(last.e1rm, unit), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("$tagPrefix-trend-latest"))
        Text(changeText, color = changeColor(change),
            modifier = Modifier.testTag("$tagPrefix-trend-change"))
    }
    Spacer(Modifier.height(12.dp))
    controls()
    if (first == null || last == null) {
        BuhatEmptyState("No data in range", "Finish a working or failure set with weight and reps to see estimated 1RM.",
            Modifier.testTag("$tagPrefix-trend-empty"))
    } else {
        val summary = "$exerciseName, $rangeLabel: ${points.size} ${if (points.size == 1) "session" else "sessions"}. " +
            "First ${analyticsWeight(first.e1rm, unit)}, latest ${analyticsWeight(last.e1rm, unit)}. Change $changeText."
        Spacer(Modifier.height(12.dp))
        BuhatLineChart(points.map { if (unit == WeightUnit.LB) kgToLb(it.e1rm) else it.e1rm }, summary,
            Modifier.height(144.dp).testTag("$tagPrefix-chart"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(workoutDate(first.atMillis), style = MaterialTheme.typography.bodySmall)
            if (first != last) Text(workoutDate(last.atMillis), style = MaterialTheme.typography.bodySmall)
        }
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StatTile(label: String, value: String, tag: String, modifier: Modifier, stat: WindowStat? = null) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("$tag-value"))
        if (stat != null) {
            Text(stat.deltaPercent?.let(::signedPercent) ?: "vs prior 4", style = MaterialTheme.typography.bodyMedium,
                color = changeColor(stat.deltaPercent ?: 0.0), modifier = Modifier.testTag("$tag-delta"))
        }
    }
}

internal fun analyticsWeight(kg: Double, unit: WeightUnit): String =
    formatWeight(if (unit == WeightUnit.KG) BigDecimal.valueOf(kg).setScale(1, RoundingMode.HALF_UP).toDouble() else kg, unit)

private fun signedPercent(value: Double): String =
    "${if (value > 0) "+" else ""}${BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}%"

@Composable
private fun changeColor(value: Double): Color = when {
    value <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    MaterialTheme.colorScheme.surface.luminance() > 0.5f -> BuhatLightGreen
    else -> BuhatGreen
}

private fun workoutDate(at: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(at))
