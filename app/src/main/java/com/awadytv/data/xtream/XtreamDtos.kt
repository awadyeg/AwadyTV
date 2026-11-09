package com.awadytv.data.xtream

import com.awadytv.core.network.LenientDoubleSerializer
import com.awadytv.core.network.LenientIntSerializer
import com.awadytv.core.network.LenientListSerializer
import com.awadytv.core.network.LenientLongSerializer
import com.awadytv.core.network.LenientStringListSerializer
import com.awadytv.core.network.LenientStringSerializer
import com.awadytv.core.network.ObjectOrNullSerializer
import com.awadytv.core.network.XtreamJson
import com.awadytv.core.network.decodeLenientList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

typealias LStr = @Serializable(with = LenientStringSerializer::class) String?
typealias LInt = @Serializable(with = LenientIntSerializer::class) Int?
typealias LLong = @Serializable(with = LenientLongSerializer::class) Long?
typealias LDouble = @Serializable(with = LenientDoubleSerializer::class) Double?
typealias LStrList = @Serializable(with = LenientStringListSerializer::class) List<String>

@Serializable
data class AuthResponseDto(
    @SerialName("user_info") @Serializable(with = UserInfoOrNull::class) val userInfo: UserInfoDto? = null,
    @SerialName("server_info") @Serializable(with = ServerInfoOrNull::class) val serverInfo: ServerInfoDto? = null,
)

@Serializable
data class UserInfoDto(
    val username: LStr = null,
    val message: LStr = null,
    val auth: LInt = null,
    val status: LStr = null,
    @SerialName("exp_date") val expDate: LLong = null,
    @SerialName("is_trial") val isTrial: LInt = null,
    @SerialName("active_cons") val activeConnections: LInt = null,
    @SerialName("created_at") val createdAt: LLong = null,
    @SerialName("max_connections") val maxConnections: LInt = null,
    @SerialName("allowed_output_formats") val allowedOutputFormats: LStrList = emptyList(),
)

@Serializable
data class ServerInfoDto(
    val url: LStr = null,
    val port: LStr = null,
    @SerialName("https_port") val httpsPort: LStr = null,
    @SerialName("server_protocol") val serverProtocol: LStr = null,
    val timezone: LStr = null,
    @SerialName("timestamp_now") val timestampNow: LLong = null,
    @SerialName("time_now") val timeNow: LStr = null,
)

@Serializable
data class CategoryDto(
    @SerialName("category_id") val categoryId: LStr = null,
    @SerialName("category_name") val categoryName: LStr = null,
    @SerialName("parent_id") val parentId: LInt = null,
)

@Serializable
data class LiveStreamDto(
    val num: LInt = null,
    val name: LStr = null,
    @SerialName("stream_type") val streamType: LStr = null,
    @SerialName("stream_id") val streamId: LLong = null,
    @SerialName("stream_icon") val streamIcon: LStr = null,
    @SerialName("epg_channel_id") val epgChannelId: LStr = null,
    val added: LLong = null,
    @SerialName("category_id") val categoryId: LStr = null,
    @SerialName("custom_sid") val customSid: LStr = null,
    @SerialName("tv_archive") val tvArchive: LInt = null,
    @SerialName("direct_source") val directSource: LStr = null,
    @SerialName("tv_archive_duration") val tvArchiveDuration: LInt = null,
)

@Serializable
data class VodStreamDto(
    val num: LInt = null,
    val name: LStr = null,
    @SerialName("stream_id") val streamId: LLong = null,
    @SerialName("stream_icon") val streamIcon: LStr = null,
    val rating: LDouble = null,
    @SerialName("rating_5based") val rating5: LDouble = null,
    val added: LLong = null,
    @SerialName("category_id") val categoryId: LStr = null,
    @SerialName("container_extension") val containerExtension: LStr = null,
    @SerialName("custom_sid") val customSid: LStr = null,
    @SerialName("direct_source") val directSource: LStr = null,
)

