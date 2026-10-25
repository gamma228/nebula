package app.nebula.vpn.ui.home

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.data.database.entity.SubscriptionEntity
import app.nebula.vpn.ui.theme.EmeraldGreen
import app.nebula.vpn.ui.theme.LocalThemeConfig
import app.nebula.vpn.ui.theme.MintTeal
import app.nebula.vpn.ui.theme.StatusGreen
import app.nebula.vpn.ui.theme.StatusRed
import app.nebula.vpn.ui.theme.StatusYellow
import app.nebula.vpn.ui.theme.getCardShape
import app.nebula.vpn.ui.theme.getChipShape
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
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val activeSub by viewModel.activeSubscription.collectAsState()
    val isRefreshingSub by viewModel.isRefreshingSubscription.collectAsState()
    val durationSeconds by viewModel.connectionDurationSeconds.collectAsState()
    val allProfiles by viewModel.allProfiles.collectAsState()
    val themeConfig = LocalThemeConfig.current
    val context = LocalContext.current

    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Futuristic Cosmic Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NEBULA",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CORE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(themeConfig.getChipShape())
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Connection State Pill Badge
            val (statusText, statusBg, statusFg) = when {
                isConnected -> Triple(stringResource(R.string.state_connected), StatusGreen.copy(alpha = 0.15f), StatusGreen)
                isConnecting -> Triple(stringResource(R.string.state_connecting), StatusYellow.copy(alpha = 0.15f), StatusYellow)
                else -> Triple(stringResource(R.string.state_disconnected), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(themeConfig.getChipShape())
                    .background(statusBg)
                    .border(1.dp, statusFg.copy(alpha = 0.3f), themeConfig.getChipShape())
                    .padding(horizontal = 12.dp, vertical = 6.dp)
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusFg
                )
            }
        }

        // Aerospace Glass Telemetry HUD
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = themeConfig.getCardShape(),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, themeConfig.getCardShape())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Upload Telemetry
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Upload",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "UPLOAD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatSpeed(trafficStats.uploadSpeedBps),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Vertical Divider Line
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )

                // Session Uptime Clock
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Duration",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SESSION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatDuration(durationSeconds),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Vertical Divider Line
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )

                // Download Telemetry
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "DOWNLOAD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatSpeed(trafficStats.downloadSpeedBps),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Nebula Celestial Core Reactor Button
        NebulaCelestialCore(
            connectionState = connectionState,
            selectedProfile = selectedProfile,
            onClick = {
                if (isConnected || isConnecting) {
                    viewModel.toggleConnection()
                } else {
                    onRequestConnect()
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Active Node Glass Card (with 1-Tap Ping Test)
        ActiveServerBadge(
            profile = selectedProfile,
            onClick = onNavigateToServers,
            onPing = { viewModel.pingActiveServer() }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Subscription Hub ("ПАКЕТ И ПОДПИСКА")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ПОДПИСКА И ТРАФИК",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (activeSub != null) {
            ProviderSubscriptionCard(
                subscription = activeSub!!,
                isRefreshing = isRefreshingSub,
                onRefresh = { viewModel.refreshActiveSubscription() },
                onManage = onNavigateToServers
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = themeConfig.getCardShape(),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, themeConfig.getCardShape())
                    .clickable { onNavigateToServers() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(themeConfig.getChipShape())
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Подключить подписку",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Импортируйте ссылку от вашего провайдера",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Nodes Switcher
        if (allProfiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "БЫСТРЫЙ ВЫБОР УЗЛА",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = "Все (${allProfiles.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onNavigateToServers() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        Spacer(modifier = Modifier.height(28.dp))
    }
}

/**
 * Nebula Celestial Core Reactor:
 * A distinctive multi-layered holographic reactor with rotating orbital starlight ring,
 * concentric energy iris, tactile scale physics, and dynamic security telemetry.
 */
@Composable
private fun NebulaCelestialCore(
    connectionState: ConnectionState,
    selectedProfile: ProxyProfile?,
    onClick: () -> Unit
) {
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "celestial")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isConnecting) 1500 else 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = if (isConnected) 0.55f else if (isConnecting) 0.8f else 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = tween(150),
        label = "springScale"
    )

    val activePrimary = MaterialTheme.colorScheme.primary
    val activeSecondary = MaterialTheme.colorScheme.secondary

    val coreGlowColor = when {
        isConnected -> activePrimary
        isConnecting -> StatusYellow
        else -> activePrimary.copy(alpha = 0.6f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(230.dp)
                .scale(scaleAnim)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
        ) {
            // Layer 1: Cosmic Aura Glow
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                coreGlowColor.copy(alpha = auraPulse),
                                coreGlowColor.copy(alpha = auraPulse * 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Layer 2: Rotating Orbital Ring with Starlight Particle
            Canvas(
                modifier = Modifier
                    .size(200.dp)
                    .rotate(orbitAngle)
            ) {
                // Dashed orbital circumference
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            coreGlowColor,
                            activeSecondary,
                            Color.Transparent,
                            coreGlowColor
                        )
                    ),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Starlight Beacon Node on the orbit
                drawCircle(
                    color = if (isConnected) activePrimary else if (isConnecting) StatusYellow else Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(size.width, size.height / 2f)
                )
            }

            // Layer 3: Concentric Obsidian Dial Ring
            Box(
                modifier = Modifier
                    .size(165.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0F151D),
                                Color(0xFF040608)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                coreGlowColor.copy(alpha = if (isConnected) 0.8f else 0.3f),
                                activeSecondary.copy(alpha = if (isConnected) 0.5f else 0.1f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Inner Core Iris & Emblems
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = StatusYellow,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "ПОДКЛЮЧЕНИЕ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = StatusYellow
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = if (isConnected) coreGlowColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(50.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isConnected) stringResource(R.string.state_connected).uppercase() else stringResource(R.string.action_connect).uppercase(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = if (isConnected) coreGlowColor else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Protocol & Security Chip
        val proto = selectedProfile?.protocol?.name ?: "VLESS"
        Text(
            text = if (isConnected) "✦ $proto · TUNNEL ACTIVE" else "NEBULA CORE READY",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * Active Server Card with Country Flag, Protocol, and 1-Tap Ping Test Button
 */
@Composable
private fun ActiveServerBadge(
    profile: ProxyProfile?,
    onClick: () -> Unit,
    onPing: () -> Unit
) {
    val themeConfig = LocalThemeConfig.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = themeConfig.getCardShape(),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, themeConfig.getCardShape())
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Flag emoji
                val flag = getCountryFlag(profile?.name ?: "")
                Text(
                    text = flag,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Column {
                    Text(
                        text = profile?.name ?: "Авто выбор (наименьший пинг)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile?.protocol?.name ?: "AUTO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // Interactive Ping Badge
                        val ping = profile?.lastPingMs
                        val pingColor = when {
                            ping == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            ping < 0 -> StatusRed
                            ping < 120 -> StatusGreen
                            ping < 250 -> StatusYellow
                            else -> StatusRed
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(pingColor.copy(alpha = 0.12f))
                                .clickable { onPing() }
                                .padding(horizontal = 5.dp, vertical = 1.dp)
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
                                fontSize = 11.sp,
                                color = pingColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ArrowForwardIos,
                contentDescription = "Change Server",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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
    val themeConfig = LocalThemeConfig.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = themeConfig.getCardShape(),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, themeConfig.getCardShape())
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .clip(themeConfig.getChipShape())
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = subscription.title.ifBlank { "Подписка VPN" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
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
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(rotateAnim)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quota (only shown when real quota/expiry or usage exists)
            val total = subscription.totalBytes
            val used = (subscription.uploadBytes ?: 0L) + (subscription.downloadBytes ?: 0L)
            val hasQuota = total != null && total > 0L
            val hasExpiry = subscription.expireEpochSeconds != null && subscription.expireEpochSeconds > 0L

            if (hasQuota || hasExpiry || used > 0L) {
                Spacer(modifier = Modifier.height(12.dp))

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
                            text = "Осталось $daysRemaining дн.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Text(
                            text = "Бессрочно",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    if (hasQuota) {
                        Text(
                            text = "${formatBytes(used)} / ${formatBytes(total!!)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else if (used > 0L) {
                        Text(
                            text = "Использовано: ${formatBytes(used)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = "Безлимит",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
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
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
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
    val themeConfig = LocalThemeConfig.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = themeConfig.getCardShape(),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                shape = themeConfig.getCardShape()
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = getCountryFlag(profile.name),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column {
                    Text(
                        text = profile.name,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile.protocol.name} · ${profile.server}:${profile.port}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            val ping = profile.lastPingMs
            val pingColor = when {
                ping == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                ping < 0 -> StatusRed
                ping < 120 -> StatusGreen
                ping < 250 -> StatusYellow
                else -> StatusRed
            }
            Text(
                text = if (ping != null && ping >= 0) "$ping ms" else "--",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = pingColor
            )
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0.0 KB/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) {
        return "%.1f KB/s".format(kb)
    }
    val mb = kb / 1024.0
    return "%.1f MB/s".format(mb)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
    val df = DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
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
