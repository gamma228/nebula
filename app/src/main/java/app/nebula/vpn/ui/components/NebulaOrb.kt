package app.nebula.vpn.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.ui.theme.LocalReduceMotion
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun NebulaOrb(
    state: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    val haptic = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val isConnected = state is ConnectionState.Connected
    val isConnecting = state is ConnectionState.Connecting || state is ConnectionState.Reconnecting
    val isDisconnecting = state is ConnectionState.Disconnecting
    val isError = state is ConnectionState.Failed || state is ConnectionState.Error

    val colors = NebulaTheme.colors

    val statusColor = when {
        isConnected -> colors.primary
        isConnecting -> colors.accent
        isDisconnecting -> colors.statusWarning
        isError -> colors.statusError
        else -> colors.textMuted
    }

    val glowColor = when {
        isConnected -> colors.orbGlow
        isConnecting -> colors.accent
        isDisconnecting -> colors.statusWarning
        isError -> colors.statusError
        else -> Color.Transparent
    }

    // Interactive scale animation
    val scaleFactor by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "OrbPressScale"
    )

    // Continuous animations (disabled if reduceMotion is active)
    val infiniteTransition = rememberInfiniteTransition(label = "OrbInfinite")
    
    val pulseScale by if (!reduceMotion && (isConnected || isConnecting)) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = if (isConnected) 1.06f else 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isConnected) 2400 else 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "OrbPulse"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(1.0f) }
    }

    val rotationAngle by if (!reduceMotion && isConnecting) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing)
            ),
            label = "OrbRotate"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val accessibilityDesc = when (state) {
        is ConnectionState.Connected -> "VPN Connected. Tap to disconnect."
        is ConnectionState.Connecting -> "VPN Connecting. Tap to cancel."
        is ConnectionState.Disconnecting -> "VPN Disconnecting."
        is ConnectionState.Failed, is ConnectionState.Error -> "VPN Connection Failed. Tap to retry."
        is ConnectionState.Disconnected -> "VPN Disconnected. Tap to connect."
        else -> "VPN Status"
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(scaleFactor)
            .semantics {
                role = Role.Button
                contentDescription = accessibilityDesc
            }
            .focusable(interactionSource = interactionSource)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, colors.accent, CircleShape)
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer halo / aura
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.minDimension / 2f) * 0.95f

            if (glowColor != Color.Transparent) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = if (isConnected) 0.35f else 0.45f),
                            glowColor.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * pulseScale
                    ),
                    radius = radius * pulseScale,
                    center = center
                )
            }

            // Outer segmented orbital track
            val outerTrackRadius = radius * 0.88f
            rotate(rotationAngle, pivot = center) {
                drawCircle(
                    color = statusColor.copy(alpha = if (isConnected || isConnecting) 0.5f else 0.15f),
                    radius = outerTrackRadius,
                    center = center,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 16f), 0f)
                    )
                )
            }

            // Inner orbit particles
            val innerTrackRadius = radius * 0.76f
            rotate(-rotationAngle * 1.5f, pivot = center) {
                drawCircle(
                    color = statusColor.copy(alpha = if (isConnecting) 0.7f else 0.25f),
                    radius = innerTrackRadius,
                    center = center,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 24f), 0f)
                    )
                )
            }
        }

        // Center Sphere Reactor
        val coreSize = size * 0.58f
        Box(
            modifier = Modifier
                .size(coreSize)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.surfaceElevated,
                            colors.surface,
                            colors.background
                        ),
                        radius = 240f
                    )
                )
                .border(
                    width = if (isConnected) 2.dp else 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            statusColor.copy(alpha = if (isConnected) 0.9f else 0.4f),
                            statusColor.copy(alpha = 0.1f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val icon = when {
                isConnected -> Icons.Filled.Lock
                isConnecting -> Icons.Filled.Refresh
                isError -> Icons.Filled.Warning
                else -> Icons.Filled.PowerSettingsNew
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isConnected) colors.textPrimary else statusColor,
                modifier = Modifier
                    .size(coreSize * 0.42f)
                    .then(
                        if (isConnecting && !reduceMotion) {
                            Modifier.scale(pulseScale)
                        } else Modifier
                    )
            )
        }
    }
}

@Preview(name = "Orb Disconnected", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewNebulaOrbDisconnected() {
    NebulaTheme {
        NebulaOrb(
            state = ConnectionState.Disconnected,
            onClick = {}
        )
    }
}

@Preview(name = "Orb Connected", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewNebulaOrbConnected() {
    NebulaTheme {
        NebulaOrb(
            state = ConnectionState.Connected("US Edge 01", 1000L),
            onClick = {}
        )
    }
}
