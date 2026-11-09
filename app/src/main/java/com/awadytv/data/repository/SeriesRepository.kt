package com.awadytv.data.repository

import com.awadytv.core.database.CategoryDao
import com.awadytv.core.database.CategoryKind
import com.awadytv.core.database.EpisodeDao
import com.awadytv.core.database.EpisodeEntity
import com.awadytv.core.database.SeriesDao
import com.awadytv.core.database.SeriesEntity
import com.awadytv.core.network.XtreamUrls
import com.awadytv.core.util.AppResult
import com.awadytv.core.util.TimeFormat
import com.awadytv.core.network.ErrorMapping
import com.awadytv.data.xtream.XtreamClient
import com.awadytv.data.xtream.toEntity
import com.awadytv.data.xtream.toEpisodeEntities
import com.awadytv.domain.AdultContent
import com.awadytv.domain.Category
import com.awadytv.domain.Episode
import com.awadytv.domain.Series
import com.awadytv.domain.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesRepository @Inject constructor(
    private val categories: CategoryDao,
    private val series: SeriesDao,
    private val episodes: EpisodeDao,
    private val client: XtreamClient,
) {
    private val episodesFetchedAt = ConcurrentHashMap<String, Long>()

    fun categories(profileId: Long): Flow<List<Category>> =
        categories.observe(profileId, CategoryKind.SERIES).map { list -> list.map { Category(it.id, it.name, AdultContent.isAdult(it.name)) } }

    suspend fun categoryMap(profileId: Long): Map<String, String> = categories.all(profileId, CategoryKind.SERIES).associate { it.id to it.name }

    fun series(profileId: Long, categoryId: String): Flow<List<Series>> =
        if (categoryId == RECENT) recent(profileId, 200) else series.observeByCategory(profileId, categoryId).map { l -> l.map { it.toDomain() } }

    fun recent(profileId: Long, limit: Int): Flow<List<Series>> = series.observeRecent(profileId, limit).map { l -> l.map { it.toDomain() } }

    fun observe(profileId: Long, seriesId: Long): Flow<Series?> = series.observe(profileId, seriesId).map { it?.toDomain() }

    suspend fun get(profileId: Long, seriesId: Long): Series? = series.get(profileId, seriesId)?.toDomain()

    suspend fun byIds(profileId: Long, ids: List<Long>): List<Series> = ids.chunked(500).flatMap { series.byIds(profileId, it) }.map { it.toDomain() }

    suspend fun allEntities(profileId: Long): List<SeriesEntity> = series.all(profileId)

    suspend fun search(profileId: Long, query: String, limit: Int = 60): List<Series> = series.search(profileId, query, limit).map { it.toDomain() }

    fun episodes(profileId: Long, seriesId: Long): Flow<List<Episode>> = episodes.observeBySeries(profileId, seriesId).map { l -> l.map { it.toDomain() } }

    suspend fun episode(profileId: Long, episodeId: Long): Episode? = episodes.get(profileId, episodeId)?.toDomain()

    /** Loads seasons/episodes via get_series_info (cached in Room; refreshed at most every 6 hours). */
    suspend fun ensureEpisodes(session: Session, seriesId: Long, force: Boolean = false): AppResult<Unit> = withContext(Dispatchers.IO) {
        val key = "${session.profileId}:$seriesId"
        val cached = episodes.bySeries(session.profileId, seriesId)
        val fresh = (episodesFetchedAt[key] ?: 0L) > System.currentTimeMillis() - 6 * 3600 * 1000L
        if (!force && cached.isNotEmpty() && fresh) return@withContext AppResult.Success(Unit)
        try {
            val info = client.seriesInfo(session.credentials, seriesId)
            val rows = info.toEpisodeEntities(session.profileId, seriesId)
            episodes.replaceSeries(session.profileId, seriesId, rows)
            // get_series_info often carries richer metadata than get_series.
            val existing = series.get(session.profileId, seriesId)
            info.info?.toEntity(session.profileId, existing?.sortOrder ?: 0, fallbackId = seriesId)?.let { fromInfo ->
                if (existing != null) {
                    series.upsert(
                        existing.copy(
                            plot = existing.plot ?: fromInfo.plot,
                            cast = existing.cast ?: fromInfo.cast,
                            director = existing.director ?: fromInfo.director,
                            genre = existing.genre ?: fromInfo.genre,
                            backdrop = existing.backdrop ?: fromInfo.backdrop,
                            releaseDate = existing.releaseDate ?: fromInfo.releaseDate,
                            trailer = existing.trailer ?: fromInfo.trailer,
                        ),
                    )
                }
            }
            episodesFetchedAt[key] = System.currentTimeMillis()
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (cached.isNotEmpty()) AppResult.Success(Unit) else AppResult.Failure(ErrorMapping.map(e))
        }
    }

    /** Next episode in (season, number) order, crossing into the next season. */
    suspend fun nextEpisode(profileId: Long, current: Episode): Episode? {
        val all = episodes.bySeries(profileId, current.seriesId).map { it.toDomain() }
        val ordered = all.sortedWith(compareBy({ it.season }, { it.num }, { it.episodeId }))
        val index = ordered.indexOfFirst { it.episodeId == current.episodeId }
        return if (index >= 0) ordered.getOrNull(index + 1) else null
    }

    suspend fun firstEpisode(profileId: Long, seriesId: Long): Episode? =
        episodes.bySeries(profileId, seriesId).map { it.toDomain() }.minWithOrNull(compareBy({ it.season }, { it.num }))

    fun streamUrl(session: Session, episode: Episode): String = XtreamUrls.episode(session.credentials, episode.episodeId, episode.containerExt)

    companion object {
        const val RECENT = "__recent__"
    }
}

fun SeriesEntity.toDomain() = Series(
    seriesId = seriesId,
    name = name,
    cover = cover,
    plot = plot,
    cast = cast,
    director = director,
    genre = genre,
    releaseDate = releaseDate,
    year = TimeFormat.year(releaseDate, name),
    rating = rating,
    backdrop = backdrop,
    lastModified = lastModified,
    categoryId = categoryId,
    trailer = trailer,
    episodeRunTime = episodeRunTime,
)

fun EpisodeEntity.toDomain() = Episode(episodeId, seriesId, season, num, title, containerExt, image, plot, durationSecs, releaseDate)
