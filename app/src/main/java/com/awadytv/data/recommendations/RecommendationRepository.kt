package com.awadytv.data.recommendations

import com.awadytv.core.database.CategoryOpenDao
import com.awadytv.core.database.ItemKind
import com.awadytv.core.database.LikeDao
import com.awadytv.core.database.LikeSource
import com.awadytv.core.database.MovieEntity
import com.awadytv.core.database.ProgressDao
import com.awadytv.core.database.RecommendationDao
import com.awadytv.core.database.RecommendationEntity
import com.awadytv.core.database.SeriesEntity
import com.awadytv.core.datastore.SettingsRepository
import com.awadytv.core.security.ParentalGate
import com.awadytv.core.util.TimeFormat
import com.awadytv.data.repository.LibraryRepository
import com.awadytv.data.repository.MoviesRepository
import com.awadytv.data.repository.SeriesRepository
import com.awadytv.di.ApplicationScope
import com.awadytv.domain.AdultContent
import com.awadytv.domain.ContentKind
import com.awadytv.domain.PosterItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A recommendation shelf ready for the UI. */
data class RecommendationShelf(val id: String, val title: String, val items: List<PosterItem>)

/**
 * Runs [RecommendationEngine] over the cached catalog and stores results in Room with a 6h TTL.
 * Recomputes immediately (debounced) after a like changes. An optional re-ranker (see
 * [ReRanker]) may reorder the "Recommended for you" shelf; any failure keeps the local order.
 */
