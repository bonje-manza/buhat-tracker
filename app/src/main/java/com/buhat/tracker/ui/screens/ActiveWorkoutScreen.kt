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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.NoWorkingSetsException
import com.buhat.tracker.data.SessionValidationException
import com.buhat.tracker.data.Settings
import com.buhat.tracker.data.local.Exercise
import com.buhat.tracker.data.local.LoggedSet
import com.buhat.tracker.data.local.SessionExercise
import com.buhat.tracker.data.local.WorkoutSession
import com.buhat.tracker.data.toRecord
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.WeightUnit
import com.buhat.tracker.domain.effectiveUnit
import com.buhat.tracker.domain.formatElapsed
import com.buhat.tracker.domain.formatRest
import com.buhat.tracker.domain.formatWeight
import com.buhat.tracker.domain.formatWeightValue
import com.buhat.tracker.domain.lbToKg
import com.buhat.tracker.domain.nextTarget
import com.buhat.tracker.domain.numberWorkingSets
import com.buhat.tracker.domain.prefillFor
import com.buhat.tracker.domain.restRemainingMillis
import com.buhat.tracker.domain.restDeadlineElapsed
import com.buhat.tracker.domain.rirLabel
import com.buhat.tracker.domain.sourceSetForPosition
import com.buhat.tracker.domain.weightIncrementKg
import com.buhat.tracker.timer.RestTimer
import com.buhat.tracker.ui.components.BuhatCard
import com.buhat.tracker.ui.components.BuhatChoiceChip
import com.buhat.tracker.ui.components.BuhatEmptyState
import com.buhat.tracker.ui.components.BuhatSegmentedControl
import com.buhat.tracker.ui.components.SetTypeBadge
import com.buhat.tracker.ui.components.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SET_TYPES = listOf(SetType.WARMUP, SetType.WORKING, SetType.FAILURE)
private val SET_TYPE_LABELS = SET_TYPES.map { it.label }
private val RIR_CHOICES = listOf(0, 1, 2, 3, 4)

@Composable
fun ActiveWorkoutScreen(repository: BuhatRepository, restTimer: RestTimer, onStartWorkout: () -> Unit) {
    val session by remember(repository) { repository.activeSession() }.collectAsState(null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("screen-Active")
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        val current = session
        if (current == null) {
            Text(
                text = "Active",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineLarge,
            )
            Column(Modifier.testTag("active-empty")) {
                BuhatEmptyState("No active workout", "Start a workout from Workouts.")
            }
            Button(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth()) {
                Text("Go to Workouts")
            }
        } else {
            ActiveSession(repository, restTimer, current)
        }
    }
}

