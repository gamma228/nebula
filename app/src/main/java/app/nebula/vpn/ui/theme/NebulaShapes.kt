package app.nebula.vpn.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import app.nebula.vpn.core.model.ButtonShape

@Immutable
data class NebulaShapes(
    val card: CornerBasedShape,
    val button: CornerBasedShape,
    val chip: CornerBasedShape,
    val bottomSheet: CornerBasedShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
)

fun createNebulaShapes(buttonShape: ButtonShape): NebulaShapes {
    return when (buttonShape) {
        ButtonShape.ROUNDED -> NebulaShapes(
            card = RoundedCornerShape(24.dp),
            button = RoundedCornerShape(50),
            chip = RoundedCornerShape(50),
            bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        )
        ButtonShape.SQUIRCLE -> NebulaShapes(
            card = RoundedCornerShape(16.dp),
            button = RoundedCornerShape(14.dp),
            chip = RoundedCornerShape(10.dp),
            bottomSheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        )
        ButtonShape.SHARP -> NebulaShapes(
            card = RoundedCornerShape(4.dp),
            button = RoundedCornerShape(4.dp),
            chip = RoundedCornerShape(2.dp),
            bottomSheet = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
        )
    }
}

val LocalNebulaShapes = staticCompositionLocalOf { createNebulaShapes(ButtonShape.SQUIRCLE) }
