package com.awadytv.data.xtream

import com.awadytv.core.network.XtreamCredentials
import com.awadytv.core.network.XtreamJson
import com.awadytv.core.network.XtreamUrls
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/** Typed wrapper over [XtreamApi]: one method per Xtream action, all decoding lenient. */
@Singleton
class XtreamClient @Inject constructor(private val api: XtreamApi) {

    private suspend fun call(c: XtreamCredentials, vararg params: Pair<String, String>): JsonElement =
        api.playerApi(XtreamUrls.playerApi(c.server), c.username, c.password, params.toMap())

    suspend fun authenticate(c: XtreamCredentials): AuthResponseDto =
        XtreamJson.decodeFromJsonElement(AuthResponseDto.serializer(), call(c))

    suspend fun liveCategories(c: XtreamCredentials): List<CategoryDto> =
        XtreamListOf(call(c, "action" to "get_live_categories"), CategoryDto.serializer())

    suspend fun liveStreams(c: XtreamCredentials, categoryId: String? = null): List<LiveStreamDto> =
        XtreamListOf(call(c, *actionWithCategory("get_live_streams", categoryId)), LiveStreamDto.serializer())

    suspend fun vodCategories(c: XtreamCredentials): List<CategoryDto> =
        XtreamListOf(call(c, "action" to "get_vod_categories"), CategoryDto.serializer())

    suspend fun vodStreams(c: XtreamCredentials, categoryId: String? = null): List<VodStreamDto> =
        XtreamListOf(call(c, *actionWithCategory("get_vod_streams", categoryId)), VodStreamDto.serializer())

    suspend fun vodInfo(c: XtreamCredentials, vodId: Long): VodInfoResponseDto =
        XtreamJson.decodeFromJsonElement(VodInfoResponseDto.serializer(), call(c, "action" to "get_vod_info", "vod_id" to vodId.toString()))

    suspend fun seriesCategories(c: XtreamCredentials): List<CategoryDto> =
        XtreamListOf(call(c, "action" to "get_series_categories"), CategoryDto.serializer())

    suspend fun series(c: XtreamCredentials, categoryId: String? = null): List<SeriesDto> =
        XtreamListOf(call(c, *actionWithCategory("get_series", categoryId)), SeriesDto.serializer())

    suspend fun seriesInfo(c: XtreamCredentials, seriesId: Long): SeriesInfoResponseDto =
        XtreamJson.decodeFromJsonElement(SeriesInfoResponseDto.serializer(), call(c, "action" to "get_series_info", "series_id" to seriesId.toString()))

    suspend fun shortEpg(c: XtreamCredentials, streamId: Long, limit: Int = 4): ShortEpgResponseDto =
        XtreamJson.decodeFromJsonElement(ShortEpgResponseDto.serializer(), call(c, "action" to "get_short_epg", "stream_id" to streamId.toString(), "limit" to limit.toString()))

    suspend fun fullEpg(c: XtreamCredentials, streamId: Long): ShortEpgResponseDto =
        XtreamJson.decodeFromJsonElement(ShortEpgResponseDto.serializer(), call(c, "action" to "get_simple_data_table", "stream_id" to streamId.toString()))

    private fun actionWithCategory(action: String, categoryId: String?): Array<Pair<String, String>> =
        if (categoryId == null) arrayOf("action" to action) else arrayOf("action" to action, "category_id" to categoryId)
}
