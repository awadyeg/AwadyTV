package com.awadytv.data.recommendations

import com.awadytv.data.repository.MoviesRepository
import com.awadytv.data.repository.SeriesRepository
import com.awadytv.domain.ContentKind
import com.awadytv.domain.PosterItem
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Recommendation entry points used by the UI. */
@Singleton
class RecommendationRepository @Inject constructor(
    private val movies: MoviesRepository,
    private val series: SeriesRepository,
) {
    /** "More like this": same category, best rated first. */
    suspend fun moreLikeThis(profileId: Long, kind: ContentKind, id: Long, limit: Int = 20): List<PosterItem> = when (kind) {
        ContentKind.MOVIE -> {
            val movie = movies.get(profileId, id)
            val cat = movie?.categoryId ?: return emptyList()
            movies.movies(profileId, cat).first().filter { it.streamId != id }.sortedByDescending { it.rating ?: 0.0 }.take(limit)
                .map { PosterItem(ContentKind.MOVIE, it.streamId, it.name, it.icon, it.backdrop) }
        }
        ContentKind.SERIES -> {
            val s = series.get(profileId, id)
            val cat = s?.categoryId ?: return emptyList()
            series.series(profileId, cat).first().filter { it.seriesId != id }.sortedByDescending { it.rating ?: 0.0 }.take(limit)
                .map { PosterItem(ContentKind.SERIES, it.seriesId, it.name, it.cover, it.backdrop) }
        }
        else -> emptyList()
    }
}
