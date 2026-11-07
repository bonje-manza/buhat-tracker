package com.buhat.tracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.buhat.tracker.BuildConfig
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.ProfileStore
import com.buhat.tracker.diagnostics.DiagnosticsController
import com.buhat.tracker.timer.RestTimer
import com.buhat.tracker.ui.screens.ActiveWorkoutScreen
import com.buhat.tracker.ui.screens.ExerciseEditorScreen
import com.buhat.tracker.ui.screens.ExerciseLibraryScreen
import com.buhat.tracker.ui.screens.HomeScreen
import com.buhat.tracker.ui.screens.ProfileEditorScreen
import com.buhat.tracker.ui.screens.ProgressScreen
import com.buhat.tracker.ui.screens.SettingsScreen
import com.buhat.tracker.ui.screens.TemplateEditorScreen
import com.buhat.tracker.ui.screens.WorkoutsScreen
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

enum class BuhatTab(val label: String) {
    Home("Home"),
    Workouts("Workouts"),
    Active("Active"),
    Progress("Progress"),
}

@Composable
fun BuhatApp(
    repository: BuhatRepository,
    profileStore: ProfileStore,
    restTimer: RestTimer,
    backupController: BackupController,
    diagnosticsController: DiagnosticsController,
) {
    VersionUpdatePrompt(repository, backupController)
    CrashNotice(diagnosticsController)
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "tabs") {
        composable("tabs") {
            TabsScreen(
                repository = repository,
                profileStore = profileStore,
                restTimer = restTimer,
                backupController = backupController,
                onOpenProfile = { nav.navigate("profile") },
                onOpenSettings = { nav.navigate("settings") },
                onOpenLibrary = { nav.navigate("library") },
                onNewTemplate = { nav.navigate("template/new") },
                onEditTemplate = { id -> nav.navigate("template/$id") },
            )
        }
        composable("library") {
            ExerciseLibraryScreen(
                repository = repository,
                onBack = { nav.popBackStack() },
                onNewExercise = { nav.navigate("exercise/new") },
                onEditExercise = { id -> nav.navigate("exercise/$id") },
            )
        }
        composable("exercise/{id}") { entry ->
            ExerciseEditorScreen(
                repository = repository,
                exerciseId = entry.arguments?.getString("id"),
                onBack = { nav.popBackStack() },
            )
        }
        composable("template/{id}") { entry ->
            TemplateEditorScreen(
                repository = repository,
                templateId = entry.arguments?.getString("id"),
                onBack = { nav.popBackStack() },
            )
        }
        composable("profile") {
            ProfileEditorScreen(profileStore = profileStore, settingsStore = repository.settingsStore,
                onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                repository = repository,
                backupController = backupController,
                diagnosticsController = diagnosticsController,
                onBack = { nav.popBackStack() },
                onOpenProfile = { nav.navigate("profile") },
            )
        }
    }
}

@Composable
private fun TabsScreen(
    repository: BuhatRepository,
    profileStore: ProfileStore,
    restTimer: RestTimer,
    backupController: BackupController,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onNewTemplate: () -> Unit,
    onEditTemplate: (String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(BuhatTab.Home) }
    var progressExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var progressRange by rememberSaveable { mutableStateOf(1) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BuhatBottomBar(selected = selected, onSelect = { selected = it }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selected) {
                BuhatTab.Home -> HomeScreen(
                    repository = repository,
                    profileStore = profileStore,
                    backupController = backupController,
                    onOpenProfile = onOpenProfile,
                    onOpenSettings = onOpenSettings,
                    onOpenProgress = { id ->
                        progressExerciseId = id
                        selected = BuhatTab.Progress
                    },
                )
                BuhatTab.Workouts -> WorkoutsScreen(
                    repository = repository,
                    restTimer = restTimer,
                    onNewTemplate = onNewTemplate,
                    onEditTemplate = onEditTemplate,
                    onOpenLibrary = onOpenLibrary,
                    onOpenActive = { selected = BuhatTab.Active },
                )
                BuhatTab.Active -> ActiveWorkoutScreen(
                    repository = repository,
                    restTimer = restTimer,
                    onStartWorkout = { selected = BuhatTab.Workouts },
                )
                BuhatTab.Progress -> ProgressScreen(
                    repository = repository,
                    selectedExerciseId = progressExerciseId,
                    onSelectExercise = { progressExerciseId = it },
                    rangeIndex = progressRange,
                    onSelectRange = { progressRange = it },
                )
            }
        }
    }
}

@Composable
internal fun BuhatBottomBar(selected: BuhatTab, onSelect: (BuhatTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BuhatTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.surface,
                    )
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                    .testTag("tab-${tab.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun VersionUpdatePrompt(repository: BuhatRepository, backupController: BackupController) {
    val store = repository.settingsStore
    val stored by remember(store) {
        store.lastRunVersionCode.catch { emit(Int.MIN_VALUE) }
    }.collectAsState(initial = Int.MIN_VALUE)
    var show by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun acknowledge() {
        scope.launch {
            store.acknowledgeVersion(BuildConfig.VERSION_CODE)
            show = false
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        backupController.export(context.contentResolver, uri) { acknowledge() }
    }

    LaunchedEffect(stored) {
        if (stored != Int.MIN_VALUE && stored != null && stored != BuildConfig.VERSION_CODE) {
            show = true
        }
    }

    if (!show) return
    UpdatePromptDialog(
        repository = repository,
        backupController = backupController,
        onAcknowledge = { acknowledge() },
        launcher = { launcher.launch(backupFileName()) },
    )
}

@Composable
private fun UpdatePromptDialog(
    repository: BuhatRepository,
    backupController: BackupController,
    onAcknowledge: () -> Unit,
    launcher: () -> Unit,
) {
    val active by remember(repository) { repository.activeSession() }.collectAsState(initial = null)
    val hasActive = active != null
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag("update-prompt"),
        title = { Text("Buhat was updated") },
        text = {
            Text(
                if (hasActive) "Finish or discard the workout first, then export a backup."
                else "Export a backup before you continue.",
            )
        },
        confirmButton = {
            TextButton(
                enabled = !hasActive && !backupController.busy,
                onClick = launcher,
                modifier = Modifier.testTag("update-export"),
            ) { Text("Export backup") }
        },
        dismissButton = {
            TextButton(
                onClick = onAcknowledge,
                modifier = Modifier.testTag("update-later"),
            ) { Text("Not now") }
        },
    )
}

@Composable
private fun CrashNotice(controller: DiagnosticsController) {
    if (!controller.pendingCrash) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        uri?.let { controller.export(context.contentResolver, it) }
        controller.dismissCrash()
    }
    AlertDialog(
        onDismissRequest = { controller.dismissCrash() },
        modifier = Modifier.testTag("crash-notice"),
        title = { Text("Buhat closed unexpectedly") },
        text = { Text("Export diagnostics to keep a local record, or dismiss this notice.") },
        confirmButton = {
            TextButton(
                onClick = { launcher.launch(diagnosticsFileName()) },
                modifier = Modifier.testTag("crash-export"),
            ) { Text("Export diagnostics") }
        },
        dismissButton = {
            TextButton(
                onClick = { controller.dismissCrash() },
                modifier = Modifier.testTag("crash-dismiss"),
            ) { Text("Dismiss") }
        },
    )
}
