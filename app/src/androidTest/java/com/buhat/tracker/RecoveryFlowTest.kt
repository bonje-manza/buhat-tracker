package com.buhat.tracker

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<BuhatApplication>()

    private fun capturedBackup(): String =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("phase6-backup.json").bufferedReader().use { it.readText() }

    @Test
    fun successfulRecoveryRestoresTheBackup() {
        val before = app.containerGeneration.value

        app.recoverWithBackup(capturedBackup())

        compose.waitUntil(10_000) { app.containerGeneration.value > before }
        runBlocking {
            assertEquals("Avery Kamp", app.container.profileStore.profile.first().name)
            assertEquals(1, app.container.database.dao().exerciseCount())
        }
        assertNull(app.recoveryError.value)
    }

    @Test
    fun failedRecoveryShowsAnErrorAndSeedsAFreshApp() {
        val before = app.containerGeneration.value

        app.recoverWithBackup("{not a backup}")

        compose.waitUntil(10_000) { app.containerGeneration.value > before }
        assertNotNull(app.recoveryError.value)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Backup did not import").fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking { assertEquals(57, app.container.database.dao().exerciseCount()) }
    }
}
