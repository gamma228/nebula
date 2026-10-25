package app.nebula.vpn.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun NebulaTrafficChart(
    downloadHistory: List<Long>,
    uploadHistory: List<Long>,
    modifier: Modifier = Modifier
) {
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val spacing = NebulaTheme.spacing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(shapes.card)
            .background(colors.surface)
            .border(1.dp, colors.borderSubtle, shapes.card)
            .padding(horizontal = spacing.md, vertical = spacing.sm)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
            val width = size.width
            val height = size.height

            // Grid lines
            val gridLines = 3
            for (i in 1..gridLines) {
                val y = height * (i.toFloat() / (gridLines + 1))
                drawLine(
                    color = colors.borderSubtle,
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
                    val y = height - (value.toFloat() / overallMax) * (height * 0.85f)
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        val prevX = (index - 1) * stepX
                        val prevY = height - (data[index - 1].toFloat() / overallMax) * (height * 0.85f)
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
                        colors.statusOk.copy(alpha = 0.20f),
                        Color.Transparent
                    )
                )
            )
            drawPath(
                path = downPath,
                color = colors.statusOk,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw Upload Line (Electric Violet / Accent)
            if (uploadHistory.size >= 2) {
                val upPath = buildLinePath(uploadHistory)
                drawPath(
                    path = upPath,
                    color = colors.primary,
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
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
