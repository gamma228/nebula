package app.nebula.vpn.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

data class SpeedSample(
    val downBps: Long,
    val upBps: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun TrafficChart(
    samples: List<SpeedSample>,
    currentDownBps: Long,
    currentUpBps: Long,
    modifier: Modifier = Modifier
) {
    val downColor = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    // Calculate max value for auto-scaling
    val maxSpeed = max(
        100 * 1024L, // Minimum 100 KB/s ceiling
        samples.maxOfOrNull { max(it.downBps, it.upBps) } ?: 100 * 1024L
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with legend and current speeds
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Network Traffic",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendItem(
                        label = "Down: ${formatSpeed(currentDownBps)}",
                        color = downColor
                    )
                    LegendItem(
                        label = "Up: ${formatSpeed(currentUpBps)}",
                        color = upColor
                    )
                    Text(
                        text = "Peak: ${formatSpeed(maxSpeed)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Chart Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                val width = size.width
                val height = size.height

                // Draw 3 horizontal grid lines (0%, 50%, 100%)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, 0f),
                    end = Offset(width, 0f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, height / 2f),
                    end = Offset(width, height / 2f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, height),
                    end = Offset(width, height),
                    strokeWidth = 1f
                )

                if (samples.size < 2) return@Canvas

                val stepX = width / (samples.size - 1)

                // 1. Download Path (Area + Stroke)
                val downPath = Path()
                val downFillPath = Path()
                downFillPath.moveTo(0f, height)

                samples.forEachIndexed { index, sample ->
                    val x = index * stepX
                    val normalizedY = 1f - (sample.downBps.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
                    val y = normalizedY * height

                    if (index == 0) {
                        downPath.moveTo(x, y)
                        downFillPath.lineTo(x, y)
                    } else {
                        downPath.lineTo(x, y)
                        downFillPath.lineTo(x, y)
                    }
                }
                downFillPath.lineTo(width, height)
                downFillPath.close()

                drawPath(
                    path = downFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(downColor.copy(alpha = 0.25f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                drawPath(
                    path = downPath,
                    color = downColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // 2. Upload Path (Area + Stroke)
                val upPath = Path()
                val upFillPath = Path()
                upFillPath.moveTo(0f, height)

                samples.forEachIndexed { index, sample ->
                    val x = index * stepX
                    val normalizedY = 1f - (sample.upBps.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
                    val y = normalizedY * height

                    if (index == 0) {
                        upPath.moveTo(x, y)
                        upFillPath.lineTo(x, y)
                    } else {
                        upPath.lineTo(x, y)
                        upFillPath.lineTo(x, y)
                    }
                }
                upFillPath.lineTo(width, height)
                upFillPath.close()

                drawPath(
                    path = upFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(upColor.copy(alpha = 0.20f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                drawPath(
                    path = upPath,
                    color = upColor,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}
