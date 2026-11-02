package com.buhat.tracker.ui

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.buhat.tracker.BuhatApplication
import com.buhat.tracker.data.seededId
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.ui.screens.ActiveWorkoutScreen
import com.buhat.tracker.ui.theme.BuhatTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class RestPermissionTest {
    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<BuhatApplication>()

    @After
    fun cancelAlarm() = runBlocking {
        app.container.repository.clearRest()
        app.container.restTimer.cancel()
    }

    @Test
    fun restoringAnUnpromptedRestRequestsPermissionOnlyOnce() {
        app.resetForTest()
        val repository = app.container.repository
        runBlocking {
            val session = repository.startSession(seededId("template", "Push Day A"))
            val exercise = repository.sessionExercises(session).first().first()
            repository.logSet(exercise.id, SetType.WORKING, 80.0, 8, 2)
        }

        // Substitute only Android's permission boundary, keeping the real screen and stores.
        val requests = AtomicInteger()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                assertEquals(Manifest.permission.POST_NOTIFICATIONS, input)
                requests.incrementAndGet()
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        val visible = mutableStateOf(true)
        compose.setContent {
            val baseContext = LocalContext.current
            val context = object : ContextWrapper(baseContext) {
                override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                    if (permission == Manifest.permission.POST_NOTIFICATIONS) {
                        PackageManager.PERMISSION_DENIED
                    } else {
                        super.checkPermission(permission, pid, uid)
                    }
            }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides owner,
            ) {
                BuhatTheme {
                    if (visible.value) {
                        ActiveWorkoutScreen(repository, app.container.restTimer, onStartWorkout = {})
                    }
                }
            }
        }

        compose.waitUntil(5_000) { requests.get() == 1 }
        assertTrue(runBlocking { app.container.settingsStore.notificationPermissionRequested.first() })
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("rest-card")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, requests.get())
    }
}