@Serializable
data class VodInfoResponseDto(
    @Serializable(with = VodInfoOrNull::class) val info: VodInfoDto? = null,
    @SerialName("movie_data") @Serializable(with = MovieDataOrNull::class) val movieData: MovieDataDto? = null,
)

@Serializable
data class VodInfoDto(
    val name: LStr = null,
    @SerialName("movie_image") val movieImage: LStr = null,
    @SerialName("cover_big") val coverBig: LStr = null,
    @SerialName("backdrop_path") val backdropPath: LStrList = emptyList(),
    val plot: LStr = null,
    val description: LStr = null,
    val cast: LStr = null,
    val actors: LStr = null,
    val director: LStr = null,
    val genre: LStr = null,
    val releasedate: LStr = null,
    @SerialName("release_date") val releaseDate: LStr = null,
    val rating: LDouble = null,
    val duration: LStr = null,
    @SerialName("duration_secs") val durationSecs: LLong = null,
    @SerialName("youtube_trailer") val youtubeTrailer: LStr = null,
    @SerialName("tmdb_id") val tmdbId: LStr = null,
)

@Serializable
data class MovieDataDto(
    @SerialName("stream_id") val streamId: LLong = null,
    val name: LStr = null,
    val added: LLong = null,
    @SerialName("category_id") val categoryId: LStr = null,
    @SerialName("container_extension") val containerExtension: LStr = null,
)

@Serializable
data class SeriesDto(
    val num: LInt = null,
    val name: LStr = null,
    @SerialName("series_id") val seriesId: LLong = null,
    val cover: LStr = null,
    val plot: LStr = null,
    val cast: LStr = null,
    val director: LStr = null,
    val genre: LStr = null,
    val releaseDate: LStr = null,
    @SerialName("release_date") val releaseDateAlt: LStr = null,
    @SerialName("last_modified") val lastModified: LLong = null,
    val rating: LDouble = null,
    @SerialName("rating_5based") val rating5: LDouble = null,
    @SerialName("backdrop_path") val backdropPath: LStrList = emptyList(),
    @SerialName("youtube_trailer") val youtubeTrailer: LStr = null,
    @SerialName("episode_run_time") val episodeRunTime: LStr = null,
    @SerialName("category_id") val categoryId: LStr = null,
)

@Serializable
data class SeriesInfoResponseDto(
    @Serializable(with = SeasonListSerializer::class) val seasons: List<SeasonDto> = emptyList(),
    @Serializable(with = SeriesDtoOrNull::class) val info: SeriesDto? = null,
    @Serializable(with = EpisodesBySeasonSerializer::class) val episodes: Map<Int, List<EpisodeDto>> = emptyMap(),
)

@Serializable
data class SeasonDto(
    val id: LLong = null,
    val name: LStr = null,
    @SerialName("season_number") val seasonNumber: LInt = null,
    @SerialName("episode_count") val episodeCount: LInt = null,
    @SerialName("air_date") val airDate: LStr = null,
    val overview: LStr = null,
    val cover: LStr = null,
    @SerialName("cover_big") val coverBig: LStr = null,
)

@Serializable
data class EpisodeDto(
    val id: LLong = null,
    @SerialName("episode_num") val episodeNum: LInt = null,
    val title: LStr = null,
    @SerialName("container_extension") val containerExtension: LStr = null,
    @Serializable(with = EpisodeInfoOrNull::class) val info: EpisodeInfoDto? = null,
    val added: LLong = null,
    val season: LInt = null,
    @SerialName("direct_source") val directSource: LStr = null,
)

@Serializable
data class EpisodeInfoDto(
    @SerialName("movie_image") val movieImage: LStr = null,
    val plot: LStr = null,
    @SerialName("duration_secs") val durationSecs: LLong = null,
    val duration: LStr = null,
    val rating: LDouble = null,
    val releasedate: LStr = null,
    val name: LStr = null,
)

