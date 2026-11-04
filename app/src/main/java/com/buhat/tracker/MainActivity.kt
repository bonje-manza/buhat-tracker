package com.buhat.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.buhat.tracker.domain.AppTheme
import com.buhat.tracker.ui.BuhatApp
import com.buhat.tracker.ui.theme.BuhatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BuhatApplication).container
        setContent {
            val settings by remember(container) { container.settingsStore.settings }.collectAsState(initial = null)
            val dark = settings?.theme != AppTheme.LIGHT
            LaunchedEffect(dark) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            BuhatTheme(darkTheme = dark) {
                BuhatApp(
                    repository = container.repository,
                    profileStore = container.profileStore,
                    restTimer = container.restTimer,
                    backupController = container.backupController,
                )
            }
        }
    }
}
