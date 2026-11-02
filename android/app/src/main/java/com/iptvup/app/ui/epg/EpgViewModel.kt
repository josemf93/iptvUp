package com.iptvup.app.ui.epg

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.local.ChannelEntity
import com.iptvup.app.data.local.EpgDao
import com.iptvup.app.data.local.EpgProgramEntity
import com.iptvup.app.data.local.HiddenItemEntity
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EpgChannelUi(
    val channel: ChannelEntity,
    val programs: List<EpgProgramEntity>,
    val now: EpgProgramEntity?,
    val next: EpgProgramEntity?
)

data class EpgUi(
    val windowStart: Long,
    val windowEnd: Long,
    val channels: List<EpgChannelUi>
)

@HiltViewModel
class EpgViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val hiddenRepository: HiddenRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val profileId: String = checkNotNull(savedStateHandle["profileId"])

    private val _windowStart = MutableStateFlow(roundToHalfHour(System.currentTimeMillis()))
    private val _programs = MutableStateFlow<List<EpgProgramEntity>>(emptyList())

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

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

    private val hidden: StateFlow<List<HiddenItemEntity>> = hiddenRepository.observe(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val allGroups = channelDao.observeGroups(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val hiddenGroupKeys = hidden.map { items ->
        items.filter { it.kind == HiddenRepository.KIND_GROUP }
            .map { it.itemKey }
            .toSet()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet()
    )

    val groups = combine(allGroups, hiddenGroupKeys) { all, hiddenKeys ->
        all.filter { it !in hiddenKeys }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val filters = combine(hidden, _selectedGroup, _query) { hiddenItems, group, text ->
        Triple(hiddenItems, group, text)
    }

    val ui: StateFlow<EpgUi> = combine(
        channelDao.observeAll(profileId),
        filters,
        _programs,
        _windowStart
    ) { channels, (hiddenItems, selectedGroup, query), programs, windowStart ->
        val hiddenGroups = hiddenItems
            .filter { it.kind == HiddenRepository.KIND_GROUP }
            .map { it.itemKey }
            .toSet()
        val hiddenChannels = hiddenItems
            .filter { it.kind == HiddenRepository.KIND_CHANNEL }
            .map { it.itemKey }
            .toSet()
        val byEpgId = programs.groupBy { it.epgChannelId }
        val now = System.currentTimeMillis()
        val visibleChannels = channels
            .asSequence()
            .filter { it.groupName !in hiddenGroups }
            .filter { it.channelKey !in hiddenChannels }
            .filter { selectedGroup == null || it.groupName == selectedGroup }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.groupName.contains(query, ignoreCase = true)
            }
            .mapNotNull { channel ->
                val epgId = channel.epgChannelId ?: return@mapNotNull null
                val list = byEpgId[epgId].orEmpty().sortedBy { it.startMs }
                EpgChannelUi(
                    channel = channel,
                    programs = list,
                    now = list.firstOrNull { it.startMs <= now && it.endMs > now },
                    next = list.firstOrNull { it.startMs > now }
                )
            }
            .toList()
        EpgUi(
            windowStart = windowStart,
            windowEnd = windowStart + WINDOW_MS,
            channels = visibleChannels
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EpgUi(
            windowStart = _windowStart.value,
            windowEnd = _windowStart.value + WINDOW_MS,
            channels = emptyList()
        )
    )

    init {
        viewModelScope.launch {
            runCatching { hiddenRepository.refresh(profileId) }
        }
        viewModelScope.launch {
            syncManager.state.collect { state ->
                if (state is SyncState.Success && state.profileId == profileId) {
                    loadPrograms()
                }
            }
        }
        viewModelScope.launch {
            loadPrograms()
            while (isActive) {
                delay(5 * 60 * 1000L)
                loadPrograms()
            }
        }
    }

    fun selectGroup(group: String?) {
        _selectedGroup.value = group
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun toggleGroupHidden(group: String) {
        if (_selectedGroup.value == group) {
            _selectedGroup.value = null
        }
        viewModelScope.launch {
            hiddenRepository.toggleGroup(profileId, group)
        }
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

    fun refresh() {
        viewModelScope.launch { loadPrograms() }
    }

    fun sync() {
        syncManager.sync(profileId)
    }

    fun dismissError() {
        syncManager.clearError(profileId)
    }

    private suspend fun loadPrograms() {
        val start = roundToHalfHour(System.currentTimeMillis())
        _windowStart.value = start
        _programs.value = epgDao.getWindow(
            profileId = profileId,
            from = start,
            to = start + WINDOW_MS
        )
    }

    private companion object {
        const val WINDOW_MS = 6 * 60 * 60 * 1000L

        fun roundToHalfHour(ms: Long): Long {
            val halfHour = 30 * 60 * 1000L
            return (ms / halfHour) * halfHour
        }
    }
}
