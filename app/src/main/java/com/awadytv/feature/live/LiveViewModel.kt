package com.awadytv.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awadytv.core.datastore.SettingsRepository
import com.awadytv.core.security.ParentalGate
import com.awadytv.data.repository.EpgRepository
import com.awadytv.data.repository.LibraryRepository
import com.awadytv.data.repository.LiveRepository
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.domain.Category
import com.awadytv.domain.Channel
import com.awadytv.domain.ContentKind
import com.awadytv.domain.NowNext
import com.awadytv.domain.Session
import com.awadytv.domain.applyCategoryOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveUiState(
    val loadingCategories: Boolean = true,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val loadingChannels: Boolean = true,
    val channels: List<Channel> = emptyList(),
    val favourites: Set<Long> = emptySet(),
    val epg: Map<String, NowNext> = emptyMap(),
    val adultLocked: Boolean = false,
    val pinPromptFor: Category? = null,
    val now: Long = System.currentTimeMillis(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val live: LiveRepository,
    private val epgRepo: EpgRepository,
    private val library: LibraryRepository,
    private val parental: ParentalGate,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LiveUiState(adultLocked = parental.locked()))
    val state: StateFlow<LiveUiState> = _state
    private val selected = MutableStateFlow<String?>(null)
    private var session: Session? = null
    private var epgJob: Job? = null

    init {
        viewModelScope.launch {
            profiles.session.filterNotNull().flatMapLatest { s ->
                session = s
                combine(live.categories(s.profileId), settings.categoryOrder(s.profileId, ContentKind.LIVE.key)) { cats, order -> cats to order }
            }.collect { (cats, order) ->
                val withVirtual = listOf(Category(LiveRepository.FAVOURITES, "Favourites", false), Category(LiveRepository.ALL, "All channels", false)) + cats
                _state.update { it.copy(loadingCategories = false, categories = applyCategoryOrder(withVirtual, order)) }
                if (selected.value == null) {
                    val favCount = session?.let { live.favouriteChannels(it.profileId).size } ?: 0
                    select(if (favCount > 0) LiveRepository.FAVOURITES else cats.firstOrNull { c -> !c.isAdult }?.id ?: LiveRepository.ALL)
                }
            }
        }
        viewModelScope.launch {
            combine(selected.filterNotNull(), profiles.session.filterNotNull()) { cat, s -> cat to s }
                .flatMapLatest { (cat, s) ->
                    _state.update { it.copy(loadingChannels = true) }
                    live.channels(s.profileId, cat)
                }
                .collect { list ->
                    _state.update { it.copy(loadingChannels = false, channels = list) }
                    refreshEpg(list, prefetch = true)
                }
        }
        viewModelScope.launch {
            profiles.session.filterNotNull().flatMapLatest { library.favouriteIds(it.profileId, ContentKind.LIVE) }
                .collect { ids -> _state.update { it.copy(favourites = ids.toSet()) } }
        }
        viewModelScope.launch {
            parental.unlocked.collect { _state.update { it.copy(adultLocked = parental.locked()) } }
        }
        // Keep "now playing" progress bars moving.
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                _state.update { it.copy(now = System.currentTimeMillis()) }
                refreshEpg(_state.value.channels, prefetch = false)
            }
        }
    }

    fun select(categoryId: String) {
        val category = _state.value.categories.firstOrNull { it.id == categoryId }
        if (category != null && category.isAdult && parental.locked()) {
            _state.update { it.copy(pinPromptFor = category) }
            return
        }
        if (selected.value == categoryId) return
        selected.value = categoryId
        _state.update { it.copy(selectedCategoryId = categoryId) }
        val s = session ?: return
        if (!categoryId.startsWith("__")) viewModelScope.launch { library.recordCategoryOpen(s.profileId, ContentKind.LIVE, categoryId) }
    }

    /** Moves a category one step up (-1) or down (+1) in the rail and saves the order. */
    fun moveCategory(categoryId: String, delta: Int) {
        val s = session ?: return
        val list = _state.value.categories.toMutableList()
        val from = list.indexOfFirst { it.id == categoryId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        _state.update { it.copy(categories = list) }
        viewModelScope.launch { settings.setCategoryOrder(s.profileId, ContentKind.LIVE.key, list.map { it.id }) }
    }

    fun submitPin(pin: String): Boolean {
        val category = _state.value.pinPromptFor ?: return false
        if (!parental.unlock(pin)) return false
        _state.update { it.copy(pinPromptFor = null) }
        select(category.id)
        return true
    }

    fun dismissPin() = _state.update { it.copy(pinPromptFor = null) }

    fun toggleFavourite(channel: Channel) {
        val s = session ?: return
        val fav = channel.streamId in _state.value.favourites
        viewModelScope.launch { library.setFavourite(s.profileId, ContentKind.LIVE, channel.streamId, !fav) }
    }

    /** Fetch short EPG for the focused channel if the cache does not cover it. */
    fun onChannelFocused(channel: Channel) {
        val s = session ?: return
        viewModelScope.launch {
            epgRepo.ensureShortEpg(s, channel)
            val update = epgRepo.nowNext(s.profileId, listOf(channel))
            if (update.isNotEmpty()) _state.update { it.copy(epg = it.epg + update) }
        }
    }

    private fun refreshEpg(channels: List<Channel>, prefetch: Boolean) {
        val s = session ?: return
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            _state.update { it.copy(epg = epgRepo.nowNext(s.profileId, channels)) }
            if (prefetch) {
                // Warm the first screenful sequentially (panels rate-limit parallel calls).
                channels.take(12).forEach { channel ->
                    epgRepo.ensureShortEpg(s, channel)
                }
                _state.update { it.copy(epg = epgRepo.nowNext(s.profileId, channels)) }
            }
        }
    }
}
