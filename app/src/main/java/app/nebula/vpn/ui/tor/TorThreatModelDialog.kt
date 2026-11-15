package app.nebula.vpn.ui.tor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.nebula.vpn.R
import app.nebula.vpn.ui.theme.NebulaTheme

/**
 * First-time Tor selection one-time dialog with simplified threat model.
 * Milestones T8 & T1 Hard Rules:
 * - What Tor mode does (onion multi-hop routing, hides destinations from ISP)
 * - What it does NOT do (device IDs, app logins, correlation)
 * - Experimental notice, slower than standard VPN, no UDP/QUIC/calls
 * - Explicit required "I understand & Proceed" confirmation before activating Tor.
 */
@Composable
fun TorThreatModelDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.tor_dialog_title),
                            style = typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.tor_dialog_subtitle),
                            style = typography.labelSmall,
                            color = colors.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ThreatModelItem(
                    title = stringResource(R.string.tor_dialog_item_1_title),
                    description = stringResource(R.string.tor_dialog_item_1_desc),
                    badgeColor = colors.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                ThreatModelItem(
                    title = stringResource(R.string.tor_dialog_item_2_title),
                    description = stringResource(R.string.tor_dialog_item_2_desc),
                    badgeColor = colors.statusOk
                )

                Spacer(modifier = Modifier.height(10.dp))

                ThreatModelItem(
                    title = stringResource(R.string.tor_dialog_item_3_title),
                    description = stringResource(R.string.tor_dialog_item_3_desc),
                    badgeColor = colors.statusWarning
                )

                Spacer(modifier = Modifier.height(10.dp))

                ThreatModelItem(
                    title = stringResource(R.string.tor_dialog_item_4_title),
                    description = stringResource(R.string.tor_dialog_item_4_desc),
                    badgeColor = colors.statusError
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = shapes.button,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tor_dialog_understand_btn),
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreatModelItem(
    title: String,
    description: String,
    badgeColor: Color
) {
    val colors = NebulaTheme.colors
    val typography = NebulaTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceElevated, RoundedCornerShape(10.dp))
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(badgeColor, RoundedCornerShape(4.dp))
                .align(Alignment.Top)
                .padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}
