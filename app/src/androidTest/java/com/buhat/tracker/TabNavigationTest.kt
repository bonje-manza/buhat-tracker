package com.buhat.tracker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val app = ApplicationProvider.getApplicationContext<BuhatApplication>()

    @Before
    fun setUp() {
        app.resetForTest()
        compose.waitForIdle()
    }

    @Test
    fun tabsSwitchScreens() {
        compose.onNodeWithTag("screen-Home").assertIsDisplayed()
        listOf("Workouts", "Active", "Progress", "Home").forEach { tab ->
            compose.onNodeWithTag("tab-$tab").performClick()
            compose.onNodeWithTag("tab-$tab").assertIsSelected()
            compose.onNodeWithTag("screen-$tab").assertIsDisplayed()
        }
    }

    @Test
    fun selectedTabSurvivesRecreate() {
        compose.onNodeWithTag("tab-Progress").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("screen-Progress").assertIsDisplayed()
    }
}
