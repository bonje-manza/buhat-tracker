package com.buhat.tracker.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.buhat.tracker.data.Profile
import com.buhat.tracker.data.ProfileStore
import com.buhat.tracker.data.SettingsStore
import com.buhat.tracker.domain.WeightUnit
import com.buhat.tracker.domain.formatWeightValue
import com.buhat.tracker.domain.lbToKg
import com.buhat.tracker.domain.formatKg
import com.buhat.tracker.domain.validateTextLength
import com.buhat.tracker.ui.components.BuhatScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileEditorScreen(
    profileStore: ProfileStore,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    val profile by remember(profileStore) { profileStore.profile }.collectAsState(null)
    val settings by remember(settingsStore) { settingsStore.settings }.collectAsState(null)

    BuhatScreen(title = "Profile", onBack = onBack) {
        val loaded = profile
        val unit = settings?.unit
        if (loaded == null || unit == null) {
            Text("Loading...", modifier = Modifier.padding(24.dp))
            return@BuhatScreen
        }
        var name by rememberSaveable(loaded) { mutableStateOf(loaded.name.orEmpty()) }
        var gender by rememberSaveable(loaded) { mutableStateOf(loaded.gender.orEmpty()) }
        var age by rememberSaveable(loaded) { mutableStateOf(loaded.age?.toString().orEmpty()) }
        val displayedWeight = loaded.weightKg?.let { formatWeightValue(it, unit) }.orEmpty()
        var weight by rememberSaveable(loaded, unit) { mutableStateOf(displayedWeight) }
        var height by rememberSaveable(loaded) { mutableStateOf(loaded.heightCm?.toString().orEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }
        var saving by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("screen-ProfileEditor"),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile-name"),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = gender,
                onValueChange = { gender = it },
                label = { Text("Gender") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile-gender"),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = age,
                onValueChange = { new -> if (new.length <= 3 && new.all { it.isDigit() }) age = new },
                label = { Text("Age") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("profile-age"),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = weight,
                onValueChange = { new ->
                    if (new.count { it == '.' } <= 1 && new.all { it.isDigit() || it == '.' }) weight = new
                },
                label = { Text("Weight (${unit.name.lowercase()})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("profile-weight"),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = height,
                onValueChange = { new -> if (new.length <= 3 && new.all { it.isDigit() }) height = new },
                label = { Text("Height (cm)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("profile-height"),
            )
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
                    if (saving) return@Button
                    val ageValue = age.toIntOrNull()
                     val weightValue = if (weight == displayedWeight) loaded.weightKg
                         else weight.toDoubleOrNull()?.let { if (unit == WeightUnit.LB) lbToKg(it) else it }
                    val heightValue = height.toIntOrNull()
                    error = validateTextLength(name, "Name")
                        ?: validateTextLength(gender, "Gender")
                        ?: when {
                        age.isNotBlank() && (ageValue == null || ageValue !in 1..120) -> "Enter an age from 1 to 120."
                        weight.isNotBlank() && (weightValue == null || weightValue !in 0.0..1000.0) ->
                            "Enter a weight from 0 to 1000 kg."
                        height.isNotBlank() && (heightValue == null || heightValue !in 1..300) ->
                            "Enter a height from 1 to 300 cm."
                        else -> null
                    }
                    if (error == null) {
                        saving = true
                        scope.launch {
                            profileStore.save(
                                Profile(
                                    name = name.trim().ifBlank { null },
                                    gender = gender.trim().ifBlank { null },
                                    age = ageValue,
                                    weightKg = weightValue,
                                    heightCm = heightValue,
                                ),
                            )
                            withContext(Dispatchers.Main) { onBack() }
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().testTag("save-profile"),
            ) {
                Text("Save profile")
            }
        }
    }
}
