package com.buhat.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.local.Exercise
import com.buhat.tracker.domain.ContentValidationException
import com.buhat.tracker.domain.TemplateExerciseDraft
import com.buhat.tracker.ui.components.BuhatCard
import com.buhat.tracker.ui.components.BuhatScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DraftRow(
    val exerciseId: String,
    val exerciseName: String,
    val sets: String,
    val repMin: String,
    val repMax: String,
    val rir: String,
)

@Composable
fun TemplateEditorScreen(
    repository: BuhatRepository,
    templateId: String?,
    onBack: () -> Unit,
) {
    val isNew = templateId == null || templateId == "new"
    val library by remember(repository) { repository.exercises() }
        .collectAsState(emptyList())
    var name by rememberSaveable { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<DraftRow>>(emptyList()) }
    var loading by remember { mutableStateOf(!isNew) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(templateId) {
        if (!isNew) {
            repository.loadTemplateDraft(templateId!!)?.let { draft ->
                name = draft.name
                rows = draft.exercises.map {
                    DraftRow(
                        exerciseId = it.exerciseId,
                        exerciseName = it.exerciseName,
                        sets = it.workingSets.toString(),
                        repMin = it.repMin.toString(),
                        repMax = it.repMax.toString(),
                        rir = it.targetRir?.toString().orEmpty(),
                    )
                }
            }
            loading = false
        }
    }

    if (pickerOpen) {
        ExercisePicker(
            exercises = library.filter { exercise -> rows.none { it.exerciseId == exercise.id } },
            query = query,
            onQueryChange = { query = it },
            onPick = { exercise ->
                rows = rows + DraftRow(exercise.id, exercise.name, "3", "8", "12", "")
                pickerOpen = false
                query = ""
            },
            onCancel = {
                pickerOpen = false
                query = ""
            },
        )
        return
    }

    BuhatScreen(title = if (isNew) "New template" else "Edit template", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("screen-TemplateEditor"),
        ) {
            if (loading) {
                Text("Loading...", modifier = Modifier.padding(24.dp))
                return@Column
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Template name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("template-name"),
            )
            Spacer(Modifier.height(16.dp))
            rows.forEachIndexed { index, row ->
                DraftRowCard(
                    row = row,
                    canMoveUp = index > 0,
                    canMoveDown = index < rows.lastIndex,
                    onChange = { updated ->
                        rows = rows.map { if (it.exerciseId == row.exerciseId) updated else it }
                    },
                    onRemove = { rows = rows.filterNot { it.exerciseId == row.exerciseId } },
                    onUp = { rows = rows.swapped(index, index - 1) },
                    onDown = { rows = rows.swapped(index, index + 1) },
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedButton(
                onClick = { pickerOpen = true },
                modifier = Modifier.fillMaxWidth().testTag("add-exercise"),
            ) {
                Text("Add exercise")
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("error-message"),
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    error = null
                    val drafts = rows.map {
                        TemplateExerciseDraft(
                            exerciseId = it.exerciseId,
                            exerciseName = it.exerciseName,
                            workingSets = it.sets.toIntOrNull() ?: -1,
                            repMin = it.repMin.toIntOrNull() ?: -1,
                            repMax = it.repMax.toIntOrNull() ?: -1,
                            targetRir = if (it.rir.isBlank()) null else (it.rir.toIntOrNull() ?: -1),
                        )
                    }
                    scope.launch {
                        try {
                            repository.saveTemplate(if (isNew) null else templateId, name, drafts)
                            withContext(Dispatchers.Main) { onBack() }
                        } catch (e: ContentValidationException) {
                            error = e.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("save-template"),
            ) {
                Text("Save template")
            }
        }
    }
}

@Composable
private fun DraftRowCard(
    row: DraftRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (DraftRow) -> Unit,
    onRemove: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    BuhatCard(Modifier.testTag("draft-${row.exerciseName}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.exerciseName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onUp,
                enabled = canMoveUp,
                modifier = Modifier.testTag("up-${row.exerciseName}"),
            ) {
                Text("↑")
            }
            TextButton(
                onClick = onDown,
                enabled = canMoveDown,
                modifier = Modifier.testTag("down-${row.exerciseName}"),
            ) {
                Text("↓")
            }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.testTag("remove-${row.exerciseName}"),
            ) {
                Text("×")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Sets", row.sets, "sets-${row.exerciseName}", Modifier.weight(1f)) {
                onChange(row.copy(sets = it))
            }
            NumberField("Min", row.repMin, "repmin-${row.exerciseName}", Modifier.weight(1f)) {
                onChange(row.copy(repMin = it))
            }
            NumberField("Max", row.repMax, "repmax-${row.exerciseName}", Modifier.weight(1f)) {
                onChange(row.copy(repMax = it))
            }
            NumberField("RIR", row.rir, "rir-${row.exerciseName}", Modifier.weight(1f)) {
                onChange(row.copy(rir = it))
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.length <= 3 && new.all { it.isDigit() }) onChange(new) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.testTag(tag),
    )
}

@Composable
private fun ExercisePicker(
    exercises: List<Exercise>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (Exercise) -> Unit,
    onCancel: () -> Unit,
) {
    BuhatScreen(title = "Add exercise", onBack = onCancel) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search exercises") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("picker-search"),
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            exercises.filter { it.name.contains(query, ignoreCase = true) }.forEach { exercise ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(exercise) }
                        .testTag("picker-${exercise.name}")
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = exercise.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${exercise.muscleGroup} · ${exercise.equipment}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun <T> List<T>.swapped(from: Int, to: Int): List<T> =
    toMutableList().also {
        val item = it[from]
        it[from] = it[to]
        it[to] = item
    }
