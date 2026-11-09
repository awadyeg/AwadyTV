package com.awadytv.domain

import com.awadytv.core.database.ItemKind
import com.awadytv.core.network.XtreamCredentials

enum class ContentKind(val key: String) {
    LIVE(ItemKind.LIVE), MOVIE(ItemKind.MOVIE), SERIES(ItemKind.SERIES), EPISODE(ItemKind.EPISODE);

    companion object {
        fun fromKey(key: String): ContentKind = entries.firstOrNull { it.key == key } ?: MOVIE
    }
}

data class Profile(
    val id: Long,
    val name: String,
    val serverUrl: String,
    val username: String,
    val expDate: Long?,
    val status: String?,
    val maxConnections: Int?,
    val allowedFormats: List<String>,
    val lastSyncAt: Long?,
    val lastUsedAt: Long,
)

/** An authenticated profile with its decrypted credentials. Never log this. */
data class Session(val profile: Profile, val credentials: XtreamCredentials) {
    val profileId: Long get() = profile.id
    override fun toString(): String = "Session(profileId=${profile.id})"
}

data class Category(val id: String, val name: String, val isAdult: Boolean)

data class Channel(
    val streamId: Long,
    val num: Int,
    val name: String,
    val icon: String?,
    val epgChannelId: String?,
    val categoryId: String?,
    val tvArchive: Boolean,
)

data class Movie(
    val streamId: Long,
    val name: String,
    val icon: String?,
    val rating: Double?,
    val added: Long?,
    val categoryId: String?,
    val containerExt: String?,
    val genre: String?,
    val year: Int?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val backdrop: String?,
    val durationSecs: Long?,
    val trailer: String?,
    val infoLoaded: Boolean,
)

data class Series(
    val seriesId: Long,
    val name: String,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val year: Int?,
    val rating: Double?,
    val backdrop: String?,
    val lastModified: Long?,
    val categoryId: String?,
    val trailer: String?,
    val episodeRunTime: String?,
)

data class Episode(
    val episodeId: Long,
    val seriesId: Long,
    val season: Int,
    val num: Int,
    val title: String,
    val containerExt: String?,
    val image: String?,
    val plot: String?,
    val durationSecs: Long?,
    val releaseDate: String?,
)

data class EpgEntry(val channelId: String, val start: Long, val end: Long, val title: String, val description: String) {
    fun progress(now: Long): Float = if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    fun isLive(now: Long) = now in start until end
}

data class NowNext(val now: EpgEntry?, val next: EpgEntry?)

data class WatchProgress(
    val kind: ContentKind,
    val itemId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val seriesId: Long?,
    val title: String?,
    val image: String?,
) {
    val fraction: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** Category names that indicate adult content (locked behind the parental PIN). */
object AdultContent {
    private val pattern = Regex("(?i)(\\bxxx\\b|\\badults?\\b|18\\s*\\+|\\+\\s*18|\\bporn|\\berotic|\\bfor adults\\b)")
    fun isAdult(categoryName: String?): Boolean = categoryName != null && pattern.containsMatchIn(categoryName)
}

/** Applies a saved user order: ids in [order] first in that order, the rest after in provider order. */
fun applyCategoryOrder(categories: List<Category>, order: List<String>): List<Category> {
    if (order.isEmpty()) return categories
    val rank = order.withIndex().associate { it.value to it.index }
    val (known, unknown) = categories.partition { it.id in rank }
    return known.sortedBy { rank.getValue(it.id) } + unknown
}
