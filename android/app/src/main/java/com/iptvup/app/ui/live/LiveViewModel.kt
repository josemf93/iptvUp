package com.iptvup.app.ui.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.local.ChannelEntity
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.local.EpgDao
import com.iptvup.app.data.local.HiddenItemEntity
import com.iptvup.app.data.local.toParsedProgram
import com.iptvup.app.data.model.EpgEntry
import com.iptvup.app.data.repository.FavoritesRepository
import com.iptvup.app.data.repository.HiddenRepository
import com.iptvup.app.data.repository.PlaylistRepository
import com.iptvup.app.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveChannelUi(
    val channel: ChannelEntity,
    val favorite: Boolean,
    val epg: EpgEntry
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val favoritesRepository: FavoritesRepository,
    private val hiddenRepository: HiddenRepository,
    private val profileRepository: ProfileRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val profileId: String = checkNotNull(savedStateHandle["profileId"])

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _epg = MutableStateFlow<Map<String, EpgEntry>>(emptyMap())

    val favorites = favoritesRepository.observe(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet()
    )

    val hidden: StateFlow<List<HiddenItemEntity>> = hiddenRepository.observe(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val allGroups = channelDao.observeGroups(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val groups = combine(allGroups, hidden) { all, hiddenItems ->
        val hiddenGroups = hiddenItems
            .filter { it.kind == HiddenRepository.KIND_GROUP }
            .map { it.itemKey }
            .toSet()
        all.filter { it !in hiddenGroups }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val filters = combine(query, selectedGroup) { text, group ->
        text to group
    }

    private val visibility = combine(favorites, hidden) { favoriteKeys, hiddenItems ->
        favoriteKeys to hiddenItems
    }

    val channels: StateFlow<List<LiveChannelUi>> = combine(
        channelDao.observeAll(profileId),
        filters,
        visibility,
        _epg
    ) { all, (text, group), (favoriteKeys, hiddenItems), epgMap ->
        val hiddenGroups = hiddenItems
            .filter { it.kind == HiddenRepository.KIND_GROUP }
            .map { it.itemKey }
            .toSet()
        val hiddenChannels = hiddenItems
            .filter { it.kind == HiddenRepository.KIND_CHANNEL }
            .map { it.itemKey }
            .toSet()
        all.asSequence()
            .filter { it.groupName !in hiddenGroups }
            .filter { it.channelKey !in hiddenChannels }
            .filter { group == null || it.groupName == group }
            .filter {
                text.isBlank() ||
                    it.name.contains(text, ignoreCase = true) ||
                    it.groupName.contains(text, ignoreCase = true)
            }
            .map {
                LiveChannelUi(
                    channel = it,
                    favorite = it.channelKey in favoriteKeys,
                    epg = epgMap[it.channelKey] ?: EpgEntry()
                )
            }
            .toList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            runCatching { favoritesRepository.refresh(profileId) }
            runCatching { hiddenRepository.refresh(profileId) }
        }
        viewModelScope.launch {
            if (channelDao.count(profileId) == 0) {
                sync()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshEpg()
                delay(60_000L)
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun selectGroup(group: String?) {
        _selectedGroup.value = group
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            favoritesRepository.toggle(profileId, channel.channelKey)
        }
    }

    fun toggleChannelHidden(channel: ChannelEntity) {
        viewModelScope.launch {
            hiddenRepository.toggleChannel(profileId, channel.channelKey)
        }
    }

    fun toggleGroupHidden(group: String) {
        if (_selectedGroup.value == group) {
            _selectedGroup.value = null
        }
        viewModelScope.launch {
            hiddenRepository.toggleGroup(profileId, group)
        }
    }

    fun sync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            _error.value = null
            _status.value = "Preparando..."
            val profile = profileRepository.getById(profileId)
            if (profile == null) {
                _error.value = "No se encontró el perfil"
                _syncing.value = false
                return@launch
            }
            try {
                val credentials = profileRepository.getCredentials(profile)
                playlistRepository.sync(profile, credentials) { message ->
                    _status.value = message
                }.onSuccess {
                    refreshEpg()
                }.onFailure {
                    _error.value = it.message ?: "No se pudo actualizar la lista"
                }
            } catch (exception: Exception) {
                _error.value = exception.message ?: "No se pudo actualizar la lista"
            }
            _syncing.value = false
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private suspend fun refreshEpg() {
        val now = System.currentTimeMillis()
        val programs = epgDao.getWindow(
            profileId = profileId,
            from = now - 60 * 60 * 1000L,
            to = now + 6 * 60 * 60 * 1000L
        )
        val byEpgId = programs.groupBy { it.epgChannelId }
        val channelsSnapshot = channelDao.observeAll(profileId).first()
        _epg.value = channelsSnapshot.mapNotNull { channel ->
            val epgId = channel.epgChannelId ?: return@mapNotNull null
            val list = byEpgId[epgId].orEmpty()
            channel.channelKey to EpgEntry(
                now = list.firstOrNull { it.startMs <= now && it.endMs > now }
                    ?.toParsedProgram(),
                next = list.firstOrNull { it.startMs > now }
                    ?.toParsedProgram()
            )
        }.toMap()
    }
}