@Singleton
class RecommendationRepository @Inject constructor(
    private val movies: MoviesRepository,
    private val series: SeriesRepository,
    private val likes: LikeDao,
    private val progress: ProgressDao,
    private val categoryOpens: CategoryOpenDao,
    private val dao: RecommendationDao,
    private val settings: SettingsRepository,
    private val parental: ParentalGate,
    private val reRanker: ReRanker,
    library: LibraryRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val engine = RecommendationEngine()
    private val mutex = Mutex()
    private var cachedIndex: Pair<String, CatalogIndex>? = null

    init {
        @OptIn(FlowPreview::class)
        appScope.launch {
            library.likeChanges.debounce(800).collect { profileId -> runCatching { refresh(profileId, force = true) } }
        }
    }

    fun shelves(profileId: Long): Flow<List<RecommendationShelf>> = dao.observe(profileId).map { rows ->
        if (rows.isEmpty()) return@map emptyList()
        val movieIds = rows.filter { it.itemKind == ItemKind.MOVIE }.map { it.itemId }.distinct()
        val seriesIds = rows.filter { it.itemKind == ItemKind.SERIES }.map { it.itemId }.distinct()
        val movieMap = movies.byIds(profileId, movieIds).associateBy { it.streamId }
        val seriesMap = series.byIds(profileId, seriesIds).associateBy { it.seriesId }
        rows.sortedBy { it.position }.groupBy { it.shelf }.map { (shelf, list) ->
            RecommendationShelf(
                id = shelf,
                title = list.first().shelfTitle,
                items = list.mapNotNull { row ->
                    when (row.itemKind) {
                        ItemKind.MOVIE -> movieMap[row.itemId]?.let { PosterItem(ContentKind.MOVIE, it.streamId, it.name, it.icon, it.backdrop, caption = row.reason) }
                        ItemKind.SERIES -> seriesMap[row.itemId]?.let { PosterItem(ContentKind.SERIES, it.seriesId, it.name, it.cover, it.backdrop, caption = row.reason) }
                        else -> null
                    }
                },
            )
        }.filter { it.items.isNotEmpty() }
    }

    /** Recomputes when the cache is older than 6h (or always with [force]). */
    suspend fun refresh(profileId: Long, force: Boolean = false) = mutex.withLock {
        withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            if (!settings.current().aiRecommendations) return@withContext
            if (!force) {
                val last = dao.lastComputedAt(profileId)
                if (last != null && now - last < TTL_MS) return@withContext
            }
            val index = indexFor(profileId, rebuild = true)
            val input = engineInput(profileId, now)
            val output = engine.recommend(index, input)
            val shelves = output.shelves.toMutableList()
            val recommended = shelves.indexOfFirst { it.id == RecommendationEngine.SHELF_RECOMMENDED }
            if (recommended >= 0 && settings.current().claudeEnabled && !output.coldStart) {
                val liked = input.signals.filter { it.weight > 0 }.mapNotNull { index.byKey[it.key] }
                reRanker.rerank(liked, widen(index, input, shelves[recommended].items))?.let { reranked ->
                    shelves[recommended] = shelves[recommended].copy(items = reranked)
                }
            }
            val rows = shelves.flatMapIndexed { shelfIndex, shelf ->
                shelf.items.mapIndexed { i, scored ->
                    RecommendationEntity(
                        profileId = profileId,
                        shelf = shelf.id,
                        shelfTitle = shelf.title,
                        itemKind = scored.item.kind.key,
                        itemId = scored.item.id,
                        score = scored.score,
                        reason = scored.reason,
                        position = shelfIndex * 1000 + i,
                        computedAt = now,
                    )
                }
            }
            dao.replace(profileId, rows)
        }
    }

    /** "More like this" for a detail page: nearest neighbours in the local index. */
    suspend fun moreLikeThis(profileId: Long, kind: ContentKind, id: Long, limit: Int = 20): List<PosterItem> = withContext(Dispatchers.Default) {
        val index = mutex.withLock { indexFor(profileId, rebuild = false) }
        val blocked = blockedKeys(index)
        val similar = engine.similar(index, RecItem.key(kind, id), blocked, limit)
        val movieMap = movies.byIds(profileId, similar.filter { it.item.kind == ContentKind.MOVIE }.map { it.item.id }).associateBy { it.streamId }
        val seriesMap = series.byIds(profileId, similar.filter { it.item.kind == ContentKind.SERIES }.map { it.item.id }).associateBy { it.seriesId }
        similar.mapNotNull { s ->
            when (s.item.kind) {
                ContentKind.MOVIE -> movieMap[s.item.id]?.let { PosterItem(ContentKind.MOVIE, it.streamId, it.name, it.icon, it.backdrop) }
                ContentKind.SERIES -> seriesMap[s.item.id]?.let { PosterItem(ContentKind.SERIES, it.seriesId, it.name, it.cover, it.backdrop) }
                else -> null
            }
        }
    }

    /** Re-ranker candidates: the local top-60. */
    private fun widen(index: CatalogIndex, input: EngineInput, top: List<ScoredItem>): List<ScoredItem> {
        val wide = RecommendationEngine(recommendedSize = 60).recommend(index, input).shelves.firstOrNull { it.id == RecommendationEngine.SHELF_RECOMMENDED }?.items
        return wide ?: top
    }

    private suspend fun engineInput(profileId: Long, now: Long): EngineInput {
        val signals = likes.all(profileId).mapNotNull { like ->
            val kind = when (like.kind) {
                ItemKind.MOVIE -> ContentKind.MOVIE
                ItemKind.SERIES -> ContentKind.SERIES
                else -> return@mapNotNull null
            }
            val weight = when (like.source) {
                LikeSource.EXPLICIT -> 1.0
                LikeSource.IMPLICIT -> 0.5
                LikeSource.IMPLICIT_NEGATIVE -> -0.5
                else -> 0.0
            }
            Signal(kind, like.itemId, weight, like.likedAt, like.source == LikeSource.EXPLICIT)
        }
        val watched = progress.all(profileId).mapNotNull { p ->
            when (p.kind) {
                ItemKind.MOVIE -> RecItem.key(ContentKind.MOVIE, p.itemId)
                ItemKind.EPISODE -> p.seriesId?.let { RecItem.key(ContentKind.SERIES, it) }
                else -> null
            }
        }.toSet()
        val opened = categoryOpens.all(profileId).map { "${it.kind}:${it.categoryId}" }.toSet()
        val index = cachedIndex?.second
        return EngineInput(signals, watched, opened, index?.let { blockedKeys(it) } ?: emptySet(), now)
    }

    private fun blockedKeys(index: CatalogIndex): Set<String> =
        if (!parental.locked()) emptySet() else index.items.filter { AdultContent.isAdult(it.categoryName) }.map { it.key }.toSet()

    private suspend fun indexFor(profileId: Long, rebuild: Boolean): CatalogIndex {
        val cacheKey = "$profileId"
        val cached = cachedIndex
        if (!rebuild && cached != null && cached.first == cacheKey) return cached.second
        val movieCats = movies.categoryMap(profileId)
        val seriesCats = series.categoryMap(profileId)
        val likedKeys = likes.all(profileId).map { "${it.kind}:${it.itemId}" }.toSet()
        val movieItems = movies.allEntities(profileId).map { it.toRecItem(movieCats) }
        val seriesItems = series.allEntities(profileId).map { it.toRecItem(seriesCats) }
        // Cap the candidate pool on huge catalogs (most recent first), always keeping liked items.
        val all = (movieItems + seriesItems)
        val capped = if (all.size <= MAX_ITEMS) all else {
            val keep = all.filter { it.key in likedKeys }
            (keep + all.sortedByDescending { it.addedAtSec ?: 0L }.take(MAX_ITEMS)).distinctBy { it.key }
        }
        val index = engine.buildIndex(capped)
        cachedIndex = cacheKey to index
        return index
    }

    private fun MovieEntity.toRecItem(categories: Map<String, String>) = RecItem(
        kind = ContentKind.MOVIE,
        id = streamId,
        title = name,
        genres = RecommendationEngine.splitList(genre),
        directors = RecommendationEngine.splitList(director),
        cast = RecommendationEngine.splitList(cast),
        year = year ?: TimeFormat.year(name),
        plot = plot,
        rating = rating,
        addedAtSec = added,
        categoryId = categoryId,
        categoryName = categoryId?.let { categories[it] },
    )

    private fun SeriesEntity.toRecItem(categories: Map<String, String>) = RecItem(
        kind = ContentKind.SERIES,
        id = seriesId,
        title = name,
        genres = RecommendationEngine.splitList(genre),
        directors = RecommendationEngine.splitList(director),
        cast = RecommendationEngine.splitList(cast),
        year = TimeFormat.year(releaseDate, name),
        plot = plot,
        rating = rating,
        addedAtSec = lastModified,
        categoryId = categoryId,
        categoryName = categoryId?.let { categories[it] },
    )

    /** For Home's "Recently added" fallbacks when AI is disabled. */
    suspend fun isEnabled(): Boolean = settings.settings.first().aiRecommendations

    companion object {
        const val TTL_MS = 6L * 3600 * 1000
        private const val MAX_ITEMS = 15_000
    }
}
