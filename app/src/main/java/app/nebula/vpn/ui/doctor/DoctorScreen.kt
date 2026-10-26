package app.nebula.vpn.ui.doctor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.ui.theme.StatusGreen
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import app.nebula.vpn.ui.theme.EmeraldGreen
import app.nebula.vpn.ui.theme.StatusRed

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import app.nebula.vpn.ui.theme.NebulaTheme

@Composable
fun DoctorScreen(
    viewModel: DoctorViewModel,
    modifier: Modifier = Modifier
) {
    val checks by viewModel.checks.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val verdict by viewModel.verdict.collectAsState()
    val context = LocalContext.current

    val colors = NebulaTheme.colors
    val spacing = NebulaTheme.spacing
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.doctor_title),
                style = typography.displayLarge.copy(fontSize = 24.sp),
                color = colors.textPrimary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                }
                IconButton(
                    onClick = { viewModel.runDiagnostics() },
                    enabled = !isRunning,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rerun",
                        tint = colors.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.sm))

        if (verdict != null) {
            val isSuccess = verdict!!.isSuccess
            val verdictBorder = if (isSuccess) colors.statusOk else colors.statusError
            val verdictBg = if (isSuccess) colors.statusOk.copy(alpha = 0.12f) else colors.statusError.copy(alpha = 0.12f)
            Card(
                colors = CardDefaults.cardColors(containerColor = verdictBg),
                shape = shapes.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, verdictBorder.copy(alpha = 0.5f), shapes.card)
            ) {
                Row(
                    modifier = Modifier.padding(spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isSuccess) colors.statusOk else colors.statusError,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Column {
                        Text(
                            text = verdict!!.title,
                            style = typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = verdict!!.description,
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(spacing.sm))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.weight(1f)
        ) {
            items(checks) { check ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    shape = shapes.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.borderSubtle, shapes.card)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (check.isPassed) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (check.isPassed) colors.statusOk else colors.statusError,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = check.title,
                                style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = check.details,
                                style = typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }

                        if (check.canFix && check.fixActionId != null) {
                            Spacer(modifier = Modifier.width(spacing.xs))
                            Button(
                                onClick = { viewModel.applyFix(check.fixActionId) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = 0.2f)),
                                shape = shapes.button,
                                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                            ) {
                                Text(
                                    text = when (check.fixActionId) {
                                        "DISABLE_IPV6" -> stringResource(R.string.doctor_fix_ipv6)
                                        "BLOCK_QUIC" -> stringResource(R.string.doctor_fix_quic)
                                        else -> stringResource(R.string.fix_issue)
                                    },
                                    style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        Button(
            onClick = {
                val report = viewModel.generateRedactedDiagnosticsBundle()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Nebula Diagnostic Report", report))
                Toast.makeText(context, context.getString(R.string.copy_diagnostic_report), Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
            shape = shapes.button,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .border(1.dp, colors.borderSubtle, shapes.button)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = colors.primary)
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(
                text = stringResource(R.string.copy_diagnostic_report),
                style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
        }
    }
}
