package app.nebula.vpn.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.core.diagnostics.StabilityDiagnosticsCollector
import app.nebula.vpn.ui.theme.EmeraldGreen
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun OemBackgroundGuideDialog(
    onDismiss: () -> Unit,
    onOpenSettings: (Context) -> Unit
) {
    val context = LocalContext.current
    val colors = NebulaTheme.colors
    val spacing = NebulaTheme.spacing
    val typography = NebulaTheme.typography
    val shapes = NebulaTheme.shapes

    val detectedOem = StabilityDiagnosticsCollector.detectOemSkin()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(spacing.xs))
                Text(
                    text = stringResource(R.string.oem_dialog_title),
                    style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.oem_dialog_desc, detectedOem),
                    style = typography.bodyMedium,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(spacing.sm))

                // Step 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .padding(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.oem_step_battery),
                        style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(spacing.xxs))

                // Step 2
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .padding(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.oem_step_autostart),
                        style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(spacing.xxs))

                // Step 3
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .padding(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.oem_step_lock_recents),
                        style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.textPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onOpenSettings(context)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = shapes.button
            ) {
                Icon(Icons.Default.Launch, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(spacing.xxs))
                Text(
                    text = stringResource(R.string.oem_action_open_settings),
                    style = typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.onPrimary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.oem_action_dismiss),
                    style = typography.bodySmall,
                    color = colors.textMuted
                )
            }
        },
        containerColor = colors.surface,
        shape = shapes.card
    )
}
