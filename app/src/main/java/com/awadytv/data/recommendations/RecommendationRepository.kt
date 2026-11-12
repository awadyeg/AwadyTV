package com.awadytv.data.recommendations

import com.awadytv.core.database.CategoryOpenDao
import com.awadytv.core.database.FavouriteDao
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

/** One card of the taste trainer. [why] says why it is shown ("Picked for you" or the category explored). */
data class TasteCard(
    val kind: ContentKind,
    val id: Long,
    val title: String,
    val image: String?,
    val backdrop: String?,
    val year: Int?,
    val rating: Double?,
    val genre: String?,
    val plot: String?,
    val why: String,
)

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
    private val favourites: FavouriteDao,
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
            // A like does not change the catalog, so reuse the index (rebuilding it takes tens of seconds on a TV).
            library.likeChanges.debounce(800).collect { profileId -> runCatching { refresh(profileId, force = true, rebuildIndex = false) } }
        }
    }

    fun shelves(profileId: Long): Flow<List<RecommendationShelf>> = dao.observe(profileId).map { all ->
        val rows = all.filterNot { it.shelf.startsWith(FOR_YOU_PREFIX) }
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
    suspend fun refresh(profileId: Long, force: Boolean = false, rebuildIndex: Boolean = true) = mutex.withLock {
        withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            if (!settings.current().aiRecommendations) return@withContext
            if (!force) {
                val last = dao.lastComputedAt(profileId)
                if (last != null && now - last < TTL_MS && dao.forYouCount(profileId) > 0) return@withContext
            }
            val index = indexFor(profileId, rebuild = rebuildIndex)
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
            dao.replace(profileId, rows + forYouRows(profileId, index, input, now))
        }
    }

    /**
     * The "For you" category of Movies and Series: each catalog ranked by the engine, best first. Computed in
     * this background pass over the same index and stored with the shelves, so the category opens instantly
     * (ranking a large catalog from scratch on a TV CPU takes tens of seconds).
     */
    private fun forYouRows(profileId: Long, index: CatalogIndex, input: EngineInput, now: Long): List<RecommendationEntity> {
        val ranked = RecommendationEngine(recommendedSize = FOR_YOU_SIZE * 4).recommend(index, input)
            .shelves.firstOrNull { it.id == RecommendationEngine.SHELF_RECOMMENDED }?.items.orEmpty()
        return listOf(ContentKind.MOVIE, ContentKind.SERIES).flatMap { kind ->
            ranked.filter { it.item.kind == kind }.take(FOR_YOU_SIZE).mapIndexed { i, scored ->
                RecommendationEntity(
                    profileId = profileId,
                    shelf = FOR_YOU_PREFIX + kind.key,
                    shelfTitle = "For you",
                    itemKind = kind.key,
                    itemId = scored.item.id,
                    score = scored.score,
                    reason = scored.reason,
                    position = i,
                    computedAt = now,
                )
            }
        }
    }

    /** The stored "For you" ranking of one kind; empty until the first background pass has run (it is started here). */
    fun forYou(profileId: Long, kind: ContentKind): Flow<List<PosterItem>> = dao.observe(profileId).map { all ->
        val rows = all.filter { it.shelf == FOR_YOU_PREFIX + kind.key }.sortedBy { it.position }
        if (rows.isEmpty()) {
            appScope.launch { runCatching { refresh(profileId) } }
            return@map emptyList()
        }
        when (kind) {
            ContentKind.MOVIE -> {
                val map = movies.byIds(profileId, rows.map { it.itemId }).associateBy { it.streamId }
                rows.mapNotNull { r -> map[r.itemId]?.let { PosterItem(ContentKind.MOVIE, it.streamId, it.name, it.icon, it.backdrop, ratingLabel(it.rating), caption = r.reason) } }
            }
            ContentKind.SERIES -> {
                val map = series.byIds(profileId, rows.map { it.itemId }).associateBy { it.seriesId }
                rows.mapNotNull { r -> map[r.itemId]?.let { PosterItem(ContentKind.SERIES, it.seriesId, it.name, it.cover, it.backdrop, ratingLabel(it.rating), caption = r.reason) } }
            }
            else -> emptyList()
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

    private fun ratingLabel(rating: Double?): String? = rating?.takeIf { it > 0 }?.let { "★ " + String.format(java.util.Locale.US, "%.1f", it) }

    /**
     * Cards for the taste trainer: titles not yet rated (skips come back after [SKIP_DAYS]), about a third
     * from the current "For you" ranking (to confirm or correct it) and the rest explored across categories,
     * round-robin, preferring well rated titles, so every session shows different corners of the catalog.
     */
    suspend fun tasteCards(profileId: Long, count: Int = 30, exclude: Set<String> = emptySet()): List<TasteCard> = withContext(Dispatchers.Default) {
        // Needs only titles, categories and ratings, not the feature index: never wait for the (slow,
        // mutex-held) index build of a running recommendation pass. Reuse the index's item list if it exists.
        val items = cachedIndex?.takeIf { it.first == "$profileId" }?.second?.items ?: run {
            val movieCats = movies.categoryMap(profileId)
            val seriesCats = series.categoryMap(profileId)
            movies.allEntities(profileId).map { it.toRecItem(movieCats) } + series.allEntities(profileId).map { it.toRecItem(seriesCats) }
        }
        val now = System.currentTimeMillis()
        val answered = likes.all(profileId)
            .filter { it.source != LikeSource.SKIP || now - it.likedAt < SKIP_DAYS * 86_400_000L }
            .map { "${it.kind}:${it.itemId}" }.toSet()
        val blocked = if (!parental.locked()) emptySet() else items.filter { AdultContent.isAdult(it.categoryName) }.map { it.key }.toSet()
        val skip = answered + exclude + blocked
        val random = kotlin.random.Random(now)

        val forYouRows = dao.observe(profileId).first().filter { it.shelf.startsWith(FOR_YOU_PREFIX) }.sortedBy { it.position }
        val picked = forYouRows.map { "${it.itemKind}:${it.itemId}" }.filter { it !in skip }.take(60).shuffled(random).take(count / 3)
        val chosen = LinkedHashMap<String, String>() // key -> why
        picked.forEach { chosen[it] = "Picked for you" }

        val byCategory = items.filter { it.key !in skip && it.key !in chosen && it.categoryId != null }
            .groupBy { "${it.kind.key}:${it.categoryId}" }
            .mapValues { (_, list) -> list.sortedByDescending { it.rating ?: 0.0 }.take(40).shuffled(random).toMutableList() }
            .toMutableMap()
        val categoryOrder = byCategory.keys.shuffled(random).toMutableList()
        while (chosen.size < count && categoryOrder.isNotEmpty()) {
            val iterator = categoryOrder.iterator()
            while (iterator.hasNext() && chosen.size < count) {
                val bucket = byCategory.getValue(iterator.next())
                val item = bucket.removeFirstOrNull()
                if (item == null) { iterator.remove(); continue }
                chosen[item.key] = item.categoryName?.let { "Exploring $it" } ?: "Exploring"
            }
        }

        val keys = chosen.keys.shuffled(random)
        val movieMap = movies.byIds(profileId, keys.filter { it.startsWith(ContentKind.MOVIE.key + ":") }.map { it.substringAfter(':').toLong() }).associateBy { it.streamId }
        val seriesMap = series.byIds(profileId, keys.filter { it.startsWith(ContentKind.SERIES.key + ":") }.map { it.substringAfter(':').toLong() }).associateBy { it.seriesId }
        keys.mapNotNull { key ->
            val id = key.substringAfter(':').toLong()
            val why = chosen.getValue(key)
            if (key.startsWith(ContentKind.MOVIE.key + ":")) {
                movieMap[id]?.takeIf { !it.icon.isNullOrBlank() }?.let { TasteCard(ContentKind.MOVIE, it.streamId, it.name, it.icon, it.backdrop, it.year, it.rating, it.genre, it.plot, why) }
            } else {
                seriesMap[id]?.takeIf { !it.cover.isNullOrBlank() }?.let { TasteCard(ContentKind.SERIES, it.seriesId, it.name, it.cover, it.backdrop, it.year, it.rating, it.genre, it.plot, why) }
            }
        }
    }

    /** Re-ranker candidates: the local top-60. */
    private fun widen(index: CatalogIndex, input: EngineInput, top: List<ScoredItem>): List<ScoredItem> {
        val wide = RecommendationEngine(recommendedSize = 60).recommend(index, input).shelves.firstOrNull { it.id == RecommendationEngine.SHELF_RECOMMENDED }?.items
        return wide ?: top
    }

    private suspend fun engineInput(profileId: Long, now: Long): EngineInput {
        fun kindOf(key: String) = when (key) {
            ItemKind.MOVIE -> ContentKind.MOVIE
            ItemKind.SERIES -> ContentKind.SERIES
            else -> null
        }
        val likeSignals = likes.all(profileId).mapNotNull { like ->
            val kind = kindOf(like.kind) ?: return@mapNotNull null
            val weight = when (like.source) {
                LikeSource.EXPLICIT -> 1.0
                LikeSource.IMPLICIT -> 0.5
                LikeSource.DISLIKE -> -1.0
                else -> return@mapNotNull null // includes legacy implicit_negative: no longer a dislike
            }
            Signal(kind, like.itemId, weight, like.likedAt, like.source == LikeSource.EXPLICIT || like.source == LikeSource.DISLIKE)
        }
        // A favourite counts as an explicit like, unless the user disliked it or already liked it explicitly.
        val byKey = likeSignals.associateBy { it.key }
        val favouriteSignals = favourites.all(profileId).mapNotNull { fav ->
            val kind = kindOf(fav.kind) ?: return@mapNotNull null
            val signal = Signal(kind, fav.itemId, 1.0, fav.addedAt, explicit = true)
            val existing = byKey[signal.key]
            if (existing != null && (existing.weight < 0 || existing.explicit)) null else signal
        }
        val favKeys = favouriteSignals.map { it.key }.toSet()
        val signals = likeSignals.filter { it.key !in favKeys } + favouriteSignals
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
        const val FOR_YOU_PREFIX = "foryou:"
        private const val SKIP_DAYS = 30L
        private const val FOR_YOU_SIZE = 200
        private const val MAX_ITEMS = 15_000
    }
}
