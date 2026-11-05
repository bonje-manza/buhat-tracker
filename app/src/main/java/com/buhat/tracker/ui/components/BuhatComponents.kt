package com.buhat.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buhat.tracker.domain.SetType

val SetType.label: String
    get() = when (this) {
        SetType.WARMUP -> "Warmup"
        SetType.WORKING -> "Working"
        SetType.FAILURE -> "Failure"
    }

@Composable
fun BuhatCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun BuhatPill(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun Badge(
    label: String,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun BuhatBadge(label: String, modifier: Modifier = Modifier) {
    Badge(
        label = label,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    )
}

@Composable
fun PrBadge(modifier: Modifier = Modifier) {
    BuhatBadge(label = "PR", modifier = modifier)
}

@Composable
fun SetTypeBadge(type: SetType, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (background, content) = when (type) {
        SetType.WARMUP -> scheme.tertiary to scheme.onTertiary
        SetType.WORKING -> scheme.secondary to scheme.onSecondary
        SetType.FAILURE -> scheme.error to scheme.onError
    }
    // Set type is always text, never color alone.
    Badge(label = type.label, color = background, contentColor = content, modifier = modifier)
}

@Composable
fun BuhatSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.onSurface,
    selectedContentColor: Color = MaterialTheme.colorScheme.background,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerLow)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) selectedColor else Color.Transparent)
                    .selectable(
                        selected = selected,
                        role = androidx.compose.ui.semantics.Role.RadioButton,
                        onClick = { onSelect(index) },
                    )
                    .testTag("segmented-$option")
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .align(Alignment.CenterVertically),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    color = if (selected) selectedContentColor else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun BuhatStepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    step: Double,
    min: Double,
    max: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    tagPrefix: String = "",
) {
    val scheme = MaterialTheme.colorScheme
    val minusTag = if (tagPrefix.isEmpty()) "stepper-minus" else "$tagPrefix-minus"
    val plusTag = if (tagPrefix.isEmpty()) "stepper-plus" else "$tagPrefix-plus"
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepperButton(tag = minusTag, label = "−", scheme = scheme) {
            onValueChange((value - step).coerceIn(min, max))
        }
        Text(
            text = format(value),
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        StepperButton(tag = plusTag, label = "+", scheme = scheme) {
            onValueChange((value + step).coerceIn(min, max))
        }
    }
}

@Composable
private fun StepperButton(
    tag: String,
    label: String,
    scheme: androidx.compose.material3.ColorScheme,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(scheme.surfaceVariant)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
fun BuhatLineChart(
    values: List<Double>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (values.isEmpty()) return@Canvas
            val min = values.min()
            val max = values.max()
            val span = (max - min).takeIf { it > 0.0 }
            val verticalPadding = size.height * 0.1f
            val points = values.mapIndexed { index, value ->
                val x = size.width * index / (values.size - 1).coerceAtLeast(1)
                val fraction = span?.let { ((value - min) / it).toFloat() } ?: 0.5f
                val y = verticalPadding + (size.height - 2 * verticalPadding) * (1f - fraction)
                androidx.compose.ui.geometry.Offset(x, y)
            }
            if (points.size == 1) {
                drawCircle(color = scheme.primary, center = points.first(), radius = 4.dp.toPx())
                return@Canvas
            }
            val line = Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
            drawPath(
                path = Path().apply {
                    addPath(line)
                    lineTo(points.last().x, size.height)
                    lineTo(points.first().x, size.height)
                    close()
                },
                color = scheme.secondary.copy(alpha = 0.18f),
            )
            drawPath(
                path = line,
                color = scheme.secondary,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(color = scheme.primary, center = points.last(), radius = 4.dp.toPx())
        }
    }
}

@Composable
fun BuhatEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun BuhatChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.background
            else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** Full-screen editor shell: back arrow, title, optional action, no bottom bar. */@Composable
fun BuhatScreen(
    title: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Back" }
                    .testTag("back"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "←",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
        content()
    }
}
