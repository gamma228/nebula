package app.nebula.vpn.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.parser.LinkParser
import app.nebula.vpn.core.parser.SubscriptionDecoder
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.SettingsRepository
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
    private val settingsRepository: SettingsRepository,
    private val vpnRepository: VpnRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterFavoriteOnly = MutableStateFlow(false)
    val filterFavoriteOnly: StateFlow<Boolean> = _filterFavoriteOnly.asStateFlow()

    val selectedProfileId: StateFlow<String?> = settingsRepository.selectedProfileId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val profiles: StateFlow<List<ProxyProfile>> = combine(
        profileRepository.allProfiles,
        _searchQuery,
        _filterFavoriteOnly
    ) { all, query, favOnly ->
        all.filter { profile ->
            val matchesQuery = query.isEmpty() ||
                    profile.name.contains(query, ignoreCase = true) ||
                    profile.server.contains(query, ignoreCase = true)
            val matchesFav = !favOnly || profile.isFavorite
            matchesQuery && matchesFav
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoriteFilter() {
        _filterFavoriteOnly.value = !_filterFavoriteOnly.value
    }

    fun selectProfile(id: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedProfileId(id)
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

    fun importFromLink(link: String): Result<ProxyProfile> {
        return when (val result = LinkParser.parse(link)) {
            is Result.Success -> {
                viewModelScope.launch {
                    profileRepository.saveProfile(result.data)
                }
                result
            }
            is Result.Error -> result
        }
    }

    fun importSubscription(content: String): Result<Int> {
        return when (val result = SubscriptionDecoder.decode(content)) {
            is Result.Success -> {
                viewModelScope.launch {
                    profileRepository.saveProfiles(result.data.profiles)
                }
                Result.Success(result.data.profiles.size)
            }
            is Result.Error -> result
        }
    }
}
