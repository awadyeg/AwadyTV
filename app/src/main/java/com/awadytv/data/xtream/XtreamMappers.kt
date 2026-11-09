package com.awadytv.data.xtream

import com.awadytv.core.database.CategoryEntity
import com.awadytv.core.database.ChannelEntity
import com.awadytv.core.database.EpgEntity
import com.awadytv.core.database.EpisodeEntity
import com.awadytv.core.database.MovieBase
import com.awadytv.core.database.MovieEntity
import com.awadytv.core.database.SeriesEntity
import com.awadytv.core.util.Base64Text
import com.awadytv.core.util.TimeFormat

fun List<CategoryDto>.toEntities(profileId: Long, kind: String): List<CategoryEntity> =
    mapIndexedNotNull { index, dto ->
        val id = dto.categoryId ?: return@mapIndexedNotNull null
        CategoryEntity(profileId, kind, id, dto.categoryName ?: "Category $id", dto.parentId, index)
    }.distinctBy { it.id }

fun List<LiveStreamDto>.toChannelEntities(profileId: Long): List<ChannelEntity> =
    mapIndexedNotNull { index, dto ->
        val id = dto.streamId ?: return@mapIndexedNotNull null
        ChannelEntity(
            profileId = profileId,
            streamId = id,
            num = dto.num ?: (index + 1),
            name = dto.name?.trim().orEmpty().ifEmpty { "Channel $id" },
            icon = dto.streamIcon,
            epgChannelId = dto.epgChannelId,
            categoryId = dto.categoryId,
            tvArchive = (dto.tvArchive ?: 0) > 0,
            tvArchiveDuration = dto.tvArchiveDuration,
            added = dto.added,
            sortOrder = index,
        )
    }.distinctBy { it.streamId }

fun List<VodStreamDto>.toMovieBases(profileId: Long, stamp: Long): List<MovieBase> =
    mapIndexedNotNull { index, dto ->
        val id = dto.streamId ?: return@mapIndexedNotNull null
        MovieBase(
            profileId = profileId,
            streamId = id,
            name = dto.name?.trim().orEmpty().ifEmpty { "Movie $id" },
            icon = dto.streamIcon,
            rating = dto.rating ?: dto.rating5?.times(2),
            added = dto.added,
            categoryId = dto.categoryId,
            containerExt = dto.containerExtension,
            sortOrder = index,
            syncStamp = stamp,
        )
    }.distinctBy { it.streamId }

fun List<SeriesDto>.toSeriesEntities(profileId: Long): List<SeriesEntity> =
    mapIndexedNotNull { index, dto -> dto.toEntity(profileId, index) }.distinctBy { it.seriesId }

fun SeriesDto.toEntity(profileId: Long, sortOrder: Int, fallbackId: Long? = null): SeriesEntity? {
    val id = seriesId ?: fallbackId ?: return null
    return SeriesEntity(
        profileId = profileId,
        seriesId = id,
        name = name?.trim().orEmpty().ifEmpty { "Series $id" },
        cover = cover,
        plot = plot,
        cast = cast,
        director = director,
        genre = genre,
        releaseDate = releaseDate ?: releaseDateAlt,
        rating = rating ?: rating5?.times(2),
        backdrop = backdropPath.firstOrNull(),
        lastModified = lastModified,
        categoryId = categoryId,
        trailer = youtubeTrailer,
        episodeRunTime = episodeRunTime,
        sortOrder = sortOrder,
    )
}

fun SeriesInfoResponseDto.toEpisodeEntities(profileId: Long, seriesId: Long): List<EpisodeEntity> =
    episodes.flatMap { (season, list) ->
        list.mapNotNull { dto ->
            val id = dto.id ?: return@mapNotNull null
            EpisodeEntity(
                profileId = profileId,
                seriesId = seriesId,
                season = dto.season ?: season,
                episodeId = id,
                num = dto.episodeNum ?: 0,
                title = dto.title?.trim().orEmpty().ifEmpty { "Episode ${dto.episodeNum ?: ""}".trim() },
                containerExt = dto.containerExtension,
                image = dto.info?.movieImage,
                plot = dto.info?.plot,
                durationSecs = dto.info?.durationSecs ?: TimeFormat.durationSeconds(dto.info?.duration),
                releaseDate = dto.info?.releasedate,
                rating = dto.info?.rating,
            )
        }
    }

/** Movie enrichment fields extracted from get_vod_info. */
data class MovieInfo(
    val genre: String?,
    val year: Int?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val backdrop: String?,
    val durationSecs: Long?,
    val trailer: String?,
    val tmdbId: String?,
    val rating: Double?,
    val poster: String?,
)

fun VodInfoResponseDto.toMovieInfo(fallbackName: String?): MovieInfo {
    val i = info
    return MovieInfo(
        genre = i?.genre,
        year = TimeFormat.year(i?.releasedate, i?.releaseDate, fallbackName),
        plot = i?.plot ?: i?.description,
        cast = i?.cast ?: i?.actors,
        director = i?.director,
        backdrop = i?.backdropPath?.firstOrNull(),
        durationSecs = i?.durationSecs ?: TimeFormat.durationSeconds(i?.duration),
        trailer = i?.youtubeTrailer,
        tmdbId = i?.tmdbId,
        rating = i?.rating,
        poster = i?.movieImage ?: i?.coverBig,
    )
}

fun MovieEntity.withInfo(info: MovieInfo, now: Long) = copy(
    genre = info.genre, year = info.year ?: year, plot = info.plot, cast = info.cast, director = info.director,
    backdrop = info.backdrop, durationSecs = info.durationSecs, trailer = info.trailer, tmdbId = info.tmdbId,
    rating = info.rating ?: rating, infoLoadedAt = now,
)

/** Short-EPG listings -> rows; base64 title/description decoded; timestamps preferred over date strings. */
fun ShortEpgResponseDto.toEpgEntities(profileId: Long, epgChannelId: String): List<EpgEntity> =
    listings.mapNotNull { l ->
        val start = l.startTimestamp?.times(1000) ?: TimeFormat.parseXtreamDateTime(l.start) ?: return@mapNotNull null
        val end = l.stopTimestamp?.times(1000) ?: TimeFormat.parseXtreamDateTime(l.end) ?: return@mapNotNull null
        EpgEntity(
            profileId = profileId,
            epgChannelId = epgChannelId,
            start = start,
            end = end,
            title = Base64Text.decode(l.title).ifEmpty { "No information" },
            description = Base64Text.decode(l.description),
        )
    }
