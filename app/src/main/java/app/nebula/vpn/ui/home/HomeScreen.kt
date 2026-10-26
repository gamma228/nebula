package app.nebula.vpn.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.data.database.entity.SubscriptionEntity
import app.nebula.vpn.ui.components.NebulaOrb
import app.nebula.vpn.ui.components.NebulaStatusHeader
import app.nebula.vpn.ui.components.NebulaTrafficChart
import app.nebula.vpn.ui.theme.NebulaTheme
import java.text.DecimalFormat

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToServers: () -> Unit,
    onRequestConnect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val trafficStats by viewModel.trafficStats.collectAsState()
    val downloadHistory by viewModel.downloadHistory.collectAsState()
    val uploadHistory by viewModel.uploadHistory.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val activeSub by viewModel.activeSubscription.collectAsState()
    val isRefreshingSub by viewModel.isRefreshingSubscription.collectAsState()
    val durationSeconds by viewModel.connectionDurationSeconds.collectAsState()
    val allProfiles by viewModel.allProfiles.collectAsState()

    val colors = NebulaTheme.colors
    val spacing = NebulaTheme.spacing
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    val connectedState = connectionState as? ConnectionState.Connected
    val isConnected = connectionState is ConnectionState.Connected
    val isDegraded = connectedState?.isDegraded == true
    val isConnecting = connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Reconnecting
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Title & State Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xxs, bottom = spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NEBULA",
                    style = typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = colors.primary
                )
                Spacer(modifier = Modifier.width(spacing.xs))
                Box(
                    modifier = Modifier
                        .clip(shapes.chip)
                        .background(colors.surfaceElevated)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "CORE",
                        style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textSecondary
                    )
                }
            }

            // Connection State Pill Badge
            val (statusText, statusBg, statusFg) = when {
                isConnected && isDegraded -> Triple(stringResource(R.string.state_connected_degraded), colors.statusWarning.copy(alpha = 0.15f), colors.statusWarning)
                isConnected -> Triple(stringResource(R.string.state_connected), colors.statusOk.copy(alpha = 0.15f), colors.statusOk)
                isConnecting -> Triple(stringResource(R.string.state_connecting), colors.statusWarning.copy(alpha = 0.15f), colors.statusWarning)
                else -> Triple(stringResource(R.string.state_disconnected), colors.surfaceElevated, colors.textMuted)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(shapes.chip)
                    .background(statusBg)
                    .border(1.dp, statusFg.copy(alpha = 0.35f), shapes.chip)
                    .padding(horizontal = spacing.sm, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusFg)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = statusFg
                )
            }
        }

        // Live Telemetry Status Header (Upload, Download, Session, Ping)
        NebulaStatusHeader(
            downloadBps = trafficStats.downloadSpeedBps,
            uploadBps = trafficStats.uploadSpeedBps,
            durationSeconds = durationSeconds,
            pingMs = selectedProfile?.lastPingMs
        )

        Spacer(modifier = Modifier.height(spacing.lg))

        // Signature Quantum Orb Centerpiece
        NebulaOrb(
            state = connectionState,
            onClick = {
                if (isConnected || isConnecting) {
                    viewModel.toggleConnection()
                } else {
                    onRequestConnect()
                }
            },
            size = 190.dp
        )

        Spacer(modifier = Modifier.height(spacing.sm))

        // Security Protocol & State Sub-badge
        val proto = selectedProfile?.protocol?.name ?: "VLESS"
        Text(
            text = when {
                isConnected && isDegraded -> "✦ $proto · NO TRAFFIC (CHECK DOCTOR)"
                isConnected -> "✦ $proto · TUNNEL ENCRYPTED"
                else -> "READY TO CONNECT"
            },
            style = typography.labelMedium.copy(letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold),
            color = when {
                isConnected && isDegraded -> colors.statusWarning
                isConnected -> colors.primary
                else -> colors.textMuted
            }
        )

        Spacer(modifier = Modifier.height(spacing.lg))

        // Bandwidth Chart (Animated visibility when connected)
        AnimatedVisibility(visible = isConnected) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.md)) {
                Text(
                    text = stringResource(R.string.home_traffic_chart_title).uppercase(),
                    style = typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = colors.textMuted,
                    modifier = Modifier.padding(horizontal = spacing.xxs, vertical = spacing.xxs)
                )
                Spacer(modifier = Modifier.height(spacing.xxs))
                NebulaTrafficChart(
                    downloadHistory = downloadHistory,
                    uploadHistory = uploadHistory
                )
            }
        }

        // Active Node Card with 1-Tap Ping Test
        ActiveServerCard(
            profile = selectedProfile,
            onClick = onNavigateToServers,
            onPing = { viewModel.pingActiveServer() }
        )

        Spacer(modifier = Modifier.height(spacing.md))

        // Subscription Hub Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xxs, vertical = spacing.xxs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_section_subscription).uppercase(),
                style = typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = colors.textMuted
            )
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        if (activeSub != null) {
            ProviderSubscriptionCard(
                subscription = activeSub!!,
                isRefreshing = isRefreshingSub,
                onRefresh = { viewModel.refreshActiveSubscription() },
                onManage = onNavigateToServers
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = shapes.card,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.borderSubtle, shapes.card)
                    .clickable { onNavigateToServers() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(shapes.chip)
                                .background(colors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Column {
                            Text(
                                text = stringResource(R.string.home_add_subscription),
                                style = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.home_add_subscription_desc),
                                style = typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        // Quick Nodes Switcher
        if (allProfiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xxs, vertical = spacing.xxs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_quick_nodes).uppercase(),
                    style = typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = colors.textMuted
                )
                Text(
                    text = stringResource(R.string.home_all_nodes, allProfiles.size),
                    style = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primary,
                    modifier = Modifier.clickable { onNavigateToServers() }
                )
            }

            Spacer(modifier = Modifier.height(spacing.xs))

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                allProfiles.take(3).forEach { profile ->
                    val isSelected = profile.id == selectedProfile?.id
                    QuickServerItem(
                        profile = profile,
                        isSelected = isSelected,
                        onClick = { viewModel.selectProfile(profile.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.xl))
    }
}

@Composable
private fun ActiveServerCard(
    profile: ProxyProfile?,
    onClick: () -> Unit,
    onPing: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val spacing = NebulaTheme.spacing
    val typography = NebulaTheme.typography

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = shapes.card,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isFocused) colors.accent else colors.borderSubtle, shapes.card)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Flag emoji container
                val flag = getCountryFlag(profile?.name ?: "")
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = flag,
                        style = typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.width(spacing.sm))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.name ?: stringResource(R.string.home_no_server_selected),
                        style = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = profile?.protocol?.name ?: "AUTO",
                                style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(spacing.xs))

                        // Interactive Ping Badge
                        val ping = profile?.lastPingMs
                        val pingColor = when {
                            ping == null -> colors.textMuted
                            ping < 0 -> colors.statusError
                            ping < 120 -> colors.statusOk
                            ping < 250 -> colors.statusWarning
                            else -> colors.statusError
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(shapes.chip)
                                .background(pingColor.copy(alpha = 0.12f))
                                .border(1.dp, pingColor.copy(alpha = 0.25f), shapes.chip)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onPing()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(pingColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    ping == null -> "--"
                                    ping < 0 -> "Timeout"
                                    else -> "$ping ms"
                                },
                                style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = pingColor
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = stringResource(R.string.home_tap_to_change),
                tint = colors.textMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun ProviderSubscriptionCard(
    subscription: SubscriptionEntity,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onManage: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val spacing = NebulaTheme.spacing
    val typography = NebulaTheme.typography

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = shapes.card,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.borderSubtle, shapes.card)
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(shapes.chip)
                            .background(colors.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = subscription.title.ifBlank { "VPN Subscription" },
                        style = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rotateAnim by animateFloatAsState(
                        targetValue = if (isRefreshing) 360f else 0f,
                        animationSpec = tween(800, easing = LinearEasing),
                        label = "spin"
                    )
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRefresh()
                        },
                        enabled = !isRefreshing,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Subscription",
                            tint = colors.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(rotateAnim)
                        )
                    }
                }
            }

            // Quota bar & expiry
            val total = if (subscription.totalBytes == 107374182400L) null else subscription.totalBytes
            val used = (subscription.uploadBytes ?: 0L) + (subscription.downloadBytes ?: 0L)
            val hasQuota = total != null && total > 0L
            val hasExpiry = subscription.expireEpochSeconds != null && subscription.expireEpochSeconds > 0L

            if (hasQuota || hasExpiry || used > 0L) {
                Spacer(modifier = Modifier.height(spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasExpiry) {
                        val nowSec = System.currentTimeMillis() / 1000
                        val diffSec = subscription.expireEpochSeconds!! - nowSec
                        val daysRemaining = (diffSec / 86400).coerceAtLeast(0)
                        Text(
                            text = "${daysRemaining}d remaining",
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                    } else {
                        Text(
                            text = "No expiry",
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }

                    if (hasQuota) {
                        Text(
                            text = "${formatBytes(used)} / ${formatBytes(total!!)}",
                            style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary
                        )
                    } else if (used > 0L) {
                        Text(
                            text = "Used: ${formatBytes(used)}",
                            style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary
                        )
                    }
                }

                if (hasQuota) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val progress = (used.toFloat() / total!!.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = colors.primary,
                        trackColor = colors.surfaceElevated,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickServerItem(
    profile: ProxyProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val colors = NebulaTheme.colors
    val shapes = NebulaTheme.shapes
    val spacing = NebulaTheme.spacing
    val typography = NebulaTheme.typography

    val cardBgColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.surface,
        label = "QuickServerBg"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = shapes.card,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) colors.primary.copy(alpha = 0.45f) else Color.Transparent,
                shape = shapes.card
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                }
                Text(
                    text = getCountryFlag(profile.name),
                    style = typography.bodyLarge,
                    modifier = Modifier.padding(end = spacing.xs)
                )
                Column {
                    Text(
                        text = profile.name,
                        style = typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile.protocol.name} · ${profile.server}:${profile.port}",
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
            }

            val ping = profile.lastPingMs
            val pingColor = when {
                ping == null -> colors.textMuted
                ping < 0 -> colors.statusError
                ping < 120 -> colors.statusOk
                ping < 250 -> colors.statusWarning
                else -> colors.statusError
            }
            Text(
                text = if (ping != null && ping >= 0) "$ping ms" else "--",
                style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = pingColor
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
    val df = DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}

private fun getCountryFlag(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.contains("germany") || lower.contains("de") || lower.contains("герман") -> "🇩🇪"
        lower.contains("netherlands") || lower.contains("nl") || lower.contains("нидерланд") -> "🇳🇱"
        lower.contains("united states") || lower.contains("usa") || lower.contains("us") || lower.contains("сша") -> "🇺🇸"
        lower.contains("finland") || lower.contains("fi") || lower.contains("финлянд") -> "🇫🇮"
        lower.contains("sweden") || lower.contains("se") || lower.contains("швеци") -> "🇸🇪"
        lower.contains("united kingdom") || lower.contains("uk") || lower.contains("gb") || lower.contains("британи") -> "🇬🇧"
        lower.contains("singapore") || lower.contains("sg") || lower.contains("сингапур") -> "🇸🇬"
        lower.contains("japan") || lower.contains("jp") || lower.contains("япони") -> "🇯🇵"
        lower.contains("turkey") || lower.contains("tr") || lower.contains("турци") -> "🇹🇷"
        lower.contains("france") || lower.contains("fr") || lower.contains("франци") -> "🇫🇷"
        lower.contains("russia") || lower.contains("ru") || lower.contains("росси") -> "🇷🇺"
        else -> "🌐"
    }
}
