package com.awadytv.data.repository

import com.awadytv.core.database.CategoryOpenDao
import com.awadytv.core.database.CategoryOpenEntity
import com.awadytv.core.database.FavouriteDao
import com.awadytv.core.database.FavouriteEntity
import com.awadytv.core.database.LikeDao
import com.awadytv.core.database.LikeEntity
import com.awadytv.core.database.LikeSource
import com.awadytv.core.database.ProgressDao
import com.awadytv.core.database.ProgressEntity
import com.awadytv.domain.ContentKind
import com.awadytv.domain.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Favourites, likes (explicit + implicit), watch progress and category-open stats for one profile. */
@Singleton
class LibraryRepository @Inject constructor(
    private val favourites: FavouriteDao,
    private val likes: LikeDao,
    private val progress: ProgressDao,
    private val categoryOpens: CategoryOpenDao,
) {
    private val _likeChanges = MutableSharedFlow<Long>(extraBufferCapacity = 8)

    /** Emits the profile id whenever a like, dislike or favourite changes, so recommendations recompute immediately. */
    val likeChanges: SharedFlow<Long> = _likeChanges

    fun favouriteIds(profileId: Long, kind: ContentKind): Flow<List<Long>> =
        favourites.observe(profileId, kind.key).map { list -> list.map { it.itemId } }

    fun isFavourite(profileId: Long, kind: ContentKind, itemId: Long): Flow<Boolean> = favourites.observeIs(profileId, kind.key, itemId)

    suspend fun setFavourite(profileId: Long, kind: ContentKind, itemId: Long, favourite: Boolean) {
        if (favourite) {
            favourites.insert(FavouriteEntity(profileId, kind.key, itemId, System.currentTimeMillis()))
        } else {
            favourites.delete(profileId, kind.key, itemId)
        }
        // Favourite movies and series count as a like for recommendations.
        if (kind == ContentKind.MOVIE || kind == ContentKind.SERIES) _likeChanges.tryEmit(profileId)
    }

    fun isLiked(profileId: Long, kind: ContentKind, itemId: Long): Flow<Boolean> =
        likes.observe(profileId, kind.key, itemId).map { it?.source == LikeSource.EXPLICIT }

    fun isDisliked(profileId: Long, kind: ContentKind, itemId: Long): Flow<Boolean> =
        likes.observe(profileId, kind.key, itemId).map { it?.source == LikeSource.DISLIKE }

    fun observeLikes(profileId: Long): Flow<List<LikeEntity>> = likes.observeAll(profileId)

    /** Thumbs-down. Shares the row with a like, so disliking replaces a like and vice versa. */
    suspend fun setDisliked(profileId: Long, kind: ContentKind, itemId: Long, disliked: Boolean) {
        if (disliked) {
            likes.upsert(LikeEntity(profileId, kind.key, itemId, System.currentTimeMillis(), LikeSource.DISLIKE))
        } else if (likes.get(profileId, kind.key, itemId)?.source == LikeSource.DISLIKE) {
            likes.delete(profileId, kind.key, itemId)
        }
        _likeChanges.tryEmit(profileId)
    }

    suspend fun setLiked(profileId: Long, kind: ContentKind, itemId: Long, liked: Boolean) {
        if (liked) {
            likes.upsert(LikeEntity(profileId, kind.key, itemId, System.currentTimeMillis(), LikeSource.EXPLICIT))
        } else {
            likes.delete(profileId, kind.key, itemId)
        }
        _likeChanges.tryEmit(profileId)
    }

    enum class Verdict { LIKE, DISLIKE, SKIP }

    /**
     * A taste-trainer answer. With [notify] false the recommendations are not recomputed per card (that
     * takes seconds on a TV); call [notifyLikesChanged] once when the session ends.
     */
    suspend fun rate(profileId: Long, kind: ContentKind, itemId: Long, verdict: Verdict, notify: Boolean = false) {
        val source = when (verdict) {
            Verdict.LIKE -> LikeSource.EXPLICIT
            Verdict.DISLIKE -> LikeSource.DISLIKE
            Verdict.SKIP -> LikeSource.SKIP
        }
        likes.upsert(LikeEntity(profileId, kind.key, itemId, System.currentTimeMillis(), source))
        if (notify) _likeChanges.tryEmit(profileId)
    }

    fun notifyLikesChanged(profileId: Long) {
        _likeChanges.tryEmit(profileId)
    }

    fun continueWatching(profileId: Long, limit: Int = 20): Flow<List<WatchProgress>> =
        progress.observeContinueWatching(profileId, limit).map { list -> list.map { it.toDomain() } }

    fun observeProgress(profileId: Long, kind: ContentKind, itemId: Long): Flow<WatchProgress?> =
        progress.observe(profileId, kind.key, itemId).map { it?.toDomain() }

    fun observeSeriesProgress(profileId: Long, seriesId: Long): Flow<Map<Long, WatchProgress>> =
        progress.observeForSeries(profileId, seriesId).map { list -> list.associate { it.itemId to it.toDomain() } }

    suspend fun progressOf(profileId: Long, kind: ContentKind, itemId: Long): WatchProgress? = progress.get(profileId, kind.key, itemId)?.toDomain()

    suspend fun latestEpisodeProgress(profileId: Long, seriesId: Long): WatchProgress? = progress.latestForSeries(profileId, seriesId)?.toDomain()

    suspend fun saveProgress(
        profileId: Long,
        kind: ContentKind,
        itemId: Long,
        positionMs: Long,
        durationMs: Long,
        seriesId: Long? = null,
        title: String? = null,
        image: String? = null,
        containerExt: String? = null,
    ) {
        progress.upsert(ProgressEntity(profileId, kind.key, itemId, positionMs, durationMs, System.currentTimeMillis(), seriesId, title, image, containerExt))
    }

    /**
     * Implicit signal: finishing >= 90 % counts as a weak like. Stopping early is not treated as a dislike
     * (people stop for many reasons); dislikes are only the explicit thumbs-down. Never overrides an
     * explicit like or dislike. For episodes the signal is recorded on the parent series.
     */
    suspend fun recordImplicitSignal(profileId: Long, kind: ContentKind, itemId: Long, fraction: Float) {
        val targetKind = if (kind == ContentKind.EPISODE) ContentKind.SERIES else kind
        if (targetKind != ContentKind.MOVIE && targetKind != ContentKind.SERIES) return
        val existing = likes.get(profileId, targetKind.key, itemId)
        if (existing?.source == LikeSource.EXPLICIT || existing?.source == LikeSource.DISLIKE) return
        if (fraction < 0.9f) return
        likes.upsert(LikeEntity(profileId, targetKind.key, itemId, System.currentTimeMillis(), LikeSource.IMPLICIT))
        _likeChanges.tryEmit(profileId)
    }

    suspend fun recordCategoryOpen(profileId: Long, kind: ContentKind, categoryId: String) {
        val existing = categoryOpens.get(profileId, kind.key, categoryId)
        categoryOpens.upsert(CategoryOpenEntity(profileId, kind.key, categoryId, (existing?.count ?: 0) + 1, System.currentTimeMillis()))
    }
}

fun ProgressEntity.toDomain() = WatchProgress(
    kind = ContentKind.fromKey(kind),
    itemId = itemId,
    positionMs = positionMs,
    durationMs = durationMs,
    updatedAt = updatedAt,
    seriesId = seriesId,
    title = title,
    image = image,
)
