package com.awadytv.data.repository

import android.content.Context
import com.awadytv.core.database.CategoryDao
import com.awadytv.core.database.CategoryKind
import com.awadytv.core.database.ChannelDao
import com.awadytv.core.database.MovieDao
import com.awadytv.core.database.SeriesDao
import com.awadytv.core.network.ErrorMapping
import com.awadytv.core.util.AppError
import com.awadytv.data.xtream.CategoryDto
import com.awadytv.data.xtream.XtreamClient
import com.awadytv.data.xtream.toChannelEntities
import com.awadytv.data.xtream.toEntities
import com.awadytv.data.xtream.toMovieBases
import com.awadytv.data.xtream.toSeriesEntities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InterruptedIOException
import javax.inject.Inject
import javax.inject.Singleton

data class SyncProgress(
    val step: Int,
    val total: Int,
    val label: String,
    val done: Boolean = false,
    val error: AppError? = null,
    val counts: Map<String, Int> = emptyMap(),
)

/**
 * Full catalog sync (categories + streams for live, movies, series) into Room. Screens only ever read
 * Room; this runs at sign-in and in the background when the cache is stale (stale-while-revalidate).
 */
@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
    private val client: XtreamClient,
    private val categories: CategoryDao,
    private val channels: ChannelDao,
    private val movies: MovieDao,
    private val series: SeriesDao,
) {
    private val mutex = Mutex()

    fun sync(profileId: Long): Flow<SyncProgress> = flow {
        val session = profiles.sessionFor(profileId)
        if (session == null) {
            emit(SyncProgress(0, TOTAL, "Not signed in", error = AppError.NoProfile))
            return@flow
        }
        val c = session.credentials
        val counts = linkedMapOf<String, Int>()
        try {
            mutex.withLock {
                emit(SyncProgress(1, TOTAL, "Live TV categories"))
                val liveCats = client.liveCategories(c)
                categories.replace(profileId, CategoryKind.LIVE, liveCats.toEntities(profileId, CategoryKind.LIVE))

                emit(SyncProgress(2, TOTAL, "Live channels"))
                val live = fetchWithFallback(liveCats, { client.liveStreams(c) }) { client.liveStreams(c, it) }
                val channelEntities = live.toChannelEntities(profileId)
                channels.replace(profileId, channelEntities)
                counts["Channels"] = channelEntities.size

                emit(SyncProgress(3, TOTAL, "Movie categories", counts = counts.toMap()))
                val vodCats = client.vodCategories(c)
                categories.replace(profileId, CategoryKind.MOVIE, vodCats.toEntities(profileId, CategoryKind.MOVIE))

                emit(SyncProgress(4, TOTAL, "Movies", counts = counts.toMap()))
                val vod = fetchWithFallback(vodCats, { client.vodStreams(c) }) { client.vodStreams(c, it) }
                val stamp = System.currentTimeMillis()
                val movieBases = vod.toMovieBases(profileId, stamp)
                movies.sync(profileId, stamp, movieBases)
                counts["Movies"] = movieBases.size

                emit(SyncProgress(5, TOTAL, "Series categories", counts = counts.toMap()))
                val seriesCats = client.seriesCategories(c)
                categories.replace(profileId, CategoryKind.SERIES, seriesCats.toEntities(profileId, CategoryKind.SERIES))

                emit(SyncProgress(6, TOTAL, "Series", counts = counts.toMap()))
                val seriesList = fetchWithFallback(seriesCats, { client.series(c) }) { client.series(c, it) }
                val seriesEntities = seriesList.toSeriesEntities(profileId)
                series.replace(profileId, seriesEntities)
                counts["Series"] = seriesEntities.size

                profiles.markSynced(profileId)
            }
            emit(SyncProgress(TOTAL, TOTAL, "Done", done = true, counts = counts.toMap()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emit(SyncProgress(0, TOTAL, "Sync failed", error = ErrorMapping.map(e, context)))
        }
    }.flowOn(Dispatchers.IO)

    /** Silent background refresh when the cache is older than [maxAgeMs]. Returns true when a sync ran OK. */
    suspend fun refreshIfStale(maxAgeMs: Long = STALE_AFTER_MS): Boolean {
        val session = profiles.currentSession() ?: return false
        val last = session.profile.lastSyncAt ?: 0L
        if (System.currentTimeMillis() - last < maxAgeMs) return false
        var ok = false
        sync(session.profileId).collect { if (it.done) ok = true }
        return ok
    }

    /**
     * Big panels sometimes time out on the "all streams" call; fall back to one request per category.
     */
    private suspend fun <T> fetchWithFallback(
        cats: List<CategoryDto>,
        all: suspend () -> List<T>,
        perCategory: suspend (String) -> List<T>,
    ): List<T> = try {
        all()
    } catch (e: InterruptedIOException) {
        if (cats.isEmpty()) throw e
        cats.mapNotNull { it.categoryId }.flatMap { id -> runCatching { perCategory(id) }.getOrDefault(emptyList()) }
    }

    companion object {
        const val TOTAL = 6
        const val STALE_AFTER_MS = 12L * 60 * 60 * 1000
    }
}
