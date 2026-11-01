package com.buhat.tracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.SeedContent
import com.buhat.tracker.domain.ContentValidationException
import com.buhat.tracker.ui.components.BuhatChoiceChip
import com.buhat.tracker.ui.components.BuhatScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorScreen(
    repository: BuhatRepository,
    exerciseId: String?,
    onBack: () -> Unit,
) {
    val isNew = exerciseId == null || exerciseId == "new"
    var name by rememberSaveable { mutableStateOf("") }
    var muscleGroup by rememberSaveable { mutableStateOf(SeedContent.muscleGroups.first()) }
    var equipment by rememberSaveable { mutableStateOf(SeedContent.equipmentTypes.first()) }
    var loading by remember { mutableStateOf(!isNew) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(exerciseId) {
        if (!isNew) {
            repository.exercise(exerciseId!!)?.let {
                name = it.name
                muscleGroup = it.muscleGroup
                equipment = it.equipment
            }
            loading = false
        }
    }

    BuhatScreen(title = if (isNew) "New exercise" else "Edit exercise", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("screen-ExerciseEditor"),
        ) {
            if (loading) {
                Text("Loading...", modifier = Modifier.padding(24.dp))
                return@Column
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Exercise name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("exercise-name"),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Muscle group",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeedContent.muscleGroups.forEach { option ->
                    BuhatChoiceChip(
                        label = option,
                        selected = option == muscleGroup,
                        modifier = Modifier.testTag("muscle-$option"),
                        onClick = { muscleGroup = option },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Equipment",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeedContent.equipmentTypes.forEach { option ->
                    BuhatChoiceChip(
                        label = option,
                        selected = option == equipment,
                        modifier = Modifier.testTag("equipment-$option"),
                        onClick = { equipment = option },
                    )
                }
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
                    scope.launch {
                        try {
                            repository.saveExercise(
                                id = if (isNew) null else exerciseId,
                                name = name,
                                muscleGroup = muscleGroup,
                                equipment = equipment,
                            )
                            withContext(Dispatchers.Main) { onBack() }
                        } catch (e: ContentValidationException) {
                            error = e.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("save-exercise"),
            ) {
                Text("Save exercise")
            }
        }
    }
}
