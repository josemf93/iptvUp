package com.iptvup.app.ui.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.local.ChannelEntity
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.local.EpgDao
import com.iptvup.app.data.local.HiddenItemEntity
import com.iptvup.app.data.local.toParsedProgram
import com.iptvup.app.data.local.dedupePrograms
import com.iptvup.app.data.identity.ChannelQuality
import com.iptvup.app.data.model.EpgEntry
import com.iptvup.app.data.repository.FavoritesRepository
import com.iptvup.app.data.repository.GroupOrderRepository
import com.iptvup.app.data.repository.HiddenRepository
import com.iptvup.app.data.sync.SyncManager
import com.iptvup.app.data.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val groupOrderRepository: GroupOrderRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val profileId: String = checkNotNull(savedStateHandle["profileId"])

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    val syncing: StateFlow<Boolean> = syncManager.state
        .map { it is SyncState.Running && it.profileId == profileId }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val status: StateFlow<String> = syncManager.state
        .map {
            (it as? SyncState.Running)
                ?.takeIf { running -> running.profileId == profileId }
                ?.status
                .orEmpty()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    val error: StateFlow<String?> = syncManager.state
        .map {
            (it as? SyncState.Error)
                ?.takeIf { failure -> failure.profileId == profileId }
                ?.message
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _epg = MutableStateFlow<Map<String, EpgEntry>>(emptyMap())

    val groupOrder = groupOrderRepository.observe(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

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

    val allGroups = combine(channelDao.observeGroups(profileId), hidden, groupOrder) {
            stored, hiddenItems, order ->
        val hiddenKeys = hiddenItems
            .filter { it.kind == HiddenRepository.KIND_GROUP }
            .map { it.itemKey }
        groupOrderRepository.sortGroups(
            (stored + hiddenKeys).distinct(),
            order
        )
    }.stateIn(
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

    val groupCounts = channelDao.observeAll(profileId)
        .map { all ->
            all.filter { !ChannelQuality.isJunkChannel(it.name) }
                .groupBy { it.groupName }
                .mapValues { it.value.size }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
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
        all.asSequence()
            .filter { it.groupName !in hiddenGroups }
            .filter { !ChannelQuality.isJunkChannel(it.name) }
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
            runCatching { groupOrderRepository.refresh(profileId) }
            combine(channelDao.observeAll(profileId), hidden, groupOrder) { channels, _, _ ->
                channels
            }.first()
            _ready.value = true
        }
        viewModelScope.launch {
            syncManager.syncIfEmpty(profileId)
        }
        viewModelScope.launch {
            syncManager.state.collect { state ->
                if (state is SyncState.Success && state.profileId == profileId) {
                    refreshEpg()
                }
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

    fun toggleGroupHidden(group: String) {
        if (_selectedGroup.value == group) {
            _selectedGroup.value = null
        }
        viewModelScope.launch {
            hiddenRepository.toggleGroup(profileId, group)
        }
    }

    fun sync() {
        syncManager.sync(profileId)
    }

    fun dismissError() {
        syncManager.clearError(profileId)
    }

    fun showAllGroups() {
        viewModelScope.launch {
            hiddenRepository.setGroupsHidden(profileId, allGroups.value, hidden = false)
        }
    }

    fun hideAllGroups() {
        _selectedGroup.value = null
        viewModelScope.launch {
            hiddenRepository.setGroupsHidden(profileId, allGroups.value, hidden = true)
        }
    }

    fun saveGroupOrder(groups: List<String>) {
        viewModelScope.launch {
            groupOrderRepository.save(profileId, groups)
        }
    }

    private suspend fun refreshEpg() {
        val now = System.currentTimeMillis()
        val programs = epgDao.getWindow(
            profileId = profileId,
            from = now - 60 * 60 * 1000L,
            to = now + 6 * 60 * 60 * 1000L
        )
        val byEpgId = programs.groupBy { it.epgChannelId }
            .mapValues { (_, list) -> list.dedupePrograms() }
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
