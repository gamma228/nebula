package app.nebula.vpn.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.ui.theme.NebulaTheme
import java.util.Locale

@Composable
fun NebulaTrafficChart(
    downloadHistory: List<Long>,
    uploadHistory: List<Long>,
    modifier: Modifier = Modifier
) {
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val spacing = NebulaTheme.spacing
    val typography = NebulaTheme.typography

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(115.dp)
            .clip(shapes.card)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, shapes.card)
            .padding(horizontal = spacing.md, vertical = spacing.xs)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(95.dp).align(Alignment.BottomCenter)) {
            val width = size.width
            val height = size.height

            // Grid lines
            val gridLines = 3
            for (i in 1..gridLines) {
                val y = height * (i.toFloat() / (gridLines + 1))
                drawLine(
                    color = colors.borderSubtle.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }

            if (downloadHistory.size < 2) return@Canvas

            val maxDown = (downloadHistory.maxOrNull() ?: 1L).coerceAtLeast(1024L).toFloat()
            val maxUp = (uploadHistory.maxOrNull() ?: 1L).coerceAtLeast(1024L).toFloat()
            val overallMax = maxOf(maxDown, maxUp)

            fun buildLinePath(data: List<Long>): Path {
                val path = Path()
                val stepX = width / (data.size - 1)

                data.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = height - (value.toFloat() / overallMax) * (height * 0.80f)
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        val prevX = (index - 1) * stepX
                        val prevY = height - (data[index - 1].toFloat() / overallMax) * (height * 0.80f)
                        val midX = (prevX + x) / 2f
                        path.cubicTo(midX, prevY, midX, y, x, y)
                    }
                }
                return path
            }

            fun buildAreaPath(linePath: Path, data: List<Long>): Path {
                val areaPath = Path()
                areaPath.addPath(linePath)
                val stepX = width / (data.size - 1)
                areaPath.lineTo(width, height)
                areaPath.lineTo(0f, height)
                areaPath.close()
                return areaPath
            }

            // Draw Download Area & Line (Status Green / Primary)
            val downPath = buildLinePath(downloadHistory)
            val downArea = buildAreaPath(downPath, downloadHistory)
            drawPath(
                path = downArea,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.statusOk.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                )
            )
            drawPath(
                path = downPath,
                color = colors.statusOk,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw Upload Line (Primary / Accent)
            if (uploadHistory.size >= 2) {
                val upPath = buildLinePath(uploadHistory)
                drawPath(
                    path = upPath,
                    color = colors.primary,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Live chart overlay badge (Legends & Peak indicator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "30s LIVE",
                style = typography.labelSmall,
                color = colors.textMuted,
                fontSize = 9.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.statusOk))
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = "DL", style = typography.labelSmall, color = colors.statusOk, fontSize = 9.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.primary))
                Spacer(modifier = Modifier.width(3.dp))
                Text(text = "UL", style = typography.labelSmall, color = colors.primary, fontSize = 9.sp)
                val peak = maxOf(downloadHistory.maxOrNull() ?: 0L, uploadHistory.maxOrNull() ?: 0L)
                if (peak > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Max: ${formatChartSpeed(peak)}",
                        style = typography.labelSmall,
                        color = colors.textMuted,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

private fun formatChartSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 KB/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1000) {
        return String.format(Locale.US, "%.1f KB/s", kb)
    }
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB/s", mb)
}

@Preview(name = "Traffic Chart Preview", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewNebulaTrafficChart() {
    NebulaTheme {
        NebulaTrafficChart(
            downloadHistory = listOf(100L, 500L, 1200L, 8000L, 15000L, 22000L, 18000L, 34000L, 42000L, 38000L),
            uploadHistory = listOf(50L, 100L, 300L, 1500L, 2000L, 4000L, 3500L, 6000L, 5500L, 7000L)
        )
    }
}
