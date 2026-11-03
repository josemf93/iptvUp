package com.iptvup.app.ui.epg

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.local.ChannelEntity
import com.iptvup.app.data.local.EpgDao
import com.iptvup.app.data.local.EpgProgramEntity
import com.iptvup.app.data.local.HiddenItemEntity
import com.iptvup.app.data.local.containsPattern
import com.iptvup.app.data.local.dedupePrograms
import com.iptvup.app.data.repository.GroupOrderRepository
import com.iptvup.app.data.repository.HiddenRepository
import com.iptvup.app.data.sync.SyncManager
import com.iptvup.app.data.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EpgChannelUi(
    val channel: ChannelEntity,
    val programs: List<EpgProgramEntity>,
    val now: EpgProgramEntity?,
    val next: EpgProgramEntity?
)

data class EpgUi(
    val group: String?,
    val windowStart: Long,
    val windowEnd: Long,
    val channels: List<EpgChannelUi>
)

private const val EPG_ID_CHUNK = 400

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EpgViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val hiddenRepository: HiddenRepository,
    private val groupOrderRepository: GroupOrderRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val profileId: String = checkNotNull(savedStateHandle["profileId"])

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

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
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val groupOrder = groupOrderRepository.observe(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
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
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val groupCounts = channelDao.observeGroupCounts(profileId)
        .map { rows -> rows.associate { it.groupName to it.total } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    val hiddenGroupKeys = hidden.map { items ->
        items.filter { it.kind == HiddenRepository.KIND_GROUP }
            .map { it.itemKey }
            .toSet()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )

    val groups = combine(allGroups, hiddenGroupKeys) { all, hiddenKeys ->
        all.filter { it !in hiddenKeys }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val groupChannels: Flow<Pair<String?, List<ChannelEntity>>> = combine(
        _selectedGroup,
        _query
    ) { group, text ->
        group to text
    }.flatMapLatest { (group, text) ->
        if (group == null) {
            flowOf(null to emptyList())
        } else {
            channelDao.observeVisible(profileId, group, containsPattern(text))
                .map { group to it }
        }
    }

    val ui: StateFlow<EpgUi> = combine(
        groupChannels,
        syncManager.refreshTicks(profileId)
    ) { loaded, now ->
        Triple(loaded.first, loaded.second, now)
    }.mapLatest { (group, channels, now) ->
        buildUi(group, channels, now)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyUi(System.currentTimeMillis())
    )

    init {
        viewModelScope.launch {
            runCatching { hiddenRepository.refresh(profileId) }
        }
        viewModelScope.launch {
            runCatching { groupOrderRepository.refresh(profileId) }
            combine(channelDao.observeGroups(profileId), hidden, groupOrder) { groups, _, _ ->
                groups
            }.first()
            _ready.value = true
        }
        viewModelScope.launch {
            syncManager.syncIfEmpty(profileId)
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

    fun sync() {
        syncManager.sync(profileId)
    }

    fun dismissError() {
        syncManager.clearError(profileId)
    }

    private suspend fun buildUi(group: String?, channels: List<ChannelEntity>, now: Long): EpgUi {
        val windowStart = roundToHalfHour(now)
        val windowEnd = windowStart + WINDOW_MS
        val programs = programsFor(channels, windowStart, windowEnd)
        return EpgUi(
            group = group,
            windowStart = windowStart,
            windowEnd = windowEnd,
            channels = channels.map { channel ->
                val list = channel.epgChannelId?.let { programs[it] }.orEmpty()
                EpgChannelUi(
                    channel = channel,
                    programs = list,
                    now = list.firstOrNull { it.startMs <= now && it.endMs > now },
                    next = list.firstOrNull { it.startMs > now }
                )
            }
        )
    }

    private suspend fun programsFor(
        channels: List<ChannelEntity>,
        from: Long,
        to: Long
    ): Map<String, List<EpgProgramEntity>> {
        val epgIds = channels.mapNotNull { it.epgChannelId }.distinct()
        if (epgIds.isEmpty()) return emptyMap()
        val rows = epgIds.chunked(EPG_ID_CHUNK).flatMap { ids ->
            epgDao.getWindowForChannels(profileId, ids, from, to)
        }
        return withContext(Dispatchers.Default) {
            rows.groupBy { it.epgChannelId }
                .mapValues { (_, list) -> list.dedupePrograms() }
        }
    }

    private fun emptyUi(now: Long): EpgUi {
        val windowStart = roundToHalfHour(now)
        return EpgUi(
            group = null,
            windowStart = windowStart,
            windowEnd = windowStart + WINDOW_MS,
            channels = emptyList()
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
