package com.iptvup.app.ui.live

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.iptvup.app.core.settings.SettingsStore
import com.iptvup.app.data.local.ChannelEntity
import com.iptvup.app.data.local.ChannelDao
import com.iptvup.app.data.local.EpgDao
import com.iptvup.app.data.local.EpgProgramEntity
import com.iptvup.app.data.local.HiddenItemEntity
import com.iptvup.app.data.local.containsPattern
import com.iptvup.app.data.local.toParsedProgram
import com.iptvup.app.data.local.dedupePrograms
import com.iptvup.app.data.model.EpgEntry
import com.iptvup.app.data.repository.FavoritesRepository
import com.iptvup.app.data.repository.GroupOrderRepository
import com.iptvup.app.data.repository.HiddenRepository
import com.iptvup.app.data.sync.SyncManager
import com.iptvup.app.data.sync.SyncState
import com.iptvup.app.ui.player.buildStreamPlayer
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
import okhttp3.OkHttpClient
import javax.inject.Inject

data class LiveChannelUi(
    val channel: ChannelEntity,
    val favorite: Boolean,
    val epg: EpgEntry
)

data class LiveChannelsUi(
    val group: String?,
    val items: List<LiveChannelUi>
)

private const val EPG_ID_CHUNK = 400

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val favoritesRepository: FavoritesRepository,
    private val hiddenRepository: HiddenRepository,
    private val groupOrderRepository: GroupOrderRepository,
    private val syncManager: SyncManager,
    private val okHttp: OkHttpClient
) : ViewModel() {

    val profileId: String = checkNotNull(savedStateHandle["profileId"])

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    private val _focusedChannel = MutableStateFlow<ChannelEntity?>(null)
    val focusedChannel: StateFlow<ChannelEntity?> = _focusedChannel.asStateFlow()

    private val _orderDraft = MutableStateFlow<List<String>?>(null)

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

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val groupOrder = groupOrderRepository.observe(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val favorites = favoritesRepository.observe(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )

    val hidden: StateFlow<List<HiddenItemEntity>> = hiddenRepository.observe(profileId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val allGroups = combine(
        channelDao.observeGroups(profileId),
        hidden,
        groupOrder,
        _orderDraft
    ) { stored, hiddenItems, order, draft ->
        if (draft != null) {
            draft
        } else {
            val hiddenKeys = hiddenItems
                .filter { it.kind == HiddenRepository.KIND_GROUP }
                .map { it.itemKey }
            groupOrderRepository.sortGroups(
                (stored + hiddenKeys).distinct(),
                order
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
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

    val channels: StateFlow<LiveChannelsUi> = combine(
        groupChannels,
        favorites,
        syncManager.refreshTicks(profileId)
    ) { (group, list), favoriteKeys, now ->
        Triple(group, list, Pair(favoriteKeys, now))
    }.mapLatest { (group, list, rest) ->
        val (favoriteKeys, now) = rest
        val epg = epgFor(list, now)
        LiveChannelsUi(
            group = group,
            items = list.map {
                LiveChannelUi(
                    channel = it,
                    favorite = it.channelKey in favoriteKeys,
                    epg = it.epgChannelId?.let { id -> epg[id] } ?: EpgEntry()
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LiveChannelsUi(group = null, items = emptyList())
    )

    val focusedPrograms: StateFlow<List<EpgProgramEntity>> = combine(
        _focusedChannel,
        syncManager.refreshTicks(profileId)
    ) { channel, now ->
        channel to now
    }.mapLatest { (channel, now) ->
        val epgId = channel?.epgChannelId ?: return@mapLatest emptyList()
        epgDao.getForChannel(
            profileId = profileId,
            epgId = epgId,
            from = now - 60 * 60 * 1000L,
            to = now + 12 * 60 * 60 * 1000L
        ).dedupePrograms()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    init {
        favoritesRepository.listen(profileId)
        hiddenRepository.listen(profileId)
        groupOrderRepository.listen(profileId)
        viewModelScope.launch {
            combine(
                channelDao.observeGroups(profileId),
                hiddenRepository.observe(profileId),
                groupOrderRepository.observe(profileId)
            ) { groups, _, _ ->
                groups
            }.first()
            _ready.value = true
        }
        viewModelScope.launch {
            syncManager.syncIfEmpty(profileId)
        }
    }

    fun createPreviewPlayer(context: Context): ExoPlayer =
        buildStreamPlayer(context, okHttp).apply { volume = 0f }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun selectGroup(group: String?) {
        _selectedGroup.value = group
    }

    fun focusChannel(channel: ChannelEntity?) {
        _focusedChannel.value = channel
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

    /** Empieza a reordenar: copia el orden actual a un borrador que se guarda al terminar. */
    fun beginGroupMove() {
        if (_orderDraft.value == null) {
            _orderDraft.value = allGroups.value
        }
    }

    /** Mueve un grupo hacia el siguiente grupo visible en la dirección indicada (-1 o 1). */
    fun moveGroup(group: String, direction: Int) {
        val base = _orderDraft.value ?: allGroups.value
        val hiddenKeys = hidden.value
            .filter { it.kind == HiddenRepository.KIND_GROUP }
            .map { it.itemKey }
            .toSet()
        val from = base.indexOf(group)
        if (from < 0) return
        var to = from + direction
        while (to in base.indices && base[to] in hiddenKeys) {
            to += direction
        }
        if (to !in base.indices) return
        _orderDraft.value = base.toMutableList().also { list ->
            val item = list[from]
            list[from] = list[to]
            list[to] = item
        }
    }

    /** Termina de reordenar y guarda el orden en la cuenta. */
    fun endGroupMove() {
        val draft = _orderDraft.value ?: return
        viewModelScope.launch {
            groupOrderRepository.save(profileId, draft)
            _orderDraft.value = null
        }
    }

    private suspend fun epgFor(channels: List<ChannelEntity>, now: Long): Map<String, EpgEntry> {
        val epgIds = channels.mapNotNull { it.epgChannelId }.distinct()
        if (epgIds.isEmpty()) return emptyMap()
        val programs = epgIds.chunked(EPG_ID_CHUNK).flatMap { ids ->
            epgDao.getWindowForChannels(
                profileId = profileId,
                epgIds = ids,
                from = now - 60 * 60 * 1000L,
                to = now + 6 * 60 * 60 * 1000L
            )
        }
        return withContext(Dispatchers.Default) {
            val byEpgId = programs.groupBy { it.epgChannelId }
            epgIds.associateWith { epgId ->
                val list = byEpgId[epgId].orEmpty().dedupePrograms()
                EpgEntry(
                    now = list.firstOrNull { it.startMs <= now && it.endMs > now }
                        ?.toParsedProgram(),
                    next = list.firstOrNull { it.startMs > now }?.toParsedProgram()
                )
            }
        }
    }
}
