package com.buhat.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.SeedContent
import com.buhat.tracker.data.StrongMapping
import com.buhat.tracker.data.StrongPreview
import com.buhat.tracker.data.StrongSource
import com.buhat.tracker.data.readStrongCsvText
import com.buhat.tracker.data.local.Exercise
import com.buhat.tracker.domain.StrongCsvException
import com.buhat.tracker.ui.components.BuhatCard
import com.buhat.tracker.ui.components.BuhatChoiceChip
import com.buhat.tracker.ui.components.BuhatScreen
import com.buhat.tracker.ui.formatEpochDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StrongImportScreen(
    repository: BuhatRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<StrongPreview?>(null) }
    var content by remember { mutableStateOf<String?>(null) }
    var mappings by remember { mutableStateOf<Map<String, StrongMapping>>(emptyMap()) }
    var mappingSource by remember { mutableStateOf<StrongSource?>(null) }
    var skippedEmpty by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = busy) {}

    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        error = null
        result = null
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readStrongCsvText(context.contentResolver, uri) }
                val loaded = withContext(Dispatchers.IO) { repository.previewStrongImport(text) }
                content = text
                preview = loaded
                skippedEmpty = loaded.skippedEmptyWorkouts
                mappings = loaded.sources.associate { source ->
                    source.name to StrongMapping(
                        sourceName = source.name,
                        existingExerciseId = source.matchedExerciseId,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Could not read the CSV file. Try selecting it again."
                preview = null
                content = null
            } finally {
                busy = false
            }
        }
    }

    fun confirm() {
        val text = content ?: return
        busy = true
        error = null
        scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    repository.importStrong(text, mappings.values.toList())
                }
                result = "Imported ${imported.workouts} workouts and ${imported.sets} sets."
                val skipped = buildList {
                    if (imported.skippedDuplicates > 0) add("${imported.skippedDuplicates} already imported")
                    if (skippedEmpty > 0) add("$skippedEmpty with no working sets")
                }
                if (skipped.isNotEmpty()) result = "${result!!}\nSkipped ${skipped.joinToString(" and ")}."
                preview = null
                content = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Could not import the Strong export. Nothing was saved."
            } finally {
                busy = false
            }
        }
    }

    BuhatScreen(title = "Import from Strong", onBack = { if (!busy) onBack() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("screen-StrongImport"),
        ) {
            Text(
                text = "Pick a Strong CSV export. Nothing is written until you confirm.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    chooseFile.launch(
                        arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv"),
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("strong-choose-file"),
            ) {
                Text("Choose CSV")
            }
            if (busy) {
                Spacer(Modifier.height(8.dp))
                Text("Working…", modifier = Modifier.testTag("strong-working"))
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("strong-error").semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            result?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("strong-result").semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            preview?.let { loaded ->
                Spacer(Modifier.height(16.dp))
                BuhatCard(Modifier.testTag("strong-preview")) {
                    if (loaded.newWorkouts == 0) {
                        Text(
                            text = "No new workouts. All ${loaded.skippedDuplicates} workouts were already imported.",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            text = "New workouts: ${loaded.newWorkouts}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Skipped duplicates: ${loaded.skippedDuplicates}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (loaded.skippedEmptyWorkouts > 0) {
                            Text(
                                text = "Skipped with no working sets: ${loaded.skippedEmptyWorkouts}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        val first = loaded.firstStartedAt
                        val last = loaded.lastStartedAt
                        if (first != null && last != null) {
                            Text(
                                text = "${formatEpochDate(first)} to ${formatEpochDate(last)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = "Warmup sets: ${loaded.warmupSets}. Working sets: ${loaded.workingSets}. " +
                                "Failure sets: ${loaded.failureSets}.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Exercises",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        loaded.sources.forEach { source ->
                            val mapping = mappings[source.name]
                            val matchedName = loaded.exercises
                                .firstOrNull { it.id == mapping?.existingExerciseId }?.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) { mappingSource = source }
                                    .testTag("strong-source-${source.name}")
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = source.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = when {
                                        matchedName != null -> "Matched to $matchedName"
                                        mapping?.newExerciseName != null -> "New exercise: ${mapping.newExerciseName}"
                                        else -> "Needs mapping"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        val ready = loaded.sources.all { source ->
                            val mapping = mappings[source.name]
                            mapping != null && (
                                mapping.existingExerciseId != null ||
                                    (!mapping.newExerciseName.isNullOrBlank() &&
                                        !mapping.muscleGroup.isNullOrBlank() &&
                                        !mapping.equipment.isNullOrBlank())
                                )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { confirm() },
                            enabled = ready && !busy,
                            modifier = Modifier.fillMaxWidth().testTag("strong-confirm"),
                        ) {
                            Text("Import ${loaded.newWorkouts} workouts")
                        }
                    }
                }
            }
        }
    }

    mappingSource?.let { source ->
        preview?.let { loaded ->
            StrongMappingDialog(
                source = source,
                exercises = loaded.exercises,
                onPickExisting = { exercise ->
                    mappings = mappings + (source.name to StrongMapping(source.name, existingExerciseId = exercise.id))
                    mappingSource = null
                },
                onCreateNew = { mapping ->
                    mappings = mappings + (source.name to mapping)
                    mappingSource = null
                },
                onDismiss = { mappingSource = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrongMappingDialog(
    source: StrongSource,
    exercises: List<Exercise>,
    onPickExisting: (Exercise) -> Unit,
    onCreateNew: (StrongMapping) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember(source.name) { mutableStateOf(false) }
    var query by remember(source.name) { mutableStateOf("") }
    var name by remember(source.name) { mutableStateOf(source.name) }
    var muscleGroup by remember(source.name) { mutableStateOf<String?>(null) }
    var equipment by remember(source.name) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("strong-mapping-dialog"),
        title = { Text(source.name) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (!creating) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search exercises") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("strong-picker-search"),
                    )
                    Spacer(Modifier.height(8.dp))
                    exercises.filter { it.name.contains(query, ignoreCase = true) }.forEach { exercise ->
                        Text(
                            text = exercise.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickExisting(exercise) }
                                .testTag("strong-existing-${exercise.name}")
                                .padding(vertical = 12.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { creating = true },
                        modifier = Modifier.testTag("strong-create-new"),
                    ) {
                        Text("Create new exercise")
                    }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Exercise name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("strong-new-name"),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Muscle group", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeedContent.muscleGroups.forEach { option ->
                            BuhatChoiceChip(
                                label = option,
                                selected = option == muscleGroup,
                                modifier = Modifier.testTag("strong-muscle-$option"),
                                onClick = { muscleGroup = option },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Equipment", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeedContent.equipmentTypes.forEach { option ->
                            BuhatChoiceChip(
                                label = option,
                                selected = option == equipment,
                                modifier = Modifier.testTag("strong-equipment-$option"),
                                onClick = { equipment = option },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onCreateNew(
                                StrongMapping(
                                    sourceName = source.name,
                                    newExerciseName = name.trim(),
                                    muscleGroup = muscleGroup,
                                    equipment = equipment,
                                ),
                            )
                        },
                        enabled = name.isNotBlank() && muscleGroup != null && equipment != null,
                        modifier = Modifier.fillMaxWidth().testTag("strong-use-new"),
                    ) {
                        Text("Use new exercise")
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("strong-cancel")) {
                    Text("Cancel")
                }
            }
        },
        confirmButton = {},
    )
}
