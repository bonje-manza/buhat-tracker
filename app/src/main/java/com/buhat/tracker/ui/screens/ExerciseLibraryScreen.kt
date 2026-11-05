package com.buhat.tracker.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.SeedContent
import com.buhat.tracker.data.local.Exercise
import com.buhat.tracker.ui.components.BuhatChoiceChip
import com.buhat.tracker.ui.components.BuhatEmptyState
import com.buhat.tracker.ui.components.BuhatPill
import com.buhat.tracker.ui.components.BuhatScreen
import kotlinx.coroutines.launch

@Composable
fun ExerciseLibraryScreen(
    repository: BuhatRepository,
    onBack: () -> Unit,
    onNewExercise: () -> Unit,
    onEditExercise: (String) -> Unit,
) {
    val exercises by remember(repository) { repository.exercises() }
        .collectAsState(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("All") }
    var openMenuId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Exercise?>(null) }
    var referenced by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val filtered = exercises.filter {
        (group == "All" || it.muscleGroup == group) && it.name.contains(query, ignoreCase = true)
    }

    BuhatScreen(
        title = "Exercise library",
        onBack = onBack,
        action = {
            Button(onClick = onNewExercise, modifier = Modifier.testTag("new-exercise")) {
                Text("+ New")
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("screen-Library"),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search exercises") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("library-search"),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf("All") + SeedContent.muscleGroups).forEach { option ->
                    BuhatChoiceChip(
                        label = option,
                        selected = option == group,
                        modifier = Modifier.testTag("group-$option"),
                        onClick = { group = option },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                BuhatEmptyState("No exercises", "Try another search or create one.")
            }
            filtered.forEach { exercise ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .testTag("exercise-row-${exercise.name}"),
                    ) {
                        Text(
                            text = exercise.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${exercise.muscleGroup} · ${exercise.equipment}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (exercise.isCustom) {
                                Spacer(Modifier.width(8.dp))
                                BuhatPill(
                                    label = "Custom",
                                    modifier = Modifier.testTag("custom-badge-${exercise.name}"),
                                )
                            }
                        }
                    }
                    if (exercise.isCustom) {
                        Box {
                            TextButton(
                                onClick = { openMenuId = exercise.id },
                                modifier = Modifier.testTag("exercise-menu-${exercise.name}")
                                    .semantics { contentDescription = "Exercise options" },
                            ) {
                                Text("⋯")
                            }
                            DropdownMenu(
                                expanded = openMenuId == exercise.id,
                                onDismissRequest = { openMenuId = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit exercise") },
                                    onClick = {
                                        openMenuId = null
                                        onEditExercise(exercise.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Archive exercise") },
                                    onClick = {
                                        openMenuId = null
                                        scope.launch { repository.archiveExercise(exercise.id) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete exercise") },
                                    onClick = {
                                        openMenuId = null
                                        scope.launch {
                                            referenced = repository.templateUsesFor(exercise.id) > 0
                                            pendingDelete = exercise
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { exercise ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (referenced) "Used in a template" else "Delete exercise?") },
            text = {
                Text(
                    if (referenced) {
                        "${exercise.name} is used in a template. Archive it instead?"
                    } else {
                        "${exercise.name} will be removed."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (referenced) {
                                repository.archiveExercise(exercise.id)
                            } else {
                                repository.deleteExercise(exercise.id)
                            }
                            pendingDelete = null
                        }
                    },
                    modifier = Modifier.testTag(if (referenced) "confirm-archive" else "confirm-delete"),
                ) {
                    Text(if (referenced) "Archive instead" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}


