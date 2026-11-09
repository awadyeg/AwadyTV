package com.awadytv.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles", indices = [Index(value = ["serverUrl", "username"], unique = true)])
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val serverUrl: String,
    val username: String,
    /** Key of the password inside EncryptedSharedPreferences; the password itself never touches Room. */
    val passwordRef: String,
    val createdAt: Long,
    val lastUsedAt: Long,
    val expDate: Long? = null,
    val status: String? = null,
    val maxConnections: Int? = null,
    val allowedFormats: String = "",
    val lastSyncAt: Long? = null,
)

object CategoryKind {
    const val LIVE = "live"
    const val MOVIE = "movie"
    const val SERIES = "series"
}

@Entity(tableName = "categories", primaryKeys = ["profileId", "kind", "id"])
data class CategoryEntity(
    val profileId: Long,
    val kind: String,
    val id: String,
    val name: String,
    val parentId: Int?,
    val sortOrder: Int,
)

@Entity(
    tableName = "channels",
    primaryKeys = ["profileId", "streamId"],
    indices = [Index(value = ["profileId", "categoryId"]), Index(value = ["profileId", "epgChannelId"])],
)
data class ChannelEntity(
    val profileId: Long,
    val streamId: Long,
    val num: Int,
    val name: String,
    val icon: String?,
    val epgChannelId: String?,
    val categoryId: String?,
    val tvArchive: Boolean,
    val tvArchiveDuration: Int?,
    val added: Long?,
    val sortOrder: Int,
)

@Entity(
    tableName = "movies",
    primaryKeys = ["profileId", "streamId"],
    indices = [Index(value = ["profileId", "categoryId"]), Index(value = ["profileId", "added"])],
)
data class MovieEntity(
    val profileId: Long,
    val streamId: Long,
    val name: String,
    val icon: String?,
    val rating: Double?,
    val added: Long?,
    val categoryId: String?,
    val containerExt: String?,
    val sortOrder: Int,
    val syncStamp: Long,
    // Enrichment from get_vod_info (kept across catalog re-syncs).
    val genre: String? = null,
    val year: Int? = null,
    val plot: String? = null,
    @ColumnInfo(name = "castList") val cast: String? = null,
    val director: String? = null,
    val backdrop: String? = null,
    val durationSecs: Long? = null,
    val trailer: String? = null,
    val tmdbId: String? = null,
    val infoLoadedAt: Long? = null,
)

/** Base columns written by catalog sync; enrichment columns are left untouched on upsert. */
data class MovieBase(
    val profileId: Long,
    val streamId: Long,
    val name: String,
    val icon: String?,
    val rating: Double?,
    val added: Long?,
    val categoryId: String?,
    val containerExt: String?,
    val sortOrder: Int,
    val syncStamp: Long,
)

@Entity(
    tableName = "series",
    primaryKeys = ["profileId", "seriesId"],
    indices = [Index(value = ["profileId", "categoryId"]), Index(value = ["profileId", "lastModified"])],
)
data class SeriesEntity(
    val profileId: Long,
    val seriesId: Long,
    val name: String,
    val cover: String?,
    val plot: String?,
    @ColumnInfo(name = "castList") val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: Double?,
    val backdrop: String?,
    val lastModified: Long?,
    val categoryId: String?,
    val trailer: String?,
    val episodeRunTime: String?,
    val sortOrder: Int,
)

@Entity(
    tableName = "episodes",
    primaryKeys = ["profileId", "episodeId"],
    indices = [Index(value = ["profileId", "seriesId", "season"])],
)
data class EpisodeEntity(
    val profileId: Long,
    val seriesId: Long,
    val season: Int,
    val episodeId: Long,
    val num: Int,
    val title: String,
    val containerExt: String?,
    val image: String?,
    val plot: String?,
    val durationSecs: Long?,
    val releaseDate: String?,
    val rating: Double?,
)

@Entity(
    tableName = "epg",
    primaryKeys = ["profileId", "epgChannelId", "start"],
    indices = [Index(value = ["profileId", "epgChannelId", "start"]), Index(value = ["profileId", "end"])],
)
data class EpgEntity(
    val profileId: Long,
    val epgChannelId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val description: String,
)

object ItemKind {
    const val LIVE = "live"
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val EPISODE = "episode"
}

@Entity(tableName = "favourites", primaryKeys = ["profileId", "kind", "itemId"])
data class FavouriteEntity(
    val profileId: Long,
    val kind: String,
    val itemId: Long,
    val addedAt: Long,
)

object LikeSource {
    const val EXPLICIT = "explicit"
    const val IMPLICIT = "implicit"
    /** Legacy: abandoning < 10 % used to count as a dislike. No longer written and ignored when read. */
    const val IMPLICIT_NEGATIVE = "implicit_negative"
    /** Explicit thumbs-down from the detail page. */
    const val DISLIKE = "dislike"
}

@Entity(tableName = "likes", primaryKeys = ["profileId", "kind", "itemId"])
data class LikeEntity(
    val profileId: Long,
    val kind: String,
    val itemId: Long,
    val likedAt: Long,
    val source: String,
)

@Entity(tableName = "progress", primaryKeys = ["profileId", "kind", "itemId"], indices = [Index(value = ["profileId", "updatedAt"])])
data class ProgressEntity(
    val profileId: Long,
    val kind: String,
    val itemId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** For episodes: the parent series, so Continue Watching can open the series and "watched" can roll up. */
    val seriesId: Long? = null,
    val title: String? = null,
    val image: String? = null,
    val containerExt: String? = null,
)

@Entity(tableName = "recommendations", primaryKeys = ["profileId", "shelf", "itemKind", "itemId"])
data class RecommendationEntity(
    val profileId: Long,
    val shelf: String,
    val shelfTitle: String,
    val itemKind: String,
    val itemId: Long,
    val score: Double,
    val reason: String?,
    val position: Int,
    val computedAt: Long,
)

@Entity(tableName = "category_opens", primaryKeys = ["profileId", "kind", "categoryId"])
data class CategoryOpenEntity(
    val profileId: Long,
    val kind: String,
    val categoryId: String,
    val count: Int,
    val lastOpenedAt: Long,
)
