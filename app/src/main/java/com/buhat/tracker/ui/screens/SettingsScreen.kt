package com.buhat.tracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.ProfileStore
import com.buhat.tracker.ui.components.BuhatCard
import com.buhat.tracker.ui.components.BuhatScreen

@Composable
fun SettingsScreen(
    profileStore: ProfileStore,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val profile by remember(profileStore) { profileStore.profile }.collectAsState(null)

    BuhatScreen(title = "Settings", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("screen-Settings"),
        ) {
            BuhatCard(Modifier.clickable(onClick = onOpenProfile).testTag("settings-profile")) {
                Text(
                    text = "Profile",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = profile?.name ?: "Set up your profile",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