@Composable
private fun ActiveSession(repository: BuhatRepository, restTimer: RestTimer, session: WorkoutSession) {
    val exercises by remember(session.id) { repository.sessionExercises(session.id) }
        .collectAsState(emptyList())
    val library by remember(repository) { repository.exercises() }.collectAsState(emptyList())
    var selectedId by rememberSaveable(session.id) { mutableStateOf<String?>(null) }
    val selected = exercises.firstOrNull { it.id == selectedId } ?: exercises.firstOrNull()
    val settingsResult by remember(repository) { repository.settingsStore.settings }
        .collectAsState<Settings, Settings?>(null)
    val settings = settingsResult ?: Settings()
    val restEndsAt = settingsResult?.restEndsAt
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val observedExercise by remember(selected?.exerciseId) {
        selected?.let { repository.observeExercise(it.exerciseId) } ?: flowOf(null)
    }.collectAsState(null)
    val exerciseRow = observedExercise?.takeIf { it.id == selected?.exerciseId }

    val sets by remember(selected?.id) {
        selected?.let { repository.loggedSets(it.id) } ?: flowOf(emptyList())
    }.collectAsState(emptyList())
    val history by remember(selected?.exerciseId, session.templateIdSnapshot) {
        selected?.let { repository.latestHistoryForExercise(it.exerciseId, session.templateIdSnapshot) }
            ?: flowOf(emptyList())
    }.collectAsState(emptyList())

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var elapsedNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerReplacing by remember { mutableStateOf(false) }
    var pickerQuery by remember { mutableStateOf("") }
    var pendingAdd by remember { mutableStateOf<Exercise?>(null) }
    var editOpen by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var structureError by remember { mutableStateOf<String?>(null) }
    var finishPromptOpen by remember { mutableStateOf(false) }
    var finishError by remember { mutableStateOf<String?>(null) }
    var alarmAccessAsked by rememberSaveable(session.id) { mutableStateOf(false) }
    var alarmAccessDenied by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val alarmPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (!restTimer.canScheduleExactAlarms()) {
            alarmAccessDenied = true
        } else {
            settings.restEndsAt
                ?.takeIf { it > System.currentTimeMillis() }
                ?.let { endsAt ->
                    scope.launch {
                        withContext(NonCancellable) {
                            if (!restTimer.start(endsAt)) alarmAccessDenied = true
                        }
                    }
                }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            permissionDenied = true
        } else {
            scope.launch {
                withContext(NonCancellable) {
                    repository.settingsStore.settings.first().restEndsAt
                        ?.takeIf { it > System.currentTimeMillis() }
                        ?.let { restTimer.start(it) }
                }
            }
        }
    }

    LaunchedEffect(session.id, restEndsAt) {
        if (restEndsAt != null && restEndsAt > System.currentTimeMillis() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !repository.settingsStore.notificationPermissionRequested.first()
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            withContext(NonCancellable) {
                repository.settingsStore.markNotificationPermissionRequested()
            }
        }
    }

    LaunchedEffect(session.id) {
        while (true) {
            now = System.currentTimeMillis()
            elapsedNow = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    val restElapsedDeadline = remember(restEndsAt) {
        restEndsAt?.let { restDeadlineElapsed(it, System.currentTimeMillis(), SystemClock.elapsedRealtime()) }
            ?: 0L
    }
    val restRemaining = restRemainingMillis(restElapsedDeadline, elapsedNow)
    // The persisted end time is the only timer state: restarting the app re-arms the same alarm.
    LaunchedEffect(session.id, restEndsAt, settingsResult != null) {
        if (settingsResult == null) return@LaunchedEffect
        when {
            restEndsAt == null -> withContext(NonCancellable) { restTimer.cancel() }
            restEndsAt > System.currentTimeMillis() && !restTimer.canScheduleExactAlarms() -> {
                if (!alarmAccessAsked) {
                    alarmAccessAsked = true
                    alarmPermissionLauncher.launch(restTimer.exactAlarmPermissionIntent())
                } else {
                    alarmAccessDenied = true
                }
            }
            restEndsAt > System.currentTimeMillis() -> {
                withContext(NonCancellable) {
                    if (!restTimer.start(restEndsAt)) alarmAccessDenied = true
                }
            }
            else -> withContext(NonCancellable) {
                if (repository.completeRest(restEndsAt)) restTimer.cancel()
            }
        }
    }

    val records = sets.map { it.toRecord() }
    val workingCount = numberWorkingSets(records).size
    val position = workingCount + 1
    val source = selected?.let {
        sourceSetForPosition(
            history = history,
            exerciseId = it.exerciseId,
            position = position,
            source = settings.previousSetSource,
            templateIdSnapshot = session.templateIdSnapshot,
        )
    }
    val unit = exerciseRow?.effectiveUnit(settings.unit) ?: settings.unit
    val target = selected?.let { nextTarget(source, it.repMin, it.repMax, unit) }
    val prefill = prefillFor(source, records.lastOrNull())

    // Prefill keys include the source values: history flows in after the first frame, and the
    // form must adopt them without wiping a user edit (prefill only changes on new data).
    var weightText by rememberSaveable(selected?.id, position, prefill.weightKg, prefill.reps) {
        mutableStateOf(prefill.weightKg?.let { formatWeightValue(it, unit) } ?: "")
    }
    // kg keeps generated values exact; parsing the rounded lb display back would drift.
    var weightKg by rememberSaveable(selected?.id, position, prefill.weightKg, prefill.reps) {
        mutableStateOf(prefill.weightKg)
    }
    // A unit change only reformats the display, so a typed weight survives the switch.
    LaunchedEffect(unit) { weightText = weightKg?.let { formatWeightValue(it, unit) } ?: "" }
    var repsText by rememberSaveable(selected?.id, position, prefill.weightKg, prefill.reps) {
        mutableStateOf(prefill.reps?.toString() ?: "")
    }
    var rir by rememberSaveable(selected?.id, position, prefill.weightKg, prefill.reps) {
        mutableStateOf(prefill.rir)
    }
    var typeIndex by rememberSaveable(selected?.id, position) { mutableStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var orderOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LoggedSet?>(null) }
    var discardPrompt by remember { mutableStateOf<DiscardPrompt?>(null) }
    fun displayToKg(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.let { if (unit == WeightUnit.LB) lbToKg(it) else it }

    fun setWeight(kg: Double?) {
        weightKg = kg
        weightText = kg?.let { formatWeightValue(it, unit) } ?: ""
    }

    fun stepWeight(delta: Int) {
        setWeight(((weightKg ?: 0.0) + delta * weightIncrementKg(unit)).coerceIn(0.0, 1000.0))
    }

    fun log() {
        val exercise = selected ?: return
        val kg = weightKg
        val reps = repsText.trim().toIntOrNull()
        if (kg == null) {
            error = "Enter a weight."
            return
        }
        if (reps == null) {
            error = "Enter reps."
            return
        }
        focusManager.clearFocus()
        scope.launch {
            try {
                withContext(NonCancellable) {
                    repository.logSet(exercise.id, SET_TYPES[typeIndex], kg, reps, rir)
                    val nextRest = repository.settingsStore.settings.first().restEndsAt
                        ?.takeIf { it > System.currentTimeMillis() }
                    if (nextRest != null) restTimer.start(nextRest) else restTimer.cancel()
                }
                error = null
            } catch (e: SessionValidationException) {
                error = e.message
            }
        }
    }

    fun finish(updateTemplate: Boolean) {
        finishError = null
        scope.launch {
            try {
                withContext(NonCancellable) {
                    repository.finishSession(session.id, updateTemplate)
                    restTimer.cancel()
                }
                finishPromptOpen = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoWorkingSetsException) {
                finishPromptOpen = false
                discardPrompt = DiscardPrompt(
                    "No working sets yet.",
                    "A workout needs at least one working set. Discard it instead?",
                )
            } catch (e: Exception) {
                finishError = e.message ?: "Could not finish the workout."
            }
        }
    }

    fun removeSelectedExercise() {
        val row = selected ?: return
        structureError = null
        scope.launch {
            try {
                withContext(NonCancellable) { repository.removeSessionExercise(row.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                structureError = e.message ?: "Could not remove the exercise."
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Template",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = session.templateNameSnapshot,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("active-template"),
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
                    .padding(5.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatElapsed(session.startedAt, now),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Box {
            TextButton(
                onClick = { menuOpen = true },
                modifier = Modifier.testTag("active-menu").semantics { contentDescription = "Workout options" },
            ) {
                Text("⋯")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Finish workout") },
                    onClick = {
                        menuOpen = false
                        if (session.structureDirty) {
                            finishError = null
                            finishPromptOpen = true
                        } else {
                            finish(updateTemplate = false)
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Discard workout") },
                    onClick = {
                        menuOpen = false
                        discardPrompt = DiscardPrompt("Discard workout?", "The logged sets will be deleted.")
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add exercise") },
                    modifier = Modifier.testTag("add-session-exercise"),
                    onClick = {
                        menuOpen = false
                        pickerReplacing = false
                        pickerQuery = ""
                        pickerOpen = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("Edit exercise order") },
                    enabled = exercises.isNotEmpty(),
                    modifier = Modifier.testTag("edit-order"),
                    onClick = {
                        menuOpen = false
                        selectedId = selected?.id
                        orderOpen = true
                    },
                )
            }
        }
    }
    if (!finishPromptOpen) {
        finishError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("finish-error"),
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        exercises.forEach { exercise ->
            BuhatChoiceChip(
                label = exercise.exerciseNameSnapshot,
                selected = exercise.id == selected?.id,
                modifier = Modifier.testTag("exercise-chip-${exercise.exerciseNameSnapshot}"),
                onClick = {
                    selectedId = exercise.id
                    error = null
                },
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    if (orderOpen) {
        ExerciseOrderDialog(repository, session.id, exercises) { orderOpen = false }
    }

    if (pickerOpen) {
        SessionExercisePickerDialog(
            exercises = library.filter { candidate -> exercises.none { it.exerciseId == candidate.id } },
            query = pickerQuery,
            onQueryChange = { pickerQuery = it },
            onPick = { picked ->
                val row = selected
                pickerOpen = false
                pickerQuery = ""
                if (pickerReplacing) {
                    if (row != null) {
                        structureError = null
                        scope.launch {
                            try {
                                selectedId = withContext(NonCancellable) {
                                    repository.replaceSessionExercise(row.id, picked.id)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                structureError = e.message ?: "Could not replace the exercise."
                            }
                        }
                    }
                } else {
                    dialogError = null
                    pendingAdd = picked
                }
            },
            onDismiss = { pickerOpen = false },
        )
    }

    pendingAdd?.let { exercise ->
        SessionPrescriptionDialog(
            title = "Add exercise",
            exerciseName = exercise.name,
            initialSets = 3,
            initialRepMin = 8,
            initialRepMax = 12,
            initialRir = null,
            error = dialogError,
            onDismiss = {
                pendingAdd = null
                dialogError = null
            },
            onSave = { newSets, repMin, repMax, targetRir ->
                scope.launch {
                    try {
                        val id = withContext(NonCancellable) {
                            repository.addSessionExercise(
                                session.id, exercise.id, newSets, repMin, repMax, targetRir,
                            )
                        }
                        selectedId = id
                        pendingAdd = null
                        dialogError = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        dialogError = e.message ?: "Could not add the exercise."
                    }
                }
            },
        )
    }

    val editTarget = selected
    if (editOpen && editTarget != null) {
        SessionPrescriptionDialog(
            title = "Edit exercise",
            exerciseName = editTarget.exerciseNameSnapshot,
            initialSets = editTarget.workingSets,
            initialRepMin = editTarget.repMin,
            initialRepMax = editTarget.repMax,
            initialRir = editTarget.targetRir,
            error = dialogError,
            onDismiss = {
                editOpen = false
                dialogError = null
            },
            onSave = { newSets, repMin, repMax, targetRir ->
                scope.launch {
                    try {
                        withContext(NonCancellable) {
                            repository.editSessionExercise(
                                editTarget.id, newSets, repMin, repMax, targetRir,
                            )
                        }
                        editOpen = false
                        dialogError = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        dialogError = e.message ?: "Could not edit the exercise."
                    }
                }
            },
        )
    }

    if (restRemaining > 0) {
        BuhatCard(Modifier.testTag("rest-card")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Rest",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = formatRest(restRemaining),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.testTag("rest-countdown"),
                    )
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(NonCancellable) {
                                repository.clearRest()
                                restTimer.cancel()
                            }
                        }
                    },
                    modifier = Modifier.testTag("skip-rest"),
                ) {
                    Text("Skip")
                }
            }
        }
    } else {
        BuhatCard(Modifier.testTag("rest-idle")) {
            Text("Ready for next set", style = MaterialTheme.typography.titleMedium)
            Text("Starts automatically after each set", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(Modifier.height(16.dp))
    val exercise = selected
    val nextTargetValue = target
    if (exercise != null && nextTargetValue != null) {
        BuhatCard(Modifier.testTag("target-card")) {
            Text(
                text = "Target today · ${exercise.repMin}–${exercise.repMax} rep range",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${nextTargetValue.weightKg?.let { formatWeight(it, unit) } ?: "—"} × ${nextTargetValue.reps}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.testTag("target-value"),
            )
            Text(
                text = nextTargetValue.message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("target-message"),
            )
            if (exercise.targetRir != null) {
                Text(
                    text = "Target RIR ${rirLabel(exercise.targetRir)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            exerciseRow?.note?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("exercise-note"),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    setWeight(nextTargetValue.weightKg)
                    repsText = nextTargetValue.reps.toString()
                }, modifier = Modifier.testTag("use-target")) {
                    Text("Use target")
                }
                if (exerciseRow != null) {
                    TextButton(
                        onClick = { settingsOpen = true },
                        modifier = Modifier.testTag("target-exercise-settings"),
                    ) {
                        Text("Settings")
                    }
                }
            }
        }
        if (settingsOpen) {
            exerciseRow?.let { row ->
                ExerciseSettingsDialog(repository, row, settings) { settingsOpen = false }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    BuhatCard(Modifier.testTag("logged-sets")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = exercise?.exerciseNameSnapshot ?: "",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${sets.size} logged",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        sets.forEachIndexed { index, set ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { editing = set }
                    .testTag("set-row-${index + 1}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SetTypeBadge(set.setType)
                Text(
                    text = "${formatWeight(set.weightKg, unit)} × ${set.reps}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = set.setType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                set.rir?.let {
                    Text(
                        text = "RIR ${rirLabel(it)}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (exercise != null && workingCount >= exercise.workingSets) {
            Text(
                text = "All prescribed sets logged.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    BuhatCard {
        SetFields(
            typeIndex = typeIndex,
            onTypeIndexChange = { typeIndex = it },
            weightText = weightText,
            onWeightChange = { text -> weightText = text; weightKg = displayToKg(text) },
            onWeightStep = ::stepWeight,
            repsText = repsText,
            onRepsChange = { repsText = it },
            onRepsStep = { delta ->
                repsText = ((repsText.toIntOrNull() ?: 0) + delta).coerceIn(0, 100).toString()
            },
            rir = rir,
            onRirChange = { rir = it },
            unit = unit,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = ::log,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("log-set"),
        ) {
            Text("Log set")
        }
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("active-error"),
            )
        }
    }

    if (selected != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    dialogError = null
                    editOpen = true
                },
                modifier = Modifier.testTag("edit-session-exercise"),
            ) {
                Text("Edit")
            }
            TextButton(
                onClick = {
                    structureError = null
                    pickerReplacing = true
                    pickerQuery = ""
                    pickerOpen = true
                },
                modifier = Modifier.testTag("replace-session-exercise"),
            ) {
                Text("Replace")
            }
            TextButton(
                onClick = ::removeSelectedExercise,
                enabled = sets.isEmpty(),
                modifier = Modifier.testTag("remove-session-exercise"),
            ) {
                Text("Remove")
            }
        }
        structureError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("session-structure-error"),
            )
        }
    }

    editing?.let { set ->
        SetEditorDialog(
            set = set,
            unit = unit,
            onDismiss = { editing = null },
            onSave = { type, weightKg, reps, newRir ->
                scope.launch {
                    try {
                        repository.updateSet(set.id, type, weightKg, reps, newRir)
                        editing = null
                    } catch (e: SessionValidationException) {
                        error = e.message
                        editing = null
                    }
                }
            },
            onDelete = {
                scope.launch {
                    repository.deleteSet(set.id)
                    editing = null
                }
            },
        )
    }

    if (alarmAccessDenied) {
        AlertDialog(
            onDismissRequest = { alarmAccessDenied = false },
            title = { Text("Background alert unavailable") },
            text = {
                Text("Allow exact alarms in Settings for a background alert. The in-app timer still works.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        alarmAccessDenied = false
                        alarmPermissionLauncher.launch(restTimer.exactAlarmPermissionIntent())
                    },
                    modifier = Modifier.testTag("open-exact-alarm-settings"),
                ) {
                    Text("Open settings")
                }
            },
        )
    }

    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { permissionDenied = false },
            title = { Text("Notifications are off") },
            text = {
                Text(
                    "Allow notifications to see the rest countdown on your lock screen. " +
                        "The timer still sounds and vibrates without them.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { permissionDenied = false },
                    modifier = Modifier.testTag("dismiss-permission-hint"),
                ) {
                    Text("Got it")
                }
            },
        )
    }

    if (finishPromptOpen) {
        AlertDialog(
            onDismissRequest = {
                finishPromptOpen = false
                finishError = null
            },
            title = { Text("Update template?") },
            text = {
                Column(Modifier.testTag("finish-prompt")) {
                    Text("The workout structure changed. Update the template to match?")
                    finishError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("finish-error"),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { finish(updateTemplate = true) },
                    modifier = Modifier.testTag("finish-update-template"),
                ) {
                    Text("Update template")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { finish(updateTemplate = false) },
                        modifier = Modifier.testTag("finish-this-workout-only"),
                    ) {
                        Text("This workout only")
                    }
                    TextButton(
                        onClick = {
                            finishPromptOpen = false
                            finishError = null
                        },
                    ) {
                        Text("Cancel")
                    }
                }
            },
        )
    }

    discardPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { discardPrompt = null },
            title = { Text(prompt.title) },
            text = { Text(prompt.body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(NonCancellable) {
                                repository.discardSession(session.id)
                                restTimer.cancel()
                            }
                            discardPrompt = null
                        }
                    },
                    modifier = Modifier.testTag("confirm-discard"),
                ) {
                    Text("Discard workout")
                }
            },
            dismissButton = {
                TextButton(onClick = { discardPrompt = null }) { Text("Cancel") }
            },
        )
    }
}

private data class DiscardPrompt(val title: String, val body: String)

@Composable
private fun SessionExercisePickerDialog(
    exercises: List<Exercise>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select exercise") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Search exercises") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("session-picker-search"),
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                ) {
                    exercises.filter { it.name.contains(query, ignoreCase = true) }.forEach { exercise ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onPick(exercise) }
                                .testTag("session-picker-${exercise.name}")
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SessionPrescriptionDialog(
    title: String,
    exerciseName: String,
    initialSets: Int,
    initialRepMin: Int,
    initialRepMax: Int,
    initialRir: Int?,
    error: String?,
    onSave: (Int, Int, Int, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var sets by remember(exerciseName) { mutableStateOf(initialSets.toString()) }
    var repMin by remember(exerciseName) { mutableStateOf(initialRepMin.toString()) }
    var repMax by remember(exerciseName) { mutableStateOf(initialRepMax.toString()) }
    var rir by remember(exerciseName) { mutableStateOf(initialRir?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = exerciseName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", sets, "session-sets", Modifier.weight(1f)) { sets = it }
                    NumberField("Min", repMin, "session-rep-min", Modifier.weight(1f)) { repMin = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Max", repMax, "session-rep-max", Modifier.weight(1f)) { repMax = it }
                    NumberField("RIR", rir, "session-rir", Modifier.weight(1f)) { rir = it }
                }
                Text(
                    text = "Leave RIR blank for no target.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("session-dialog-error"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        sets.toIntOrNull() ?: -1,
                        repMin.toIntOrNull() ?: -1,
                        repMax.toIntOrNull() ?: -1,
                        if (rir.isBlank()) null else (rir.toIntOrNull() ?: -1),
                    )
                },
                modifier = Modifier.testTag("save-session-exercise"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ExerciseOrderDialog(
    repository: BuhatRepository,
    sessionId: String,
    exercises: List<SessionExercise>,
    onDismiss: () -> Unit,
) {
    var pendingOrder by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val saving = pendingOrder != null

    // Keep arrows disabled until the displayed Flow has caught up with the saved order.
    LaunchedEffect(exercises) {
        if (exercises.map { it.id } == pendingOrder) pendingOrder = null
    }

    fun move(index: Int, destination: Int) {
        // Read the state directly: a captured `saving` stays false for clicks in the same frame.
        if (pendingOrder != null || destination !in exercises.indices) return
        val ids = exercises.map { it.id }.toMutableList()
        ids.add(destination, ids.removeAt(index))
        pendingOrder = ids
        error = null
        scope.launch {
            try {
                withContext(NonCancellable) { repository.reorderSessionExercises(sessionId, ids) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                pendingOrder = null
                error = "Could not save exercise order. Try again."
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit exercise order") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                exercises.forEachIndexed { index, exercise ->
                    key(exercise.id) {
                        val name = exercise.exerciseNameSnapshot
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { move(index, index - 1) },
                                enabled = !saving && index > 0,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("order-up-$name")
                                    .semantics { contentDescription = "Move $name up" },
                            ) { Text("↑") }
                            TextButton(
                                onClick = { move(index, index + 1) },
                                enabled = !saving && index < exercises.lastIndex,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("order-down-$name")
                                    .semantics { contentDescription = "Move $name down" },
                            ) { Text("↓") }
                        }
                    }
                }
                if (saving) Text("Saving...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("order-error"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.testTag("close-order")) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun SetEditorDialog(
    set: LoggedSet,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (SetType, Double, Int, Int?) -> Unit,
    onDelete: () -> Unit,
) {
    var typeIndex by remember(set.id) { mutableStateOf(SET_TYPES.indexOf(set.setType)) }
    var weightText by remember(set.id) { mutableStateOf(formatWeightValue(set.weightKg, unit)) }
    var weightKg by remember(set.id) { mutableStateOf<Double?>(set.weightKg) }
    var repsText by remember(set.id) { mutableStateOf(set.reps.toString()) }
    fun parseWeight(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.let { if (unit == WeightUnit.LB) lbToKg(it) else it }
    var rir by remember(set.id) { mutableStateOf(set.rir ?: 2) }
    var error by remember(set.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit set") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SetFields(
                    typeIndex = typeIndex,
                    onTypeIndexChange = { typeIndex = it },
                    weightText = weightText,
                    onWeightChange = { text -> weightText = text; weightKg = parseWeight(text) },
                    onWeightStep = { delta ->
                        val next = ((weightKg ?: 0.0) + delta * weightIncrementKg(unit)).coerceIn(0.0, 1000.0)
                        weightKg = next
                        weightText = formatWeightValue(next, unit)
                    },
                    repsText = repsText,
                    onRepsChange = { repsText = it },
                    onRepsStep = { delta ->
                        repsText = ((repsText.toIntOrNull() ?: 0) + delta).coerceIn(0, 100).toString()
                    },
                    rir = rir,
                    onRirChange = { rir = it },
                    unit = unit,
                    tagPrefix = "edit-",
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val weight = weightKg
                    val reps = repsText.trim().toIntOrNull()
                    if (weight == null) {
                        error = "Enter a weight."
                    } else if (reps == null) {
                        error = "Enter reps."
                    } else {
                        onSave(SET_TYPES[typeIndex], weight, reps, rir)
                    }
                },
                modifier = Modifier.testTag("save-set"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete, modifier = Modifier.testTag("delete-set")) {
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SetFields(
    typeIndex: Int,
    onTypeIndexChange: (Int) -> Unit,
    weightText: String,
    onWeightChange: (String) -> Unit,
    onWeightStep: (Int) -> Unit,
    repsText: String,
    onRepsChange: (String) -> Unit,
    onRepsStep: (Int) -> Unit,
    rir: Int,
    onRirChange: (Int) -> Unit,
    unit: WeightUnit,
    tagPrefix: String = "",
) {
    BuhatSegmentedControl(
        options = SET_TYPE_LABELS,
        selectedIndex = typeIndex,
        onSelect = onTypeIndexChange,
        selectedColor = MaterialTheme.colorScheme.secondary,
        selectedContentColor = MaterialTheme.colorScheme.onSecondary,
    )
    Spacer(Modifier.height(12.dp))
    NumberField(
        label = "Weight (${if (unit == WeightUnit.KG) "kg" else "lb"})",
        value = weightText,
        onValueChange = onWeightChange,
        onStep = onWeightStep,
        tag = "${tagPrefix}weight-input",
    )
    Spacer(Modifier.height(8.dp))
    NumberField(
        label = "Reps",
        value = repsText,
        onValueChange = onRepsChange,
        onStep = onRepsStep,
        tag = "${tagPrefix}reps-input",
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "RIR · 0 = failure, 1 = one more rep possible",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    RirSelector(rir, onRirChange, tagPrefix)
}

@Composable
private fun RirSelector(rir: Int, onRirChange: (Int) -> Unit, tagPrefix: String) {
    val selectedValue = rir.coerceAtMost(4)
    Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RIR_CHOICES.forEach { value ->
            val selected = value == selectedValue
            val label = if (value == 4) "4+" else value.toString()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                    .selectable(selected = selected, role = Role.RadioButton) { onRirChange(value) }
                    .testTag("${tagPrefix}rir-$label")
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    tag: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFeatureSettings = "tnum",
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { contentDescription = label }.testTag(tag),
            )
        }
        Spacer(Modifier.width(12.dp))
        StepButton("−", "$tag-minus") { onStep(-1) }
        Spacer(Modifier.width(12.dp))
        StepButton("+", "$tag-plus") { onStep(1) }
    }
}

@Composable
private fun StepButton(label: String, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
