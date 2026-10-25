package app.nebula.vpn.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun NebulaSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val spacing = NebulaTheme.spacing
    val typography = NebulaTheme.typography

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shapes.card)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, shapes.card)
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                val tabBgColor by animateColorAsState(
                    targetValue = if (isSelected) colors.surfaceElevated else Color.Transparent,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "TabBgColor"
                )
                val tabTextColor by animateColorAsState(
                    targetValue = if (isSelected) colors.primary else colors.textSecondary,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "TabTextColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(shapes.chip)
                        .background(tabBgColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(1.dp, colors.borderSubtle, shapes.chip)
                            } else Modifier
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTabSelected(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = typography.titleSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = tabTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun NebulaFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null
) {
    val haptic = LocalHapticFeedback.current
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val spacing = NebulaTheme.spacing
    val typography = NebulaTheme.typography

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val chipBgColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary.copy(alpha = 0.18f) else colors.surface,
        label = "FilterChipBg"
    )
    val chipBorderColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.primary
            isFocused -> colors.accent
            else -> colors.borderSubtle
        },
        label = "FilterChipBorder"
    )
    val chipTextColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else colors.textSecondary,
        label = "FilterChipText"
    )

    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shapes.chip)
            .background(chipBgColor)
            .border(1.dp, chipBorderColor, shapes.chip)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = chipTextColor
        )

        if (badgeCount != null && badgeCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.primary else colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$badgeCount",
                    style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) colors.onPrimary else colors.textPrimary
                )
            }
        }
    }
}

@Composable
fun NebulaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val colors = NebulaTheme.colors

    Switch(
        checked = checked,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCheckedChange(it)
        },
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onPrimary,
            checkedTrackColor = colors.primary,
            uncheckedThumbColor = colors.textMuted,
            uncheckedTrackColor = colors.surfaceElevated,
            uncheckedBorderColor = colors.borderSubtle
        )
    )
}

@Preview(name = "Segmented Tabs Preview", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewNebulaSegmentedTabs() {
    NebulaTheme {
        NebulaSegmentedTabs(
            tabs = listOf("Rules", "Apps", "Presets", "Test"),
            selectedIndex = 0,
            onTabSelected = {}
        )
    }
}
