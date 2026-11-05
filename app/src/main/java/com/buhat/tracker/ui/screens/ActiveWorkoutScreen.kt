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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.buhat.tracker.data.local.LoggedSet
import com.buhat.tracker.data.local.WorkoutSession
import com.buhat.tracker.data.toRecord
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.WeightUnit
import com.buhat.tracker.domain.WEIGHT_INCREMENT_KG
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
import com.buhat.tracker.domain.sourceSetForPosition
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SET_TYPES = listOf(SetType.WARMUP, SetType.WORKING, SetType.FAILURE)
private val SET_TYPE_LABELS = SET_TYPES.map { it.label }

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
    var selectedId by rememberSaveable(session.id) { mutableStateOf<String?>(null) }
    val selected = exercises.firstOrNull { it.id == selectedId } ?: exercises.firstOrNull()
    val settingsResult by remember(repository) { repository.settingsStore.settings }
        .collectAsState<Settings, Settings?>(null)
    val settings = settingsResult ?: Settings()
    val restEndsAt = settingsResult?.restEndsAt
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val sets by remember(selected?.id) {
        selected?.let { repository.loggedSets(it.id) } ?: flowOf(emptyList())
    }.collectAsState(emptyList())
    val history by remember(selected?.exerciseId) {
        selected?.let { repository.historyForExercise(it.exerciseId) } ?: flowOf(emptyList())
    }.collectAsState(emptyList())

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var elapsedNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
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
    val target = selected?.let { nextTarget(source, it.repMin, it.repMax, settings.unit) }
    val prefill = prefillFor(source, records.lastOrNull())
    val unit = settings.unit

    // Prefill keys include the source values: history flows in after the first frame, and the
    // form must adopt them without wiping a user edit (prefill only changes on new data).
    var weightText by rememberSaveable(selected?.id, position, prefill.weightKg, prefill.reps, unit) {
        mutableStateOf(prefill.weightKg?.let { formatWeightValue(it, unit) } ?: "")
    }
    // kg keeps generated values exact; parsing the rounded lb display back would drift.
    var weightKg by rememberSaveable(selected?.id, position, prefill.weightKg, prefill.reps, unit) {
        mutableStateOf(prefill.weightKg)
    }
    var repsText by rememberSaveable(selected?.id, position, prefill.weightKg, prefill.reps) {
        mutableStateOf(prefill.reps?.toString() ?: "")
    }
    var rir by rememberSaveable(selected?.id, position, prefill.weightKg, prefill.reps) {
        mutableStateOf(prefill.rir)
    }
    var typeIndex by rememberSaveable(selected?.id, position) { mutableStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LoggedSet?>(null) }
    var discardPrompt by remember { mutableStateOf<DiscardPrompt?>(null) }
    fun displayToKg(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.let { if (unit == WeightUnit.LB) lbToKg(it) else it }

    fun setWeight(kg: Double?) {
        weightKg = kg
        weightText = kg?.let { formatWeightValue(it, unit) } ?: ""
    }

    fun stepWeight(delta: Int) {
        setWeight(((weightKg ?: 0.0) + delta * WEIGHT_INCREMENT_KG).coerceIn(0.0, 1000.0))
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
                        scope.launch {
                            try {
                                withContext(NonCancellable) {
                                    repository.finishSession(session.id)
                                    restTimer.cancel()
                                }
                            } catch (e: NoWorkingSetsException) {
                                discardPrompt = DiscardPrompt(
                                    "No working sets yet.",
                                    "A workout needs at least one working set. Discard it instead?",
                                )
                            }
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
            }
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
                    text = "Target RIR ${exercise.targetRir}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = {
                setWeight(nextTargetValue.weightKg)
                repsText = nextTargetValue.reps.toString()
            }, modifier = Modifier.testTag("use-target")) {
                Text("Use target")
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
                        text = "RIR $it",
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
                        val next = ((weightKg ?: 0.0) + delta * WEIGHT_INCREMENT_KG).coerceIn(0.0, 1000.0)
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "RIR",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        StepButton("−", "${tagPrefix}rir-minus") { onRirChange((rir - 1).coerceIn(0, 5)) }
        Text(
            text = rir.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        StepButton("+", "${tagPrefix}rir-plus") { onRirChange((rir + 1).coerceIn(0, 5)) }
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
