package app.nebula.vpn.ui.servers

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
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
import app.nebula.vpn.core.proxy.BatchTestProgress
import app.nebula.vpn.core.proxy.ProbeMethod
import app.nebula.vpn.core.proxy.ServerTestState
import app.nebula.vpn.data.database.entity.SubscriptionEntity
import app.nebula.vpn.ui.theme.EmeraldGreen
import app.nebula.vpn.ui.theme.StatusGreen
import app.nebula.vpn.ui.theme.StatusRed
import app.nebula.vpn.ui.theme.StatusYellow
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
    val pingProgress by viewModel.pingProgress.collectAsState()
    val latencyProgress by viewModel.latencyProgress.collectAsState()
    val probeMethod by viewModel.probeMethod.collectAsState()
    val sortByPing by viewModel.sortByPing.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val statusMessage by viewModel.importStatusMessage.collectAsState()

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
                    text = "Добавить подключение",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Вставьте ссылку на подписку (https://...) или ссылку на сервер (vless://, vmess://, trojan://, ss://, hy2://):",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputUrlText,
                        onValueChange = { inputUrlText = it },
                        placeholder = { Text("https://... или vless://...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
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
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                showAddDialog = false
                                onOpenQrScanner()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Сканировать QR-код",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (isImporting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Загрузка и парсинг...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
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
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Импортировать")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    enabled = !isImporting
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = EmeraldGreen,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Server or Subscription", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Header with Counter and Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Серверы",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${allProfiles.size} серверов · ${subscriptions.size} провайдеров",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Ping all profiles in entire app
                    IconButton(
                        onClick = { viewModel.pingAllProfiles() },
                        enabled = !isPingingAll && !isImporting
                    ) {
                        if (isPingingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Проверить пинг всех",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Sort by latency
                    IconButton(onClick = { viewModel.toggleSortByPing() }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Сортировка по пингу",
                            tint = if (sortByPing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.refreshAllSubscriptions() },
                        enabled = !isImporting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh All",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleFavoriteFilter() }
                    ) {
                        Icon(
                            imageVector = if (favoriteOnly) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Filter Favorites",
                            tint = if (favoriteOnly) StatusYellow else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (latencyProgress.isRunning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                color = EmeraldGreen
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Проверка задержки: ${latencyProgress.completed} / ${latencyProgress.total}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { if (latencyProgress.total > 0) latencyProgress.completed.toFloat() / latencyProgress.total.toFloat() else 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = EmeraldGreen,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = { viewModel.cancelPing() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Отмена проверки",
                                tint = StatusRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Поиск серверов...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = EmeraldGreen.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Protocol and Probe Mode Selectors Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Probe Method Selector Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Тест:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    listOf(
                        ProbeMethod.TCP_HANDSHAKE to "TCP",
                        ProbeMethod.TLS_HANDSHAKE to "TLS",
                        ProbeMethod.HTTP_GET to "HTTP"
                    ).forEach { (method, label) ->
                        val isSelected = probeMethod == method
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setProbeMethod(method) },
                            label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldGreen.copy(alpha = 0.2f),
                                selectedLabelColor = EmeraldGreen,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Protocol Filter Chips
            val protocols = listOf("Все", "VLESS", "VMess", "Trojan", "Shadowsocks", "Hysteria2")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(protocols) { proto ->
                    val isSelected = (proto == "Все" && selectedProtocol == null) ||
                            (selectedProtocol != null && proto.equals(selectedProtocol, ignoreCase = true))

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.setProtocolFilter(if (proto == "Все") null else proto)
                        },
                        label = { Text(proto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = Color.Black,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Server & Subscription List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                            text = "ПОЛЬЗОВАТЕЛЬСКИЕ СЕРВЕРЫ (${customProfiles.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(customProfiles, key = { it.id }) { profile ->
                        val testState = latencyProgress.currentServerStates[profile.id]
                        ServerItemCard(
                            profile = profile,
                            isSelected = profile.id == selectedId,
                            isPinging = (pingingProfileId == profile.id || testState is ServerTestState.Testing),
                            serverTestState = testState,
                            onSelect = { viewModel.selectProfile(profile.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(profile.id) },
                            onDelete = { viewModel.deleteProfile(profile.id) },
                            onPing = { viewModel.pingSingleProfile(profile.id) }
                        )
                    }
                }

                // Bottom padding for FAB
                item {
                    Spacer(modifier = Modifier.height(72.dp))
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
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить подписку?") },
            text = { Text("Все ${profiles.size} серверов из подписки «${subscription.title}» будут удалены.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(EmeraldGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = subscription.title.ifBlank { "Подписка" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${profiles.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(EmeraldGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPingSubscription,
                        enabled = !isPinging,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isPinging) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Проверить пинг подписки",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Quota Bar (only shown when real quota/expiry or usage exists)
            val total = subscription.totalBytes
            val used = (subscription.uploadBytes ?: 0L) + (subscription.downloadBytes ?: 0L)
            val hasQuota = total != null && total > 0L
            val hasExpiry = subscription.expireEpochSeconds != null && subscription.expireEpochSeconds > 0L

            if (hasQuota || hasExpiry || used > 0L) {
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
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
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    } else if (used > 0L) {
                        Text(
                            text = "Использовано: ${formatBytes(used)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = "Безлимит",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
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
                        color = EmeraldGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            // Inner Profiles List
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profiles.forEach { profile ->
                        val isSelected = profile.id == selectedId
                        val testState = serverStates[profile.id]
                        ServerItemCard(
                            profile = profile,
                            isSelected = isSelected,
                            isPinging = (pingingProfileId == profile.id || testState is ServerTestState.Testing),
                            serverTestState = testState,
                            onSelect = { onSelectProfile(profile.id) },
                            onToggleFavorite = { onToggleFavorite(profile.id) },
                            onDelete = { onDeleteProfile(profile.id) },
                            onPing = { onPingProfile(profile.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerItemCard(
    profile: ProxyProfile,
    isSelected: Boolean,
    isPinging: Boolean = false,
    serverTestState: ServerTestState? = null,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onPing: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                EmeraldGreen.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Radio / Active icon
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Country flag
                Text(
                    text = getCountryFlag(profile.name),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.protocol.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${profile.server}:${profile.port}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Clickable Ping Badge for Single Server
                val (displayText, pingColor) = when {
                    serverTestState is ServerTestState.Ok -> {
                        val ms = serverTestState.latencyMs
                        val col = when {
                            ms < 120 -> StatusGreen
                            ms < 300 -> StatusYellow
                            else -> StatusRed
                        }
                        "$ms ms" to col
                    }
                    serverTestState is ServerTestState.Timeout -> "Таймаут" to StatusRed
                    serverTestState is ServerTestState.Error -> "Ошибка" to StatusRed
                    else -> {
                        val ping = profile.lastPingMs
                        val col = when {
                            ping == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            ping < 0 -> StatusRed
                            ping < 120 -> StatusGreen
                            ping < 250 -> StatusYellow
                            else -> StatusRed
                        }
                        val text = when {
                            ping == null -> "--"
                            ping < 0 -> "Таймаут"
                            else -> "$ping ms"
                        }
                        text to col
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(pingColor.copy(alpha = 0.12f))
                        .clickable(enabled = !isPinging && serverTestState !is ServerTestState.Testing) { onPing() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    if (isPinging || serverTestState is ServerTestState.Testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(pingColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = displayText,
                            fontSize = 11.sp,
                            color = pingColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (profile.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (profile.isFavorite) StatusYellow else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
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

private fun getCountryFlag(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.contains("герман") || lower.contains("germany") || lower.contains(" de") || lower.contains("_de") -> "🇩🇪"
        lower.contains("нидерланд") || lower.contains("netherlands") || lower.contains(" nl") || lower.contains("_nl") || lower.contains("амстердам") -> "🇳🇱"
        lower.contains("сша") || lower.contains("usa") || lower.contains("united states") || lower.contains(" us") || lower.contains("_us") -> "🇺🇸"
        lower.contains("росси") || lower.contains("russia") || lower.contains(" ru") || lower.contains("_ru") || lower.contains("москва") -> "🇷🇺"
        lower.contains("финлянд") || lower.contains("finland") || lower.contains(" fi") || lower.contains("_fi") -> "🇫🇮"
        lower.contains("швеци") || lower.contains("sweden") || lower.contains(" se") || lower.contains("_se") -> "🇸🇪"
        lower.contains("латви") || lower.contains("latvia") || lower.contains(" lv") || lower.contains("_lv") -> "🇱🇻"
        lower.contains("турци") || lower.contains("turkey") || lower.contains(" tr") || lower.contains("_tr") -> "🇹🇷"
        lower.contains("казах") || lower.contains("kazakhstan") || lower.contains(" kz") || lower.contains("_kz") -> "🇰🇿"
        lower.contains("япон") || lower.contains("japan") || lower.contains(" jp") || lower.contains("_jp") -> "🇯🇵"
        lower.contains("сингапур") || lower.contains("singapore") || lower.contains(" sg") || lower.contains("_sg") -> "🇸🇬"
        lower.contains("франци") || lower.contains("france") || lower.contains(" fr") || lower.contains("_fr") -> "🇫🇷"
        lower.contains("британ") || lower.contains("uk") || lower.contains("gb") || lower.contains("лондон") -> "🇬🇧"
        else -> "🌐"
    }
}
