package com.awadytv.navigation

import kotlinx.serialization.Serializable

@Serializable object StartRoute

@Serializable data class LoginRoute(val addProfile: Boolean = false)

@Serializable data class SyncRoute(val profileId: Long)

@Serializable object MainRoute

@Serializable data class MovieDetailRoute(val streamId: Long)

@Serializable data class SeriesDetailRoute(val seriesId: Long)

/** VOD player: [kind] is "movie" or "episode"; [seriesId] is set for episodes. */
@Serializable data class VodPlayerRoute(val kind: String, val itemId: Long, val seriesId: Long = -1L, val fromStart: Boolean = false)

@Serializable data class LivePlayerRoute(val streamId: Long, val categoryId: String? = null)

/** Navigation actions available to every screen. */
interface AppNavigator {
    fun openMovie(streamId: Long)
    fun openSeries(seriesId: Long)
    fun playMovie(streamId: Long, fromStart: Boolean = false)
    fun playEpisode(seriesId: Long, episodeId: Long, fromStart: Boolean = false)
    fun playLive(streamId: Long, categoryId: String?)
    fun addProfile()
    fun resync(profileId: Long)
    fun signedOut()
    fun back()
}
