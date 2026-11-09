package com.awadytv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY lastUsedAt DESC")
    suspend fun all(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observe(id: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE serverUrl = :server AND username = :username LIMIT 1")
    suspend fun find(server: String, username: String): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("UPDATE profiles SET lastUsedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE profiles SET lastSyncAt = :at WHERE id = :id")
    suspend fun setLastSync(id: Long, at: Long)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE profileId = :profileId AND kind = :kind ORDER BY sortOrder")
    fun observe(profileId: Long, kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE profileId = :profileId AND kind = :kind ORDER BY sortOrder")
    suspend fun all(profileId: Long, kind: String): List<CategoryEntity>

    @Query("DELETE FROM categories WHERE profileId = :profileId AND kind = :kind")
    suspend fun clear(profileId: Long, kind: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CategoryEntity>)

    @Transaction
    suspend fun replace(profileId: Long, kind: String, items: List<CategoryEntity>) {
        clear(profileId, kind)
        insertAll(items)
    }
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE profileId = :profileId AND categoryId = :categoryId ORDER BY sortOrder")
    fun observeByCategory(profileId: Long, categoryId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE profileId = :profileId ORDER BY sortOrder")
    fun observeAll(profileId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE profileId = :profileId AND categoryId = :categoryId ORDER BY sortOrder")
    suspend fun byCategory(profileId: Long, categoryId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE profileId = :profileId ORDER BY sortOrder")
    suspend fun all(profileId: Long): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE profileId = :profileId AND streamId = :streamId")
    suspend fun get(profileId: Long, streamId: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE profileId = :profileId AND streamId IN (:ids)")
    suspend fun byIds(profileId: Long, ids: List<Long>): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE profileId = :profileId AND name LIKE '%' || :query || '%' ORDER BY sortOrder LIMIT :limit")
    suspend fun search(profileId: Long, query: String, limit: Int): List<ChannelEntity>

    @Query("SELECT DISTINCT epgChannelId FROM channels WHERE profileId = :profileId AND epgChannelId IS NOT NULL AND epgChannelId != ''")
    suspend fun epgChannelIds(profileId: Long): List<String>

    @Query("DELETE FROM channels WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelEntity>)

    @Transaction
    suspend fun replace(profileId: Long, items: List<ChannelEntity>) {
        clear(profileId)
        items.chunked(500).forEach { insertAll(it) }
    }
}

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE profileId = :profileId AND categoryId = :categoryId ORDER BY sortOrder")
    fun observeByCategory(profileId: Long, categoryId: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE profileId = :profileId ORDER BY added DESC LIMIT :limit")
    fun observeRecent(profileId: Long, limit: Int): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE profileId = :profileId AND streamId = :streamId")
    fun observe(profileId: Long, streamId: Long): Flow<MovieEntity?>

    @Query("SELECT * FROM movies WHERE profileId = :profileId AND streamId = :streamId")
    suspend fun get(profileId: Long, streamId: Long): MovieEntity?

    @Query("SELECT * FROM movies WHERE profileId = :profileId AND streamId IN (:ids)")
    suspend fun byIds(profileId: Long, ids: List<Long>): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE profileId = :profileId")
    suspend fun all(profileId: Long): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE profileId = :profileId AND name LIKE '%' || :query || '%' ORDER BY added DESC LIMIT :limit")
    suspend fun search(profileId: Long, query: String, limit: Int): List<MovieEntity>

    @Upsert(entity = MovieEntity::class)
    suspend fun upsertBase(items: List<MovieBase>)

    @Query("DELETE FROM movies WHERE profileId = :profileId AND syncStamp != :stamp")
    suspend fun deleteStale(profileId: Long, stamp: Long)

    @Query(
        """UPDATE movies SET genre = :genre, year = :year, plot = :plot, castList = :cast, director = :director,
           backdrop = :backdrop, durationSecs = :durationSecs, trailer = :trailer, tmdbId = :tmdbId,
           rating = COALESCE(:rating, rating), infoLoadedAt = :loadedAt
           WHERE profileId = :profileId AND streamId = :streamId""",
    )
    suspend fun updateInfo(
        profileId: Long, streamId: Long, genre: String?, year: Int?, plot: String?, cast: String?, director: String?,
        backdrop: String?, durationSecs: Long?, trailer: String?, tmdbId: String?, rating: Double?, loadedAt: Long,
    )

    @Transaction
    suspend fun sync(profileId: Long, stamp: Long, items: List<MovieBase>) {
        items.chunked(500).forEach { upsertBase(it) }
        deleteStale(profileId, stamp)
    }

    @Query("DELETE FROM movies WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE profileId = :profileId AND categoryId = :categoryId ORDER BY sortOrder")
    fun observeByCategory(profileId: Long, categoryId: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE profileId = :profileId ORDER BY lastModified DESC LIMIT :limit")
    fun observeRecent(profileId: Long, limit: Int): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE profileId = :profileId AND seriesId = :seriesId")
    fun observe(profileId: Long, seriesId: Long): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE profileId = :profileId AND seriesId = :seriesId")
    suspend fun get(profileId: Long, seriesId: Long): SeriesEntity?

    @Query("SELECT * FROM series WHERE profileId = :profileId AND seriesId IN (:ids)")
    suspend fun byIds(profileId: Long, ids: List<Long>): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE profileId = :profileId")
    suspend fun all(profileId: Long): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE profileId = :profileId AND name LIKE '%' || :query || '%' ORDER BY lastModified DESC LIMIT :limit")
    suspend fun search(profileId: Long, query: String, limit: Int): List<SeriesEntity>

    @Upsert
    suspend fun upsert(item: SeriesEntity)

    @Query("DELETE FROM series WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SeriesEntity>)

    @Transaction
    suspend fun replace(profileId: Long, items: List<SeriesEntity>) {
        clear(profileId)
        items.chunked(500).forEach { insertAll(it) }
    }
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE profileId = :profileId AND seriesId = :seriesId ORDER BY season, num")
    fun observeBySeries(profileId: Long, seriesId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE profileId = :profileId AND seriesId = :seriesId ORDER BY season, num")
    suspend fun bySeries(profileId: Long, seriesId: Long): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE profileId = :profileId AND episodeId = :episodeId")
    suspend fun get(profileId: Long, episodeId: Long): EpisodeEntity?

    @Query("DELETE FROM episodes WHERE profileId = :profileId AND seriesId = :seriesId")
    suspend fun clearSeries(profileId: Long, seriesId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EpisodeEntity>)

    @Transaction
    suspend fun replaceSeries(profileId: Long, seriesId: Long, items: List<EpisodeEntity>) {
        clearSeries(profileId, seriesId)
        insertAll(items)
    }

    @Query("DELETE FROM episodes WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)
}

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg WHERE profileId = :profileId AND epgChannelId IN (:channelIds) AND `end` > :from AND start < :to ORDER BY start")
    suspend fun window(profileId: Long, channelIds: List<String>, from: Long, to: Long): List<EpgEntity>

    @Query("SELECT * FROM epg WHERE profileId = :profileId AND epgChannelId = :channelId AND `end` > :from ORDER BY start LIMIT :limit")
    fun observeUpcoming(profileId: Long, channelId: String, from: Long, limit: Int): Flow<List<EpgEntity>>

    @Query("SELECT MAX(`end`) FROM epg WHERE profileId = :profileId AND epgChannelId = :channelId")
    suspend fun coveredUntil(profileId: Long, channelId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EpgEntity>)

    @Query("DELETE FROM epg WHERE profileId = :profileId AND `end` < :before")
    suspend fun deleteEndedBefore(profileId: Long, before: Long)

    @Query("DELETE FROM epg WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)
}

@Dao
interface FavouriteDao {
    @Query("SELECT * FROM favourites WHERE profileId = :profileId AND kind = :kind ORDER BY addedAt DESC")
    fun observe(profileId: Long, kind: String): Flow<List<FavouriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE profileId = :profileId AND kind = :kind AND itemId = :itemId)")
    fun observeIs(profileId: Long, kind: String, itemId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE profileId = :profileId AND kind = :kind AND itemId = :itemId")
    suspend fun delete(profileId: Long, kind: String, itemId: Long)

    @Query("SELECT * FROM favourites WHERE profileId = :profileId ORDER BY addedAt DESC")
    suspend fun all(profileId: Long): List<FavouriteEntity>

    @Query("DELETE FROM favourites WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)
}

@Dao
interface LikeDao {
    @Query("SELECT * FROM likes WHERE profileId = :profileId ORDER BY likedAt DESC")
    fun observeAll(profileId: Long): Flow<List<LikeEntity>>

    @Query("SELECT * FROM likes WHERE profileId = :profileId ORDER BY likedAt DESC")
    suspend fun all(profileId: Long): List<LikeEntity>

    @Query("SELECT * FROM likes WHERE profileId = :profileId AND kind = :kind AND itemId = :itemId")
    fun observe(profileId: Long, kind: String, itemId: Long): Flow<LikeEntity?>

    @Query("SELECT * FROM likes WHERE profileId = :profileId AND kind = :kind AND itemId = :itemId")
    suspend fun get(profileId: Long, kind: String, itemId: Long): LikeEntity?

    @Upsert
    suspend fun upsert(item: LikeEntity)

    @Query("DELETE FROM likes WHERE profileId = :profileId AND kind = :kind AND itemId = :itemId")
    suspend fun delete(profileId: Long, kind: String, itemId: Long)

    @Query("DELETE FROM likes WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)
}

@Dao
interface ProgressDao {
    @Query(
        """SELECT * FROM progress WHERE profileId = :profileId AND kind IN ('movie','episode')
           AND positionMs > 30000 AND (durationMs <= 0 OR positionMs < durationMs * 0.95)
           ORDER BY updatedAt DESC LIMIT :limit""",
    )
    fun observeContinueWatching(profileId: Long, limit: Int): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE profileId = :profileId")
    fun observeAll(profileId: Long): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE profileId = :profileId")
    suspend fun all(profileId: Long): List<ProgressEntity>

    @Query("SELECT * FROM progress WHERE profileId = :profileId AND kind = :kind AND itemId = :itemId")
    suspend fun get(profileId: Long, kind: String, itemId: Long): ProgressEntity?

    @Query("SELECT * FROM progress WHERE profileId = :profileId AND kind = :kind AND itemId = :itemId")
    fun observe(profileId: Long, kind: String, itemId: Long): Flow<ProgressEntity?>

    @Query("SELECT * FROM progress WHERE profileId = :profileId AND kind = 'episode' AND seriesId = :seriesId")
    fun observeForSeries(profileId: Long, seriesId: Long): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE profileId = :profileId AND kind = 'episode' AND seriesId = :seriesId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestForSeries(profileId: Long, seriesId: Long): ProgressEntity?

    @Upsert
    suspend fun upsert(item: ProgressEntity)

    @Query("DELETE FROM progress WHERE profileId = :profileId AND kind = :kind AND itemId = :itemId")
    suspend fun delete(profileId: Long, kind: String, itemId: Long)

    @Query("DELETE FROM progress WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)
}

@Dao
interface RecommendationDao {
    @Query("SELECT * FROM recommendations WHERE profileId = :profileId ORDER BY shelf, position")
    fun observe(profileId: Long): Flow<List<RecommendationEntity>>

    @Query("SELECT MAX(computedAt) FROM recommendations WHERE profileId = :profileId")
    suspend fun lastComputedAt(profileId: Long): Long?

    @Query("SELECT COUNT(*) FROM recommendations WHERE profileId = :profileId AND shelf LIKE 'foryou:%'")
    suspend fun forYouCount(profileId: Long): Int

    @Query("DELETE FROM recommendations WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RecommendationEntity>)

    @Transaction
    suspend fun replace(profileId: Long, items: List<RecommendationEntity>) {
        clear(profileId)
        insertAll(items)
    }
}

@Dao
interface CategoryOpenDao {
    @Query("SELECT * FROM category_opens WHERE profileId = :profileId")
    suspend fun all(profileId: Long): List<CategoryOpenEntity>

    @Query("SELECT * FROM category_opens WHERE profileId = :profileId AND kind = :kind AND categoryId = :categoryId")
    suspend fun get(profileId: Long, kind: String, categoryId: String): CategoryOpenEntity?

    @Upsert
    suspend fun upsert(item: CategoryOpenEntity)

    @Query("DELETE FROM category_opens WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)
}
