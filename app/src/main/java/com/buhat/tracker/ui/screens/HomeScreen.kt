package com.buhat.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.BuhatRepository
import com.buhat.tracker.data.Profile
import com.buhat.tracker.data.ProfileStore
import com.buhat.tracker.data.local.ProfileStats
import com.buhat.tracker.domain.WeightUnit
import com.buhat.tracker.domain.formatWeight
import com.buhat.tracker.ui.components.BuhatCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    repository: BuhatRepository,
    profileStore: ProfileStore,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val profile by remember(profileStore) { profileStore.profile }
        .collectAsState(Profile(null, null, null, null, null))
    val stats by remember(repository) { repository.profileStats() }
        .collectAsState(ProfileStats(0, null))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("screen-Home")
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        BuhatCard(Modifier.testTag("profile-card").clickable(onClick = onOpenProfile)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = profile.name?.trim()?.firstOrNull()?.uppercase() ?: "?",
                        color = MaterialTheme.colorScheme.background,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.name ?: "Set up profile",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag("profile-card-name"),
                    )
                    Text(
                        text = trainingSummary(stats),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("settings-button"),
                ) {
                    Text("Settings")
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileTile("Gender", profile.gender ?: "—", Modifier.weight(1f))
                ProfileTile(
                    "Weight",
                    profile.weightKg?.let { formatWeight(it, WeightUnit.KG) } ?: "—",
                    Modifier.weight(1f),
                )
                ProfileTile("Height", profile.heightCm?.let { "$it cm" } ?: "—", Modifier.weight(1f))
            }
        }
    }
}

private fun trainingSummary(stats: ProfileStats): String {
    val first = stats.firstFinishedAt ?: return "No workouts yet"
    val month = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(first))
    val workouts = if (stats.totalWorkouts == 1) "1 workout" else "${stats.totalWorkouts} workouts"
    return "Training since $month · $workouts"
}

@Composable
private fun ProfileTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
