package app.nebula.vpn.ui.servers

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nebula.vpn.R
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.proxy.ProbeMethod
import app.nebula.vpn.core.proxy.ServerTestState
import app.nebula.vpn.data.database.entity.SubscriptionEntity
import app.nebula.vpn.ui.components.NebulaEmptyState
import app.nebula.vpn.ui.components.NebulaFilterChip
import app.nebula.vpn.ui.components.NebulaServerCard
import app.nebula.vpn.ui.theme.NebulaTheme
import java.text.DecimalFormat

@Composable
fun ServersScreen(
    viewModel: ServersViewModel,
    onOpenQrScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val allProfiles by viewModel.allProfiles.collectAsState()
    val filteredProfiles by viewModel.filteredProfiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedProtocol by viewModel.selectedProtocolFilter.collectAsState()
    val selectedId by viewModel.selectedProfileId.collectAsState()
    val favoriteOnly by viewModel.filterFavoriteOnly.collectAsState()
    val isPinging by viewModel.isPinging.collectAsState()
    val isPingingAll by viewModel.isPingingAll.collectAsState()
    val pingingSubscriptionId by viewModel.pingingSubscriptionId.collectAsState()
    val pingingProfileId by viewModel.pingingProfileId.collectAsState()
    val latencyProgress by viewModel.latencyProgress.collectAsState()
    val probeMethod by viewModel.probeMethod.collectAsState()
    val sortByPing by viewModel.sortByPing.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val statusMessage by viewModel.importStatusMessage.collectAsState()

    val colors = NebulaTheme.colors
    val spacing = NebulaTheme.spacing
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var inputUrlText by remember { mutableStateOf("") }

    // Map to track expanded state of subscription cards
    val expandedSubs = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    // Add Subscription / Server Modal Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) showAddDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.servers_add_dialog_title),
                    style = typography.titleLarge,
                    color = colors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.servers_add_dialog_hint),
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(spacing.md))

                    OutlinedTextField(
                        value = inputUrlText,
                        onValueChange = { inputUrlText = it },
                        placeholder = { Text("https://… or vless://…", color = colors.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surfaceElevated,
                            unfocusedContainerColor = colors.surface,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.borderSubtle,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        inputUrlText = clip.getItemAt(0).text?.toString() ?: ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = colors.primary)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(spacing.md))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shapes.button)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.borderSubtle, shapes.button)
                            .clickable {
                                showAddDialog = false
                                onOpenQrScanner()
                            }
                            .padding(spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Text(
                            text = stringResource(R.string.scan_qr),
                            style = typography.titleSmall,
                            color = colors.textPrimary
                        )
                    }

                    if (isImporting) {
                        Spacer(modifier = Modifier.height(spacing.md))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                                color = colors.primary
                            )
                            Spacer(modifier = Modifier.width(spacing.sm))
                            Text(
                                text = stringResource(R.string.ping_testing),
                                style = typography.bodySmall,
                                color = colors.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputUrlText.isNotBlank()) {
                            viewModel.importContent(inputUrlText) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) {
                                    inputUrlText = ""
                                    showAddDialog = false
                                }
                            }
                        }
                    },
                    enabled = !isImporting && inputUrlText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    shape = shapes.button
                ) {
                    Text(stringResource(R.string.servers_add_dialog_import))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    enabled = !isImporting
                ) {
                    Text(stringResource(R.string.undo), color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated,
            shape = shapes.card
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.servers_add_dialog_title),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.md)
        ) {
            // Header with Counter and Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.md, bottom = spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.servers_title),
                        style = typography.displayMedium.copy(fontWeight = FontWeight.Black),
                        color = colors.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.servers_subtitle, allProfiles.size, subscriptions.size),
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Ping all profiles in entire app
                    IconButton(
                        onClick = { viewModel.pingAllProfiles() },
                        enabled = !isPingingAll && !isImporting,
                        modifier = Modifier.size(44.dp)
                    ) {
                        if (isPingingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = stringResource(R.string.ping_testing),
                                tint = colors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Sort by latency
                    IconButton(
                        onClick = { viewModel.toggleSortByPing() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = stringResource(R.string.auto_select_fastest),
                            tint = if (sortByPing) colors.primary else colors.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Refresh all subscriptions
                    IconButton(
                        onClick = { viewModel.refreshAllSubscriptions() },
                        enabled = !isImporting,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.subscription_update),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Favorites filter
                    IconButton(
                        onClick = { viewModel.toggleFavoriteFilter() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (favoriteOnly) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Filter Favorites",
                            tint = if (favoriteOnly) colors.statusWarning else colors.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Latency Progress banner
            if (latencyProgress.isRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.xs)
                        .clip(shapes.card)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, shapes.card)
                        .padding(horizontal = spacing.md, vertical = spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary
                            )
                            Spacer(modifier = Modifier.width(spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        R.string.servers_latency_progress,
                                        latencyProgress.completed,
                                        latencyProgress.total
                                    ),
                                    style = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (latencyProgress.total > 0) {
                                            latencyProgress.completed.toFloat() / latencyProgress.total.toFloat()
                                        } else 0f
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = colors.primary,
                                    trackColor = colors.surface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(spacing.sm))
                        IconButton(
                            onClick = { viewModel.cancelPing() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel ping",
                                tint = colors.statusError,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.xs))
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = {
                    Text(
                        stringResource(R.string.servers_search_placeholder),
                        color = colors.textMuted,
                        style = typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = colors.textMuted
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = colors.textMuted)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceElevated,
                    unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                shape = shapes.card,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            // Probe Mode Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.servers_probe_label),
                        style = typography.labelSmall,
                        color = colors.textMuted
                    )
                    listOf(
                        ProbeMethod.TCP_HANDSHAKE to "TCP",
                        ProbeMethod.TLS_HANDSHAKE to "TLS",
                        ProbeMethod.HTTP_GET to "HTTP"
                    ).forEach { (method, label) ->
                        val isSelected = probeMethod == method
                        NebulaFilterChip(
                            text = label,
                            isSelected = isSelected,
                            onClick = { viewModel.setProbeMethod(method) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.xs))

            // Protocol Filter Chips
            val protocols = listOf("ALL", "VLESS", "VMess", "Trojan", "Shadowsocks", "Hysteria2")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(protocols) { proto ->
                    val isSelected = (proto == "ALL" && selectedProtocol == null) ||
                            (selectedProtocol != null && proto.equals(selectedProtocol, ignoreCase = true))

                    NebulaFilterChip(
                        text = if (proto == "ALL") stringResource(R.string.filter_all) else proto,
                        isSelected = isSelected,
                        onClick = {
                            viewModel.setProtocolFilter(if (proto == "ALL") null else proto)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // Main Server & Subscription List or Empty State
            if (filteredProfiles.isEmpty() && subscriptions.isEmpty()) {
                NebulaEmptyState(
                    title = stringResource(R.string.servers_empty_title),
                    description = stringResource(R.string.servers_empty_desc),
                    buttonText = stringResource(R.string.servers_empty_action),
                    onButtonClick = { showAddDialog = true },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Subscription Groups
                    items(subscriptions, key = { it.id }) { sub ->
                        val isExpanded = expandedSubs[sub.id] ?: true
                        val subProfiles = filteredProfiles.filter { it.subscriptionId == sub.id }

                        SubscriptionAccordion(
                            subscription = sub,
                            profiles = subProfiles,
                            selectedId = selectedId,
                            isExpanded = isExpanded,
                            isPinging = (pingingSubscriptionId == sub.id),
                            pingingProfileId = pingingProfileId,
                            serverStates = latencyProgress.currentServerStates,
                            onToggleExpand = { expandedSubs[sub.id] = !isExpanded },
                            onPingSubscription = { viewModel.pingSubscriptionProfiles(sub.id) },
                            onPingProfile = { viewModel.pingSingleProfile(it) },
                            onRefresh = { viewModel.refreshSubscription(sub.id) },
                            onDelete = { viewModel.deleteSubscription(sub.id) },
                            onSelectProfile = { viewModel.selectProfile(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDeleteProfile = { viewModel.deleteProfile(it) }
                        )
                    }

                    // 2. Standalone / Custom Servers (no subscriptionId)
                    val customProfiles = filteredProfiles.filter { it.subscriptionId == null }
                    if (customProfiles.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.servers_custom_header, customProfiles.size),
                                style = typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = spacing.sm, bottom = 4.dp)
                            )
                        }

                        items(customProfiles, key = { it.id }) { profile ->
                            val testState = latencyProgress.currentServerStates[profile.id]
                            val isTesting = (pingingProfileId == profile.id || testState is ServerTestState.Testing)
                            val effectivePing = when (testState) {
                                is ServerTestState.Ok -> testState.latencyMs.toLong()
                                is ServerTestState.Timeout, is ServerTestState.Error -> 9999L
                                else -> profile.lastPingMs
                            }

                            var showItemMenu by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                NebulaServerCard(
                                    name = profile.name,
                                    protocol = profile.protocol.name,
                                    pingMs = effectivePing,
                                    isTesting = isTesting,
                                    isSelected = profile.id == selectedId,
                                    isFavorite = profile.isFavorite,
                                    onSelect = { viewModel.selectProfile(profile.id) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(profile.id) },
                                    onMenuClick = { showItemMenu = true },
                                    onPingClick = { viewModel.pingSingleProfile(profile.id) },
                                    isInsecure = profile.tls.insecure,
                                    hasTls = profile.tls.enabled
                                )

                                DropdownMenu(
                                    expanded = showItemMenu,
                                    onDismissRequest = { showItemMenu = false },
                                    modifier = Modifier.background(colors.surfaceElevated)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ping_testing), color = colors.textPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, tint = colors.primary) },
                                        onClick = {
                                            showItemMenu = false
                                            viewModel.pingSingleProfile(profile.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_selected), color = colors.statusError) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.statusError) },
                                        onClick = {
                                            showItemMenu = false
                                            viewModel.deleteProfile(profile.id)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Bottom padding for FAB
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionAccordion(
    subscription: SubscriptionEntity,
    profiles: List<ProxyProfile>,
    selectedId: String?,
    isExpanded: Boolean,
    isPinging: Boolean = false,
    pingingProfileId: String? = null,
    serverStates: Map<String, ServerTestState> = emptyMap(),
    onToggleExpand: () -> Unit,
    onPingSubscription: () -> Unit,
    onPingProfile: (String) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteProfile: (String) -> Unit
) {
    val colors = NebulaTheme.colors
    val spacing = NebulaTheme.spacing
    val shapes = NebulaTheme.shapes
    val typography = NebulaTheme.typography

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.servers_delete_sub_title),
                    style = typography.titleMedium,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.servers_delete_sub_message, profiles.size, subscription.title),
                    style = typography.bodyMedium,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.statusError),
                    shape = shapes.button
                ) {
                    Text(stringResource(R.string.delete_selected))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.undo), color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated,
            shape = shapes.card
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.card)
            .background(colors.surfaceElevated)
            .border(1.dp, colors.borderSubtle, shapes.card)
            .padding(spacing.md)
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpand() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = subscription.title.ifBlank { stringResource(R.string.home_section_subscription) },
                                style = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(spacing.xs))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${profiles.size}",
                                    style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primary
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPingSubscription,
                        enabled = !isPinging,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isPinging) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = stringResource(R.string.ping_testing),
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.subscription_update),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Quota Bar (only shown when real quota/expiry or usage exists)
            val total = if (subscription.totalBytes == 107374182400L) null else subscription.totalBytes
            val used = (subscription.uploadBytes ?: 0L) + (subscription.downloadBytes ?: 0L)
            val hasQuota = total != null && total > 0L
            val hasExpiry = subscription.expireEpochSeconds != null && subscription.expireEpochSeconds > 0L

            if (hasQuota || hasExpiry || used > 0L) {
                Spacer(modifier = Modifier.height(spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (hasExpiry) {
                        val nowSec = System.currentTimeMillis() / 1000
                        val diffSec = subscription.expireEpochSeconds!! - nowSec
                        val daysRemaining = (diffSec / 86400).coerceAtLeast(0)
                        Text(
                            text = stringResource(R.string.servers_days_remaining, daysRemaining.toInt()),
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.servers_permanent),
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }

                    if (hasQuota) {
                        Text(
                            text = "${formatBytes(used)} / ${formatBytes(total!!)}",
                            style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary
                        )
                    } else if (used > 0L) {
                        Text(
                            text = "Used: ${formatBytes(used)}",
                            style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.servers_unlimited),
                            style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.textSecondary
                        )
                    }
                }

                if (hasQuota) {
                    Spacer(modifier = Modifier.height(spacing.xs))
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

            // Inner Profiles List
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    profiles.forEach { profile ->
                        val isSelected = profile.id == selectedId
                        val testState = serverStates[profile.id]
                        val isTesting = (pingingProfileId == profile.id || testState is ServerTestState.Testing)
                        val effectivePing = when (testState) {
                            is ServerTestState.Ok -> testState.latencyMs.toLong()
                            is ServerTestState.Timeout, is ServerTestState.Error -> 9999L
                            else -> profile.lastPingMs
                        }

                        var showSubItemMenu by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            NebulaServerCard(
                                name = profile.name,
                                protocol = profile.protocol.name,
                                pingMs = effectivePing,
                                isTesting = isTesting,
                                isSelected = isSelected,
                                isFavorite = profile.isFavorite,
                                onSelect = { onSelectProfile(profile.id) },
                                onFavoriteToggle = { onToggleFavorite(profile.id) },
                                onMenuClick = { showSubItemMenu = true },
                                onPingClick = { onPingProfile(profile.id) },
                                isInsecure = profile.tls.insecure,
                                hasTls = profile.tls.enabled
                            )

                            DropdownMenu(
                                expanded = showSubItemMenu,
                                onDismissRequest = { showSubItemMenu = false },
                                modifier = Modifier.background(colors.surfaceElevated)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ping_testing), color = colors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, tint = colors.primary) },
                                    onClick = {
                                        showSubItemMenu = false
                                        onPingProfile(profile.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete_selected), color = colors.statusError) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.statusError) },
                                    onClick = {
                                        showSubItemMenu = false
                                        onDeleteProfile(profile.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
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
