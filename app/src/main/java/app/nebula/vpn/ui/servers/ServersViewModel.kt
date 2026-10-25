package app.nebula.vpn.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.parser.LinkParser
import app.nebula.vpn.core.parser.SubscriptionDecoder
import app.nebula.vpn.core.proxy.BatchTestProgress
import app.nebula.vpn.core.proxy.LatencyTester
import app.nebula.vpn.core.proxy.PingTester
import app.nebula.vpn.core.proxy.PingType
import app.nebula.vpn.core.proxy.ProbeMethod
import app.nebula.vpn.core.proxy.ServerTestState
import app.nebula.vpn.data.database.entity.SubscriptionEntity
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.SettingsRepository
import app.nebula.vpn.data.repository.SubscriptionRepository
import app.nebula.vpn.data.repository.VpnRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val settingsRepository: SettingsRepository,
    private val vpnRepository: VpnRepository,
    private val pingTester: PingTester,
    private val latencyTester: LatencyTester
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedProtocolFilter = MutableStateFlow<String?>(null)
    val selectedProtocolFilter: StateFlow<String?> = _selectedProtocolFilter.asStateFlow()

    private val _filterFavoriteOnly = MutableStateFlow(false)
    val filterFavoriteOnly: StateFlow<Boolean> = _filterFavoriteOnly.asStateFlow()

    private val _sortByPing = MutableStateFlow(false)
    val sortByPing: StateFlow<Boolean> = _sortByPing.asStateFlow()

    val latencyProgress: StateFlow<BatchTestProgress> = latencyTester.progress

    val isPinging: StateFlow<Boolean> = latencyProgress.map { it.isRunning }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    private val _isPingingAll = MutableStateFlow(false)
    val isPingingAll: StateFlow<Boolean> = _isPingingAll.asStateFlow()

    private val _pingingSubscriptionId = MutableStateFlow<String?>(null)
    val pingingSubscriptionId: StateFlow<String?> = _pingingSubscriptionId.asStateFlow()

    private val _pingingProfileId = MutableStateFlow<String?>(null)
    val pingingProfileId: StateFlow<String?> = _pingingProfileId.asStateFlow()

    private val _livePingResults = MutableStateFlow<Map<String, Long>>(emptyMap())
    val livePingResults: StateFlow<Map<String, Long>> = _livePingResults.asStateFlow()

    val pingProgress: StateFlow<Float> = latencyProgress.map { prog ->
        if (prog.total > 0) prog.completed.toFloat() / prog.total.toFloat() else 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    private val _probeMethod = MutableStateFlow(ProbeMethod.TCP_HANDSHAKE)
    val probeMethod: StateFlow<ProbeMethod> = _probeMethod.asStateFlow()

    fun setProbeMethod(method: ProbeMethod) {
        _probeMethod.value = method
    }

    private val _activePingType = MutableStateFlow(PingType.TCP)
    val activePingType: StateFlow<PingType> = _activePingType.asStateFlow()

    val pingMode: StateFlow<String> = settingsRepository.pingMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "TCP"
    )

    init {
        viewModelScope.launch {
            var wasRunning = false
            latencyTester.progress.collect { progress ->
                val newPings = mutableMapOf<String, Long>()
                progress.currentServerStates.forEach { (id, state) ->
                    when (state) {
                        is ServerTestState.Ok -> newPings[id] = state.latencyMs
                        is ServerTestState.Timeout -> newPings[id] = -1L
                        is ServerTestState.Error -> newPings[id] = -1L
                        else -> {}
                    }
                }
                if (newPings.isNotEmpty()) {
                    _livePingResults.value = _livePingResults.value + newPings
                }
                if (wasRunning && !progress.isRunning) {
                    if (newPings.isNotEmpty()) {
                        profileRepository.updatePingsBatch(newPings)
                    }
                    _isPingingAll.value = false
                    _pingingSubscriptionId.value = null
                    _pingingProfileId.value = null
                }
                wasRunning = progress.isRunning
            }
        }
    }

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    val selectedProfileId: StateFlow<String?> = settingsRepository.selectedProfileId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val subscriptions: StateFlow<List<SubscriptionEntity>> = subscriptionRepository.allSubscriptions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val allProfiles: StateFlow<List<ProxyProfile>> = profileRepository.allProfiles.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private data class FilterParams(
        val query: String,
        val protocol: String?,
        val favOnly: Boolean,
        val sortPing: Boolean
    )

    private val profilesWithLivePing: Flow<List<ProxyProfile>> = combine(
        profileRepository.allProfiles,
        _livePingResults
    ) { all, livePings ->
        all.map { p ->
            val live = livePings[p.id]
            if (live != null) p.copy(lastPingMs = live) else p
        }
    }

    private val filterParams: Flow<FilterParams> = combine(
        _searchQuery,
        _selectedProtocolFilter,
        _filterFavoriteOnly,
        _sortByPing
    ) { query, protocol, favOnly, sortPing ->
        FilterParams(query, protocol, favOnly, sortPing)
    }

    val filteredProfiles: StateFlow<List<ProxyProfile>> = combine(
        profilesWithLivePing,
        filterParams
    ) { profiles, filter ->
        val filtered = profiles.filter { profile ->
            val matchesQuery = filter.query.isEmpty() ||
                    profile.name.contains(filter.query, ignoreCase = true) ||
                    profile.server.contains(filter.query, ignoreCase = true)
            val matchesProto = filter.protocol == null || profile.protocol.name.equals(filter.protocol, ignoreCase = true)
            val matchesFav = !filter.favOnly || profile.isFavorite
            matchesQuery && matchesProto && matchesFav
        }
        if (filter.sortPing) {
            filtered.sortedWith(
                compareBy(
                    { it.lastPingMs == null || it.lastPingMs <= 0L },
                    { it.lastPingMs ?: Long.MAX_VALUE }
                )
            )
        } else {
            filtered
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pingAllProfiles() {
        if (latencyProgress.value.isRunning) return
        val profiles = allProfiles.value
        if (profiles.isEmpty()) return
        _isPingingAll.value = true
        viewModelScope.launch {
            try {
                latencyTester.testBatch(profiles, _probeMethod.value)
                _importStatusMessage.value = "Проверка задержки завершена (${profiles.size} серверов)"
            } finally {
                _isPingingAll.value = false
            }
        }
    }

    fun pingSubscriptionProfiles(subscriptionId: String) {
        if (latencyProgress.value.isRunning) return
        val subsProfiles = allProfiles.value.filter { it.subscriptionId == subscriptionId }
        if (subsProfiles.isEmpty()) return
        _pingingSubscriptionId.value = subscriptionId
        viewModelScope.launch {
            try {
                latencyTester.testBatch(subsProfiles, _probeMethod.value)
                _importStatusMessage.value = "Проверка серверов подписки завершена (${subsProfiles.size} серверов)"
            } finally {
                _pingingSubscriptionId.value = null
            }
        }
    }

    fun pingSingleProfile(profileId: String) {
        if (latencyProgress.value.isRunning) return
        val profile = allProfiles.value.firstOrNull { it.id == profileId } ?: return
        _pingingProfileId.value = profileId
        viewModelScope.launch {
            try {
                latencyTester.testBatch(listOf(profile), _probeMethod.value)
            } finally {
                _pingingProfileId.value = null
            }
        }
    }

    fun cancelPing() {
        latencyTester.cancel()
        _isPingingAll.value = false
        _pingingSubscriptionId.value = null
        _pingingProfileId.value = null
    }

    fun toggleSortByPing() {
        _sortByPing.value = !_sortByPing.value
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setProtocolFilter(protocol: String?) {
        _selectedProtocolFilter.value = protocol
    }

    fun toggleFavoriteFilter() {
        _filterFavoriteOnly.value = !_filterFavoriteOnly.value
    }

    fun selectProfile(id: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedProfileId(id)
            if (vpnRepository.connectionState.value is app.nebula.vpn.core.model.ConnectionState.Connected) {
                vpnRepository.switchProfile(id)
            }
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            profileRepository.toggleFavorite(id)
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            profileRepository.deleteProfile(id)
        }
    }

    fun deleteSubscription(id: String) {
        viewModelScope.launch {
            subscriptionRepository.deleteSubscription(id)
        }
    }

    fun refreshSubscription(id: String) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = subscriptionRepository.refreshSubscription(id)
            _isImporting.value = false
            when (result) {
                is Result.Success -> _importStatusMessage.value = "Подписка успешно обновлена"
                is Result.Error -> _importStatusMessage.value = "Ошибка: ${result.exception.message}"
            }
        }
    }

    fun refreshAllSubscriptions() {
        viewModelScope.launch {
            _isImporting.value = true
            val subs = subscriptions.value
            for (sub in subs) {
                subscriptionRepository.refreshSubscription(sub.id)
            }
            _isImporting.value = false
            _importStatusMessage.value = "Все подписки обновлены"
        }
    }

    fun clearStatusMessage() {
        _importStatusMessage.value = null
    }

    fun importContent(raw: String, onComplete: (Boolean, String) -> Unit) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            onComplete(false, "Пустая ссылка или данные")
            return
        }

        viewModelScope.launch {
            _isImporting.value = true

            // 1. Check if it's an HTTP/HTTPS Subscription URL
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                when (val result = subscriptionRepository.addSubscription(trimmed)) {
                    is Result.Success -> {
                        _isImporting.value = false
                        onComplete(true, "Подписка успешно загружена: ${result.data.title}")
                    }
                    is Result.Error -> {
                        _isImporting.value = false
                        onComplete(false, "Не удалось загрузить подписку: ${result.exception.message}")
                    }
                }
                return@launch
            }

            // 2. Check if it's a single proxy link (vless://, vmess://, etc.)
            val singleParsed = LinkParser.parse(trimmed)
            if (singleParsed is Result.Success) {
                profileRepository.saveProfile(singleParsed.data)
                settingsRepository.setSelectedProfileId(singleParsed.data.id)
                _isImporting.value = false
                onComplete(true, "Сервер добавлен: ${singleParsed.data.name}")
                return@launch
            }

            // 3. Try decoding as subscription content (base64 or YAML)
            when (val subResult = SubscriptionDecoder.decode(trimmed)) {
                is Result.Success -> {
                    profileRepository.saveProfiles(subResult.data.profiles)
                    if (subResult.data.profiles.isNotEmpty()) {
                        settingsRepository.setSelectedProfileId(subResult.data.profiles.first().id)
                    }
                    _isImporting.value = false
                    onComplete(true, "Импортировано серверов: ${subResult.data.profiles.size}")
                }
                is Result.Error -> {
                    _isImporting.value = false
                    onComplete(false, "Не удалось распознать формат ссылки или данных")
                }
            }
        }
    }
}
