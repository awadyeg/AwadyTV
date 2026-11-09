package com.awadytv.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        ProfileEntity::class, CategoryEntity::class, ChannelEntity::class, MovieEntity::class, SeriesEntity::class,
        EpisodeEntity::class, EpgEntity::class, FavouriteEntity::class, LikeEntity::class, ProgressEntity::class,
        RecommendationEntity::class, CategoryOpenEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun categories(): CategoryDao
    abstract fun channels(): ChannelDao
    abstract fun movies(): MovieDao
    abstract fun series(): SeriesDao
    abstract fun episodes(): EpisodeDao
    abstract fun epg(): EpgDao
    abstract fun favourites(): FavouriteDao
    abstract fun likes(): LikeDao
    abstract fun progress(): ProgressDao
    abstract fun recommendations(): RecommendationDao
    abstract fun categoryOpens(): CategoryOpenDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "awadytv.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun profileDao(db: AppDatabase) = db.profiles()
    @Provides fun categoryDao(db: AppDatabase) = db.categories()
    @Provides fun channelDao(db: AppDatabase) = db.channels()
    @Provides fun movieDao(db: AppDatabase) = db.movies()
    @Provides fun seriesDao(db: AppDatabase) = db.series()
    @Provides fun episodeDao(db: AppDatabase) = db.episodes()
    @Provides fun epgDao(db: AppDatabase) = db.epg()
    @Provides fun favouriteDao(db: AppDatabase) = db.favourites()
    @Provides fun likeDao(db: AppDatabase) = db.likes()
    @Provides fun progressDao(db: AppDatabase) = db.progress()
    @Provides fun recommendationDao(db: AppDatabase) = db.recommendations()
    @Provides fun categoryOpenDao(db: AppDatabase) = db.categoryOpens()
}
