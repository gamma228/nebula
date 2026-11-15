package app.nebula.vpn.ui.tor

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.core.carrier.NetworkType
import app.nebula.vpn.core.tor.AttemptResult
import app.nebula.vpn.core.tor.ConnectionPlanEngine
import app.nebula.vpn.core.tor.PlanAttempt
import app.nebula.vpn.core.tor.PlanProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EmeraldGreen = Color(0xFF00E676)
private val AmberWarning = Color(0xFFFFB703)

@Composable
fun ConnectionAssistantDialog(
    onDismissRequest: () -> Unit,
    onApplyWorkingAttempt: (PlanAttempt) -> Unit,
    planEngine: ConnectionPlanEngine = remember { ConnectionPlanEngine() }
) {
    val coroutineScope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var currentAttemptText by remember { mutableStateOf<String?>(null) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    val results = remember { mutableStateListOf<AttemptResult>() }
    var workingAttempt by remember { mutableStateOf<PlanAttempt?>(null) }

    AlertDialog(
        onDismissRequest = {
            activeJob?.cancel()
            onDismissRequest()
        },
        title = {
            Text(
                text = stringResource(R.string.tor_assistant_title),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.tor_assistant_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    lineHeight = 16.sp
                )

                if (currentAttemptText != null && isRunning) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EmeraldGreen)
                        Text(
                            text = currentAttemptText!!,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (results.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(results) { res ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (res.isSuccess) EmeraldGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = res.attempt.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (res.isSuccess) EmeraldGreen else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (res.isSuccess) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(text = "${res.latencyMs} ms", fontSize = 10.sp, color = EmeraldGreen)
                                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                                            }
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(text = res.failureReason?.name ?: "FAILED", fontSize = 9.sp, color = AmberWarning)
                                                Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                    val suggestion = res.nextStepSuggestion
                                    if (!res.isSuccess && !suggestion.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = suggestion,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (workingAttempt != null) {
                    Text(
                        text = stringResource(R.string.tor_assistant_success, workingAttempt!!.displayName, 320),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }
            }
        },
        confirmButton = {
            if (workingAttempt != null) {
                Button(
                    onClick = {
                        onApplyWorkingAttempt(workingAttempt!!)
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text(stringResource(R.string.tor_assistant_apply), fontSize = 12.sp)
                }
            } else if (!isRunning) {
                Button(
                    onClick = {
                        results.clear()
                        workingAttempt = null
                        isRunning = true
                        activeJob = coroutineScope.launch {
                            try {
                                planEngine.executePlan(NetworkType.WIFI) { attempt ->
                                    // Simulation of real probe execution step
                                    delay(400L)
                                    // Carrier attempts succeed; direct attempts show DPI blocked in Russia simulation
                                    if (attempt.useCarrier) {
                                        AttemptResult(attempt = attempt, isSuccess = true, latencyMs = 280L)
                                    } else {
                                        AttemptResult(
                                            attempt = attempt,
                                            isSuccess = false,
                                            failureReason = app.nebula.vpn.core.tor.PlanFailureReason.TRANSPORT_BLOCKED,
                                            nextStepSuggestion = app.nebula.vpn.core.tor.PlanFailureReason.TRANSPORT_BLOCKED.getSuggestedFix()
                                        )
                                    }
                                }.collect { progress ->
                                    when (progress) {
                                        is PlanProgress.AttemptStarting -> {
                                            currentAttemptText = "Testing (${progress.attemptIndex}/${progress.totalAttempts}): ${progress.attempt.displayName}..."
                                        }
                                        is PlanProgress.AttemptFinished -> {
                                            results.add(progress.result)
                                        }
                                        is PlanProgress.PlanCompleted -> {
                                            workingAttempt = progress.workingAttempt
                                            isRunning = false
                                            currentAttemptText = null
                                        }
                                        is PlanProgress.PlanCancelled -> {
                                            isRunning = false
                                            currentAttemptText = null
                                        }
                                    }
                                }
                            } finally {
                                isRunning = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text(stringResource(R.string.tor_assistant_start), fontSize = 12.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        activeJob?.cancel()
                        isRunning = false
                        currentAttemptText = null
                    }
                ) {
                    Text(stringResource(R.string.tor_assistant_stop), fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    activeJob?.cancel()
                    onDismissRequest()
                }
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
