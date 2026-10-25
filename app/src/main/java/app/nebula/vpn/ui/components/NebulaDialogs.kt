package app.nebula.vpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun NebulaBottomSheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val colors = NebulaTheme.colors
    val spacing = NebulaTheme.spacing
    val typography = NebulaTheme.typography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm)
    ) {
        // Drag handle pill
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.borderSubtle)
        )

        Spacer(modifier = Modifier.height(spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close dialog",
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun NebulaConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "Cancel",
    isDestructive: Boolean = false
) {
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
        },
        text = {
            Text(
                text = message,
                style = typography.bodyMedium,
                color = colors.textSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = shapes.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) colors.statusError else colors.primary,
                    contentColor = colors.onPrimary
                )
            ) {
                Text(
                    text = confirmText,
                    style = typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = shapes.button
            ) {
                Text(
                    text = dismissText,
                    style = typography.titleSmall,
                    color = colors.textSecondary
                )
            }
        },
        containerColor = colors.surface,
        shape = shapes.card
    )
}

@Composable
fun NebulaSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    Snackbar(
        modifier = modifier
            .padding(12.dp)
            .border(1.dp, colors.borderSubtle, shapes.card),
        shape = shapes.card,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        action = snackbarData.visuals.actionLabel?.let { actionLabel ->
            {
                TextButton(
                    onClick = { snackbarData.performAction() }
                ) {
                    Text(
                        text = actionLabel,
                        style = typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                }
            }
        }
    ) {
        Text(
            text = snackbarData.visuals.message,
            style = typography.bodyMedium,
            color = colors.textPrimary
        )
    }
}

@Preview(name = "Bottom Sheet Header", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewNebulaBottomSheetHeader() {
    NebulaTheme {
        NebulaBottomSheetHeader(
            title = "Add Routing Rule",
            subtitle = "Paste domain or IP CIDR to route",
            onClose = {}
        )
    }
}
