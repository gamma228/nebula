package app.nebula.vpn.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.parser.LinkParser
import app.nebula.vpn.core.parser.SubscriptionDecoder
import app.nebula.vpn.data.database.entity.SubscriptionEntity
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.SettingsRepository
import app.nebula.vpn.data.repository.SubscriptionRepository
import app.nebula.vpn.data.repository.VpnRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val settingsRepository: SettingsRepository,
    private val vpnRepository: VpnRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedProtocolFilter = MutableStateFlow<String?>(null)
    val selectedProtocolFilter: StateFlow<String?> = _selectedProtocolFilter.asStateFlow()

    private val _filterFavoriteOnly = MutableStateFlow(false)
    val filterFavoriteOnly: StateFlow<Boolean> = _filterFavoriteOnly.asStateFlow()

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

    val filteredProfiles: StateFlow<List<ProxyProfile>> = combine(
        profileRepository.allProfiles,
        _searchQuery,
        _selectedProtocolFilter,
        _filterFavoriteOnly
    ) { all, query, protocol, favOnly ->
        all.filter { profile ->
            val matchesQuery = query.isEmpty() ||
                    profile.name.contains(query, ignoreCase = true) ||
                    profile.server.contains(query, ignoreCase = true)
            val matchesProto = protocol == null || profile.protocol.name.equals(protocol, ignoreCase = true)
            val matchesFav = !favOnly || profile.isFavorite
            matchesQuery && matchesProto && matchesFav
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                vpnRepository.startVpn(id)
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