@Serializable
data class ShortEpgResponseDto(
    @SerialName("epg_listings") @Serializable(with = EpgListingListSerializer::class) val listings: List<EpgListingDto> = emptyList(),
)

@Serializable
data class EpgListingDto(
    val id: LStr = null,
    @SerialName("epg_id") val epgId: LStr = null,
    val title: LStr = null,
    val lang: LStr = null,
    val start: LStr = null,
    val end: LStr = null,
    val description: LStr = null,
    @SerialName("channel_id") val channelId: LStr = null,
    @SerialName("start_timestamp") val startTimestamp: LLong = null,
    @SerialName("stop_timestamp") val stopTimestamp: LLong = null,
)

object UserInfoOrNull : ObjectOrNullSerializer<UserInfoDto>(UserInfoDto.serializer())
object ServerInfoOrNull : ObjectOrNullSerializer<ServerInfoDto>(ServerInfoDto.serializer())
object VodInfoOrNull : ObjectOrNullSerializer<VodInfoDto>(VodInfoDto.serializer())
object MovieDataOrNull : ObjectOrNullSerializer<MovieDataDto>(MovieDataDto.serializer())
object SeriesDtoOrNull : ObjectOrNullSerializer<SeriesDto>(SeriesDto.serializer())
object EpisodeInfoOrNull : ObjectOrNullSerializer<EpisodeInfoDto>(EpisodeInfoDto.serializer())
object SeasonListSerializer : LenientListSerializer<SeasonDto>(SeasonDto.serializer())
object EpgListingListSerializer : LenientListSerializer<EpgListingDto>(EpgListingDto.serializer())

/**
 * `get_series_info.episodes` is normally `{"1": [...], "2": [...]}` but some panels send a list of lists
 * (`[[...],[...]]`), a flat list, `{"1": {"0": {...}}}`, or `[]` when empty. Every episode is grouped by its
 * own `season` field when present, otherwise by its map key / list position.
 */
object EpisodesBySeasonSerializer : KSerializer<Map<Int, List<EpisodeDto>>> {
    private val delegate = MapSerializer(Int.serializer(), ListSerializer(EpisodeDto.serializer()))
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): Map<Int, List<EpisodeDto>> {
        val element = runCatching { (decoder as JsonDecoder).decodeJsonElement() }.getOrNull() ?: return emptyMap()
        val groups = mutableListOf<Pair<Int?, List<EpisodeDto>>>()
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                groups += key.trim().toIntOrNull() to decodeLenientList(value, EpisodeDto.serializer())
            }
            is JsonArray -> {
                if (element.all { it is JsonArray || (it is JsonObject && it["id"] == null) }) {
                    element.forEachIndexed { index, value -> groups += (index + 1) to decodeLenientList(value, EpisodeDto.serializer()) }
                } else {
                    groups += null to decodeLenientList(element, EpisodeDto.serializer())
                }
            }
            else -> Unit
        }
        val result = sortedMapOf<Int, MutableList<EpisodeDto>>()
        for ((key, episodes) in groups) {
            for (ep in episodes) {
                if (ep.id == null) continue
                val season = ep.season ?: key ?: 1
                result.getOrPut(season) { mutableListOf() } += ep.copy(season = season)
            }
        }
        return result.mapValues { (_, list) -> list.sortedWith(compareBy({ it.episodeNum ?: Int.MAX_VALUE }, { it.id })) }
    }

    override fun serialize(encoder: Encoder, value: Map<Int, List<EpisodeDto>>) = delegate.serialize(encoder, value)
}

/** Decodes top-level lists (categories, streams) tolerantly. */
internal fun <T> XtreamListOf(element: kotlinx.serialization.json.JsonElement, serializer: KSerializer<T>): List<T> =
    decodeLenientList(element, serializer, XtreamJson)
