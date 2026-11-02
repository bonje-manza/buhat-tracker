package com.buhat.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.ProfileStore
import com.buhat.tracker.timer.RestTimer
import com.buhat.tracker.ui.screens.ActiveWorkoutScreen
import com.buhat.tracker.ui.screens.ExerciseEditorScreen
import com.buhat.tracker.ui.screens.ExerciseLibraryScreen
import com.buhat.tracker.ui.screens.HomeScreen
import com.buhat.tracker.ui.screens.ProfileEditorScreen
import com.buhat.tracker.ui.screens.SettingsScreen
import com.buhat.tracker.ui.screens.TemplateEditorScreen
import com.buhat.tracker.ui.screens.WorkoutsScreen

enum class BuhatTab(val label: String) {
    Home("Home"),
    Workouts("Workouts"),
    Active("Active"),
    Progress("Progress"),
}

@Composable
fun BuhatApp(repository: BuhatRepository, profileStore: ProfileStore, restTimer: RestTimer) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "tabs") {
        composable("tabs") {
            TabsScreen(
                repository = repository,
                profileStore = profileStore,
                restTimer = restTimer,
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
            ProfileEditorScreen(profileStore = profileStore, onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                profileStore = profileStore,
                settingsStore = repository.settingsStore,
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
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onNewTemplate: () -> Unit,
    onEditTemplate: (String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(BuhatTab.Home) }

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
                    onOpenProfile = onOpenProfile,
                    onOpenSettings = onOpenSettings,
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
                BuhatTab.Progress -> EmptyScreen(BuhatTab.Progress)
            }
        }
    }
}

@Composable
private fun BuhatBottomBar(selected: BuhatTab, onSelect: (BuhatTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        else MaterialTheme.colorScheme.surfaceVariant,
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
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyScreen(tab: BuhatTab) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen-${tab.name}")
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = tab.label,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}
