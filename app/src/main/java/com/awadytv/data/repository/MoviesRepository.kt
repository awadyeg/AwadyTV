package com.awadytv.data.repository

import com.awadytv.core.database.CategoryDao
import com.awadytv.core.database.CategoryKind
import com.awadytv.core.database.MovieDao
import com.awadytv.core.database.MovieEntity
import com.awadytv.core.network.XtreamUrls
import com.awadytv.core.util.TimeFormat
import com.awadytv.data.xtream.XtreamClient
import com.awadytv.data.xtream.toMovieInfo
import com.awadytv.domain.AdultContent
import com.awadytv.domain.Category
import com.awadytv.domain.Movie
import com.awadytv.domain.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoviesRepository @Inject constructor(
    private val categories: CategoryDao,
    private val movies: MovieDao,
    private val client: XtreamClient,
) {
    fun categories(profileId: Long): Flow<List<Category>> =
        categories.observe(profileId, CategoryKind.MOVIE).map { list -> list.map { Category(it.id, it.name, AdultContent.isAdult(it.name)) } }

    suspend fun categoryMap(profileId: Long): Map<String, String> = categories.all(profileId, CategoryKind.MOVIE).associate { it.id to it.name }

    fun movies(profileId: Long, categoryId: String): Flow<List<Movie>> =
        if (categoryId == RECENT) recent(profileId, 200) else movies.observeByCategory(profileId, categoryId).map { l -> l.map { it.toDomain() } }

    fun recent(profileId: Long, limit: Int): Flow<List<Movie>> = movies.observeRecent(profileId, limit).map { l -> l.map { it.toDomain() } }

    fun observe(profileId: Long, streamId: Long): Flow<Movie?> = movies.observe(profileId, streamId).map { it?.toDomain() }

    suspend fun get(profileId: Long, streamId: Long): Movie? = movies.get(profileId, streamId)?.toDomain()

    suspend fun byIds(profileId: Long, ids: List<Long>): List<Movie> = ids.chunked(500).flatMap { movies.byIds(profileId, it) }.map { it.toDomain() }

    suspend fun allEntities(profileId: Long): List<MovieEntity> = movies.all(profileId)

    suspend fun search(profileId: Long, query: String, limit: Int = 60): List<Movie> = movies.search(profileId, query, limit).map { it.toDomain() }

    /** Lazily enriches a movie with get_vod_info (plot, cast, genre...) the first time its detail page opens. */
    suspend fun ensureInfo(session: Session, streamId: Long, maxAgeMs: Long = 7L * 24 * 3600 * 1000): Boolean = withContext(Dispatchers.IO) {
        val entity = movies.get(session.profileId, streamId) ?: return@withContext false
        val loaded = entity.infoLoadedAt
        if (loaded != null && System.currentTimeMillis() - loaded < maxAgeMs) return@withContext true
        try {
            val info = client.vodInfo(session.credentials, streamId).toMovieInfo(entity.name)
            movies.updateInfo(
                session.profileId, streamId, info.genre, info.year ?: TimeFormat.year(entity.name), info.plot, info.cast, info.director,
                info.backdrop ?: info.poster, info.durationSecs, info.trailer, info.tmdbId, info.rating, System.currentTimeMillis(),
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }

    fun streamUrl(session: Session, movie: Movie): String = XtreamUrls.movie(session.credentials, movie.streamId, movie.containerExt)

    companion object {
        const val RECENT = "__recent__"
    }
}

fun MovieEntity.toDomain() = Movie(
    streamId = streamId,
    name = name,
    icon = icon,
    rating = rating,
    added = added,
    categoryId = categoryId,
    containerExt = containerExt,
    genre = genre,
    year = year ?: TimeFormat.year(name),
    plot = plot,
    cast = cast,
    director = director,
    backdrop = backdrop,
    durationSecs = durationSecs,
    trailer = trailer,
    infoLoaded = infoLoadedAt != null,
)
