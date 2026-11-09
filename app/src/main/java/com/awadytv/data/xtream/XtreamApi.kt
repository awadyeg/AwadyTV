package com.awadytv.data.xtream

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * Xtream Codes `player_api.php`. The server differs per profile, so the full endpoint URL is passed in.
 * Responses are read as [JsonElement] and decoded tolerantly by [XtreamClient].
 */
interface XtreamApi {
    @GET
    suspend fun playerApi(
        @Url endpoint: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @QueryMap params: Map<String, String> = emptyMap(),
    ): JsonElement
}
