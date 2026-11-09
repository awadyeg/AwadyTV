package com.awadytv.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.EmptyView
import com.awadytv.core.designsystem.GlassSurface
import com.awadytv.core.designsystem.GlassToastHost
import com.awadytv.core.designsystem.rememberToastState
import com.awadytv.core.designsystem.LoadingView
import com.awadytv.core.designsystem.LocalBackdrop
import com.awadytv.core.designsystem.PinDialog
import com.awadytv.core.designsystem.PosterCard
import com.awadytv.core.designsystem.RailItem
import com.awadytv.core.datastore.SettingsRepository
import com.awadytv.core.security.ParentalGate
import com.awadytv.data.recommendations.RecommendationRepository
import com.awadytv.data.repository.LibraryRepository
import com.awadytv.data.repository.ProfileRepository
import com.awadytv.domain.Category
import com.awadytv.domain.applyCategoryOrder
import com.awadytv.domain.ContentKind
import com.awadytv.domain.PosterItem
import com.awadytv.domain.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseUiState(
    val loadingCategories: Boolean = true,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val loadingItems: Boolean = true,
    val items: List<PosterItem> = emptyList(),
    val adultLocked: Boolean = false,
    val pinPromptFor: Category? = null,
)

/**
 * Shared logic of the Movies and Series tabs: category rail + poster grid, offline-first from Room.
 * The rail starts with "For you" (the recommendation engine's ranking of this tab's catalog) when AI
 * recommendations are on, and the user can reorder it (hold OK on a category).
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class CatalogBrowseViewModel(
    private val profiles: ProfileRepository,
    private val library: LibraryRepository,
    private val parental: ParentalGate,
    private val settings: SettingsRepository,
    private val recommendations: RecommendationRepository,
    private val kind: ContentKind,
    private val recentId: String,
    private val recentLabel: String,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState(adultLocked = parental.locked()))
    val state: StateFlow<BrowseUiState> = _state
    private val _messages = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private val selected = MutableStateFlow<String?>(null)
    private var session: Session? = null

    protected abstract fun categoriesFlow(profileId: Long): Flow<List<Category>>
    protected abstract fun itemsFlow(profileId: Long, categoryId: String): Flow<List<PosterItem>>

    protected fun start() {
        viewModelScope.launch {
            profiles.session.filterNotNull().flatMapLatest { s ->
                session = s
                combine(categoriesFlow(s.profileId), settings.categoryOrder(s.profileId, kind.key), settings.settings.map { it.aiRecommendations }) { cats, order, ai ->
                    val virtual = listOfNotNull(Category(FOR_YOU, "For you", false).takeIf { ai }, Category(recentId, recentLabel, false))
                    applyCategoryOrder(virtual + cats, order) to ai
                }
            }.collect { (cats, ai) ->
                _state.update { it.copy(loadingCategories = false, categories = cats) }
                if (selected.value == null) select(if (ai) FOR_YOU else recentId)
            }
        }
        viewModelScope.launch {
            combine(selected.filterNotNull(), profiles.session.filterNotNull()) { c, s -> c to s }
                .flatMapLatest { (cat, s) ->
                    _state.update { it.copy(loadingItems = true) }
                    if (cat == FOR_YOU) forYou(s.profileId) else itemsFlow(s.profileId, cat)
                }
                .collect { items -> _state.update { it.copy(loadingItems = false, items = items) } }
        }
        viewModelScope.launch {
            parental.unlocked.collect { _state.update { it.copy(adultLocked = parental.locked()) } }
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
        if (categoryId != recentId && !categoryId.startsWith("__")) viewModelScope.launch { library.recordCategoryOpen(s.profileId, kind, categoryId) }
    }

    /** Precomputed by the recommendation pass (startup, and after every like, dislike or favourite). */
    private fun forYou(profileId: Long): Flow<List<PosterItem>> = recommendations.forYou(profileId, kind)

    /** Moves a category one step up (-1) or down (+1) in the rail and saves the order. */
    fun moveCategory(categoryId: String, delta: Int) {
        val s = session ?: return
        val list = _state.value.categories.toMutableList()
        val from = list.indexOfFirst { it.id == categoryId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        _state.update { it.copy(categories = list) }
        viewModelScope.launch { settings.setCategoryOrder(s.profileId, kind.key, list.map { it.id }) }
    }

    companion object {
        const val FOR_YOU = "__foryou__"
    }

    fun submitPin(pin: String): Boolean {
        val category = _state.value.pinPromptFor ?: return false
        if (!parental.unlock(pin)) return false
        _state.update { it.copy(pinPromptFor = null) }
        select(category.id)
        return true
    }

    fun dismissPin() = _state.update { it.copy(pinPromptFor = null) }

    /** Hold OK on a poster: like/unlike it (feeds recommendations). */
    fun toggleLike(item: PosterItem) {
        val s = session ?: return
        viewModelScope.launch {
            val liked = library.isLiked(s.profileId, item.kind, item.id).first()
            library.setLiked(s.profileId, item.kind, item.id, !liked)
            _messages.send(if (liked) "Removed “${item.title}” from likes" else "♥ Liked “${item.title}”")
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CatalogBrowseScreen(
    title: String,
    state: BrowseUiState,
    onSelectCategory: (String) -> Unit,
    onOpen: (PosterItem) -> Unit,
    onSubmitPin: (String) -> Boolean,
    onDismissPin: () -> Unit,
    emptyTitle: String,
    onLike: (PosterItem) -> Unit = {},
    messages: Flow<String>? = null,
    onMoveCategory: (String, Int) -> Unit = { _, _ -> },
) {
    val backdrop = LocalBackdrop.current
    val toast = rememberToastState()
    LaunchedEffect(messages) { messages?.collect { toast.show(it) } }
    var dwell by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dwell) {
        val id = dwell ?: return@LaunchedEffect
        delay(400)
        onSelectCategory(id)
    }
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = AwadyDimens.SafeHorizontal, end = AwadyDimens.SafeHorizontal, top = AwadyDimens.ContentTop, bottom = AwadyDimens.SafeVertical),
    ) {
        CategoryRail(
            title = title,
            loading = state.loadingCategories,
            categories = state.categories,
            selectedId = state.selectedCategoryId,
            adultLocked = state.adultLocked,
            onSelect = onSelectCategory,
            onDwell = { dwell = it },
            onMove = onMoveCategory,
            modifier = Modifier.width(AwadyDimens.SideRailWidth - 40.dp).fillMaxHeight(),
        )
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val heading = state.categories.firstOrNull { it.id == state.selectedCategoryId }?.name ?: title
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 8.dp)) {
                Text(heading, style = AwadyType.Title, color = AwadyColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (!state.loadingItems && state.items.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text("${state.items.size} titles", style = AwadyType.Caption, color = AwadyColors.TextTertiary, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            when {
                state.loadingItems -> LoadingView()
                state.items.isEmpty() && state.selectedCategoryId == CatalogBrowseViewModel.FOR_YOU ->
                    LoadingView(message = "Preparing your picks")
                state.items.isEmpty() -> EmptyView(emptyTitle, "Nothing in this category yet. Pick another one on the left.", icon = Icons.Rounded.Movie)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(AwadyDimens.PosterWidth + 8.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(state.items, key = { "${it.kind}:${it.id}" }) { item ->
                        PosterCard(
                            title = item.title,
                            imageUrl = item.image,
                            badge = item.badge,
                            progress = item.progress,
                            onClick = { onOpen(item) },
                            onLongClick = { onLike(item) },
                            onFocus = { backdrop.show(item.backdrop ?: item.image) },
                        )
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        GlassToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = AwadyDimens.SafeVertical + 8.dp))
    }
    state.pinPromptFor?.let { category ->
        PinDialog(title = "Unlock ${category.name}", onDismiss = onDismissPin, onSubmit = onSubmitPin)
    }
}

fun ratingBadge(rating: Double?): String? = rating?.takeIf { it > 0 }?.let { "★ " + String.format(java.util.Locale.US, "%.1f", it) }
