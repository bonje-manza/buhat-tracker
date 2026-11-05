package com.buhat.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.buhat.tracker.domain.SetType
import com.buhat.tracker.ui.components.SetTypeBadge
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeContrastTest {
    @get:Rule val compose = createComposeRule()

    // Changing a foreground to a light accent on a light card must fail these checks.
    @Test fun lightThemeTextHasReadableContrast() = checkTextContrast(false)
    @Test fun darkThemeTextHasReadableContrast() = checkTextContrast(true)

    private fun checkTextContrast(dark: Boolean) {
        val failures = mutableListOf<String>()
        compose.setContent {
            BuhatTheme(darkTheme = dark) {
                val c = MaterialTheme.colorScheme
                for ((name, foreground, background) in listOf(
                    Triple("body", c.onBackground, c.background),
                    Triple("card", c.onSurface, c.surface),
                    Triple("muted", c.onSurfaceVariant, c.surfaceVariant),
                    Triple("link", c.primary, c.surface),
                    Triple("error", c.error, c.surface),
                    Triple("button", c.onPrimary, c.primary),
                    Triple("working", c.onSecondary, c.secondary),
                    Triple("warmup", c.onTertiary, c.tertiary),
                )) {
                    val ratio = contrast(foreground, background)
                    if (ratio < 4.5) failures.add("$name: $ratio")
                }
            }
        }
        compose.runOnIdle { assertTrue("Text contrast below 4.5: $failures", failures.isEmpty()) }
    }

    @Test fun failureBadgeIsReadableInLightTheme() {
        var background = Color.Unspecified
        compose.setContent {
            BuhatTheme(darkTheme = false) {
                background = MaterialTheme.colorScheme.error
                SetTypeBadge(SetType.FAILURE)
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Failure").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val ratio = contrast(layouts.single().layoutInput.style.color, background)
        assertTrue("Failure badge contrast $ratio is below 4.5", ratio >= 4.5)
    }

    private fun contrast(foreground: Color, background: Color): Double =
        ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())
}
