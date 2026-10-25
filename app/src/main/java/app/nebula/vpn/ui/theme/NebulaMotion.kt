package app.nebula.vpn.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class NebulaMotion(
    val durationPress: Int = 120,
    val durationSelection: Int = 180,
    val durationCard: Int = 250,
    val durationState: Int = 300,
    val easingStandard: Easing = FastOutSlowInEasing,
    val easingEnter: Easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
)

val LocalNebulaMotion = staticCompositionLocalOf { NebulaMotion() }
val LocalReduceMotion = staticCompositionLocalOf { false }

fun <T> nebulaTween(
    durationMillis: Int,
    reduceMotion: Boolean,
    easing: Easing = FastOutSlowInEasing
) = if (reduceMotion) {
    tween<T>(durationMillis = 0, easing = LinearEasing)
} else {
    tween<T>(durationMillis = durationMillis, easing = easing)
}
