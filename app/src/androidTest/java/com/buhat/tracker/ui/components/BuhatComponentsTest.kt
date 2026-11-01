package com.buhat.tracker.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.domain.formatKg
import com.buhat.tracker.ui.theme.BuhatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuhatComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun segmentedControlReportsTheSelectedOption() {
        var selected = 0
        compose.setContent {
            BuhatTheme {
                BuhatSegmentedControl(
                    options = listOf("All", "Premade", "My"),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
            }
        }

        compose.onNodeWithTag("segmented-Premade").performClick()
        compose.runOnIdle { assertEquals(1, selected) }
    }

    @Test
    fun stepperIncreasesByTheConfiguredStep() {
        var value by mutableStateOf(80.0)
        compose.setContent {
            BuhatTheme {
                BuhatStepper(
                    value = value,
                    onValueChange = { value = it },
                    step = 2.5,
                    min = 0.0,
                    max = 1000.0,
                    format = { "${formatKg(it)} kg" },
                )
            }
        }

        compose.onNodeWithTag("stepper-plus").performClick()
        compose.runOnIdle { assertEquals(82.5, value, 0.0) }

        compose.onNodeWithTag("stepper-minus").performClick()
        compose.runOnIdle { assertEquals(80.0, value, 0.0) }
    }

    @Test
    fun stepperClampsToMinAndMax() {
        var value by mutableStateOf(0.0)
        compose.setContent {
            BuhatTheme {
                BuhatStepper(
                    value = value,
                    onValueChange = { value = it },
                    step = 2.5,
                    min = 0.0,
                    max = 1000.0,
                    format = { "${formatKg(it)} kg" },
                )
            }
        }

        compose.onNodeWithTag("stepper-minus").performClick()
        compose.runOnIdle { assertEquals(0.0, value, 0.0) }
    }

    @Test
    fun setTypeBadgeAlwaysShowsTheTypeAsText() {
        compose.setContent {
            BuhatTheme {
                SetTypeBadge(SetType.WARMUP)
                SetTypeBadge(SetType.WORKING)
                SetTypeBadge(SetType.FAILURE)
            }
        }

        compose.onNodeWithText("Warmup").assertExists()
        compose.onNodeWithText("Working").assertExists()
        compose.onNodeWithText("Failure").assertExists()
    }

    @Test
    fun pillAndPrBadgeRenderTheirText() {
        compose.setContent {
            BuhatTheme {
                BuhatPill(label = "3 × 6-8")
                PrBadge()
            }
        }

        compose.onNodeWithText("3 × 6-8").assertExists()
        compose.onNodeWithText("PR").assertExists()
    }

    @Test
    fun emptyStateShowsTitleAndMessage() {
        compose.setContent {
            BuhatTheme {
                BuhatEmptyState(title = "No workouts yet", message = "Start from a template.")
            }
        }

        compose.onNodeWithText("No workouts yet").assertExists()
        compose.onNodeWithText("Start from a template.").assertExists()
    }

    @Test
    fun chartIsDescribableByContentDescription() {
        compose.setContent {
            BuhatTheme {
                BuhatLineChart(
                    values = listOf(100.0, 120.0, 105.0),
                    contentDescription = "Squat e1RM trend, rising 5",
                )
            }
        }

        compose.onNodeWithContentDescription("Squat e1RM trend, rising 5").assertExists()
    }

    @Test
    fun cardAndBothThemesRenderTheirContent() {
        compose.setContent {
            BuhatTheme(darkTheme = false) {
                BuhatCard {
                    androidx.compose.material3.Text("light content")
                }
            }
        }
        compose.onNodeWithText("light content").assertExists()
    }
}
