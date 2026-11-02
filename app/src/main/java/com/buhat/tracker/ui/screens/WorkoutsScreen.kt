package com.buhat.tracker.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.ActiveSessionException
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.SessionValidationException
import com.buhat.tracker.data.TemplateCard
import com.buhat.tracker.timer.RestTimer
import com.buhat.tracker.ui.components.BuhatCard
import com.buhat.tracker.ui.components.BuhatEmptyState
import com.buhat.tracker.ui.components.BuhatPill
import com.buhat.tracker.ui.components.BuhatSegmentedControl
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WorkoutsScreen(
    repository: BuhatRepository,
    restTimer: RestTimer,
    onNewTemplate: () -> Unit,
    onEditTemplate: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenActive: () -> Unit,
) {
    val cards by remember(repository) { repository.templateCards() }
        .collectAsState(emptyList())
    var filter by rememberSaveable { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<TemplateCard?>(null) }
    var startConflict by remember { mutableStateOf<Pair<String, TemplateCard>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val filtered = when (filter) {
        1 -> cards.filter { it.template.isPremade }
        2 -> cards.filter { !it.template.isPremade }
        else -> cards
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("screen-Workouts")
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${filtered.size} ${if (filtered.size == 1) "template" else "templates"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Workouts",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
            TextButton(onClick = onOpenLibrary, modifier = Modifier.testTag("open-library")) {
                Text("Library")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onNewTemplate, modifier = Modifier.testTag("new-template")) {
                Text("+ New")
            }
        }
        Spacer(Modifier.height(16.dp))
        BuhatSegmentedControl(
            options = listOf("All", "Premade", "My templates"),
            selectedIndex = filter,
            onSelect = { filter = it },
        )
        Spacer(Modifier.height(16.dp))
        if (filtered.isEmpty()) {
            BuhatEmptyState(
                title = if (filter == 2) "No custom templates" else "No templates",
                message = if (filter == 2) {
                    "Duplicate a premade template or create one."
                } else {
                    "Create your first template."
                },
            )
        }
        filtered.forEach { card ->
            TemplateCardItem(
                card = card,
                onEdit = { onEditTemplate(card.template.id) },
                onStart = {
                    scope.launch {
                        try {
                            repository.startSession(card.template.id)
                            onOpenActive()
                        } catch (e: ActiveSessionException) {
                            startConflict = e.sessionId to card
                        } catch (e: SessionValidationException) {
                            error = e.message
                        }
                    }
                },
                onDuplicate = {
                    scope.launch {
                        try {
                            repository.duplicateTemplate(card.template.id)
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                },
                onDelete = { pendingDelete = card },
            )
            Spacer(Modifier.height(16.dp))
        }
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("error-message"),
            )
        }
    }

    pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete template?") },
            text = { Text("\"${card.template.name}\" and its exercises will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteTemplate(card.template.id)
                            pendingDelete = null
                        }
                    },
                    modifier = Modifier.testTag("confirm-delete-template"),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    startConflict?.let { (activeId, card) ->
        AlertDialog(
            onDismissRequest = { startConflict = null },
            title = { Text("Workout in progress") },
            text = { Text("Resume it, or discard it and start \"${card.template.name}\".") },
            confirmButton = {
                TextButton(
                    onClick = {
                        startConflict = null
                        onOpenActive()
                    },
                    modifier = Modifier.testTag("resume-workout"),
                ) {
                    Text("Resume")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        startConflict = null
                        scope.launch {
                            withContext(NonCancellable) {
                                repository.discardSession(activeId)
                                restTimer.cancel()
                            }
                            try {
                                repository.startSession(card.template.id)
                                onOpenActive()
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    },
                    modifier = Modifier.testTag("discard-and-start"),
                ) {
                    Text("Discard and start")
                }
            },
        )
    }
}

@Composable
private fun TemplateCardItem(
    card: TemplateCard,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    BuhatCard(Modifier.testTag("template-card-${card.template.name}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = card.template.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            BuhatPill(if (card.template.isPremade) "Premade" else "Custom")
            Box {
                TextButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("template-menu-${card.template.name}"),
                ) {
                    Text("⋯")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (card.template.isPremade) {
                        DropdownMenuItem(
                            text = { Text("Duplicate template") },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Edit template") },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete template") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Exercises", card.exercises.size, Modifier.weight(1f))
            StatTile("Total sets", card.exercises.sumOf { it.workingSets }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = card.exercises.joinToString(" · ") { it.exerciseName },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("start-workout-${card.template.name}"),
        ) {
            Text("Start workout")
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            text = value.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
