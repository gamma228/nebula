package app.nebula.vpn.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun LatencySparkline(
    history: List<Int>,
    color: Color = Color(0xFF00D084),
    modifier: Modifier = Modifier
        .width(44.dp)
        .height(16.dp)
) {
    if (history.size < 2) return

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val maxPing = history.maxOrNull()?.toFloat()?.coerceAtLeast(100f) ?: 100f
        val minPing = history.minOrNull()?.toFloat()?.coerceAtLeast(0f) ?: 0f
        val range = max(1f, maxPing - minPing)

        val stepX = width / (history.size - 1)
        val path = Path()

        history.forEachIndexed { index, ping ->
            val x = index * stepX
            val normalizedY = 1f - ((ping - minPing) / range)
            val y = normalizedY * (height - 4f) + 2f

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw line
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw end dot
        val lastPing = history.last()
        val lastY = (1f - ((lastPing - minPing) / range)) * (height - 4f) + 2f
        drawCircle(
            color = color,
            radius = 2.dp.toPx(),
            center = Offset(width, lastY)
        )
    }
}
