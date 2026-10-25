package app.nebula.vpn.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun NebulaEmptyState(
    title: String,
    description: String,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val spacing = NebulaTheme.spacing
    val typography = NebulaTheme.typography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Geometric Vector Illustration in Canvas
        Canvas(modifier = Modifier.size(100.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            // Outer dashed ring
            drawCircle(
                color = colors.primary.copy(alpha = 0.25f),
                radius = radius * 0.9f,
                center = center,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )

            // Inner orbital ring
            drawCircle(
                color = colors.accent.copy(alpha = 0.35f),
                radius = radius * 0.6f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Core center node
            drawCircle(
                color = colors.primary,
                radius = radius * 0.2f,
                center = center
            )

            // Crosshair markers
            drawLine(
                color = colors.borderSubtle,
                start = Offset(center.x - radius, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = colors.borderSubtle,
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y + radius),
                strokeWidth = 1.dp.toPx()
            )
        }

        Spacer(modifier = Modifier.height(spacing.lg))

        Text(
            text = title,
            style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(spacing.xs))

        Text(
            text = description,
            style = typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )

        if (buttonText != null && onButtonClick != null) {
            Spacer(modifier = Modifier.height(spacing.lg))

            Button(
                onClick = onButtonClick,
                shape = shapes.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = buttonText,
                    style = typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Preview(name = "Empty State Preview", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewNebulaEmptyState() {
    NebulaTheme {
        NebulaEmptyState(
            title = "No Server Profiles",
            description = "Import subscription URL or scan QR code to connect.",
            buttonText = "Add Server",
            onButtonClick = {}
        )
    }
}
