package com.awadytv.data.repository

import com.awadytv.core.database.CategoryDao
import com.awadytv.core.database.CategoryKind
import com.awadytv.core.database.ChannelDao
import com.awadytv.core.database.ChannelEntity
import com.awadytv.core.database.FavouriteDao
import com.awadytv.core.database.ItemKind
import com.awadytv.core.network.StreamFormat
import com.awadytv.core.network.XtreamUrls
import com.awadytv.core.datastore.PreferredFormat
import com.awadytv.domain.AdultContent
import com.awadytv.domain.Category
import com.awadytv.domain.Channel
import com.awadytv.domain.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveRepository @Inject constructor(
    private val categories: CategoryDao,
    private val channels: ChannelDao,
    private val favourites: FavouriteDao,
) {
    fun categories(profileId: Long): Flow<List<Category>> =
        categories.observe(profileId, CategoryKind.LIVE).map { list -> list.map { Category(it.id, it.name, AdultContent.isAdult(it.name)) } }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun channels(profileId: Long, categoryId: String): Flow<List<Channel>> = when (categoryId) {
        ALL -> channels.observeAll(profileId).map { list -> list.map { it.toDomain() } }
        FAVOURITES -> favouriteChannelsFlow(profileId)
        else -> channels.observeByCategory(profileId, categoryId).map { list -> list.map { it.toDomain() } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun favouriteChannelsFlow(profileId: Long): Flow<List<Channel>> =
        favourites.observe(profileId, ItemKind.LIVE).flatMapLatest { favs ->
            flow {
                val ids = favs.map { it.itemId }
                val byId = ids.chunked(500).flatMap { channels.byIds(profileId, it) }.associateBy { it.streamId }
                emit(ids.mapNotNull { byId[it]?.toDomain() })
            }
        }

    suspend fun favouriteChannels(profileId: Long): List<Channel> {
        val ids = favouritesOnce(profileId)
        val byId = ids.chunked(500).flatMap { channels.byIds(profileId, it) }.associateBy { it.streamId }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

    private suspend fun favouritesOnce(profileId: Long): List<Long> =
        favourites.observe(profileId, ItemKind.LIVE).first().map { it.itemId }

    /** Channels used for D-pad up/down zapping: same category as the playing channel. */
    suspend fun zapList(profileId: Long, categoryId: String?): List<Channel> = when (categoryId) {
        null, ALL -> channels.all(profileId).map { it.toDomain() }
        FAVOURITES -> favouriteChannels(profileId)
        else -> channels.byCategory(profileId, categoryId).map { it.toDomain() }
    }

    suspend fun channel(profileId: Long, streamId: Long): Channel? = channels.get(profileId, streamId)?.toDomain()

    suspend fun search(profileId: Long, query: String, limit: Int = 60): List<Channel> = channels.search(profileId, query, limit).map { it.toDomain() }

    fun streamUrl(session: Session, channel: Channel, preferred: PreferredFormat): String {
        val pref = when (preferred) {
            PreferredFormat.AUTO -> null
            PreferredFormat.M3U8 -> StreamFormat.M3U8
            PreferredFormat.TS -> StreamFormat.TS
        }
        val format = XtreamUrls.liveFormat(pref, session.profile.allowedFormats)
        return XtreamUrls.live(session.credentials, channel.streamId, format)
    }

    companion object {
        const val ALL = "__all__"
        const val FAVOURITES = "__favourites__"
    }
}

fun ChannelEntity.toDomain() = Channel(streamId, num, name, icon, epgChannelId, categoryId, tvArchive)
