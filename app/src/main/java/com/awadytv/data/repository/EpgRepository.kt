package com.awadytv.data.repository

import android.util.Xml
import com.awadytv.core.database.ChannelDao
import com.awadytv.core.database.EpgDao
import com.awadytv.core.database.EpgEntity
import com.awadytv.core.network.XtreamUrls
import com.awadytv.core.util.TimeFormat
import com.awadytv.data.xtream.XtreamClient
import com.awadytv.data.xtream.toEpgEntities
import com.awadytv.domain.Channel
import com.awadytv.domain.EpgEntry
import com.awadytv.domain.NowNext
import com.awadytv.domain.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepository @Inject constructor(
    private val dao: EpgDao,
    private val channels: ChannelDao,
    private val client: XtreamClient,
    private val http: OkHttpClient,
) {
    private val lastShortFetch = ConcurrentHashMap<String, Long>()

    /** Now/next for many channels from the Room cache. */
    suspend fun nowNext(profileId: Long, channelList: List<Channel>, now: Long = System.currentTimeMillis()): Map<String, NowNext> =
        withContext(Dispatchers.IO) {
            val ids = channelList.mapNotNull { it.epgChannelId?.takeIf(String::isNotBlank) }.distinct()
            val rows = ids.chunked(500).flatMap { dao.window(profileId, it, now - 60_000, now + 12 * HOUR) }
            rows.groupBy { it.epgChannelId }.mapValues { (_, list) ->
                val sorted = list.sortedBy { it.start }
                val current = sorted.firstOrNull { now in it.start until it.end }
                val next = sorted.firstOrNull { it.start >= (current?.end ?: now) }
                NowNext(current?.toDomain(), next?.toDomain())
            }
        }

    fun observeUpcoming(profileId: Long, epgChannelId: String, limit: Int = 8): Flow<List<EpgEntry>> =
        dao.observeUpcoming(profileId, epgChannelId, System.currentTimeMillis() - 4 * HOUR, limit).map { list -> list.map { it.toDomain() } }

    /**
     * Fetches `get_short_epg` for a channel unless the cache already covers the next hour, or it was fetched
     * in the last 15 minutes (Xtream panels rate-limit).
     */
    suspend fun ensureShortEpg(session: Session, channel: Channel) = withContext(Dispatchers.IO) {
        val epgId = channel.epgChannelId?.takeIf { it.isNotBlank() } ?: return@withContext
        val key = "${session.profileId}:$epgId"
        val now = System.currentTimeMillis()
        if ((lastShortFetch[key] ?: 0L) > now - 15 * MINUTE) return@withContext
        val covered = dao.coveredUntil(session.profileId, epgId) ?: 0L
        if (covered > now + HOUR) return@withContext
        lastShortFetch[key] = now
        try {
            val rows = client.shortEpg(session.credentials, channel.streamId, limit = 6).toEpgEntities(session.profileId, epgId)
            if (rows.isNotEmpty()) dao.insertAll(rows)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // EPG is best-effort; the UI simply shows no programme info.
        }
    }

    /**
     * Full refresh from `xmltv.php`, parsed with a streaming pull parser. Only programmes in a
     * [-2h, +36h] window for channels in the catalog are kept. Falls back to short EPG for [fallbackChannels].
     */
    suspend fun refreshFull(session: Session, fallbackChannels: List<Channel> = emptyList()): Int = withContext(Dispatchers.IO) {
        val profileId = session.profileId
        val now = System.currentTimeMillis()
        dao.deleteEndedBefore(profileId, now - 6 * HOUR)
        val known = channels.epgChannelIds(profileId).toHashSet()
        if (known.isEmpty()) return@withContext 0
        val count = try {
            val request = Request.Builder().url(XtreamUrls.xmltv(session.credentials)).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("empty body")
                parseXmltv(body.byteStream(), known, now - 2 * HOUR, now + 36 * HOUR) { batch -> dao.insertAll(batch.map { it.copy(profileId = profileId) }) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            0
        }
        if (count == 0) fallbackChannels.forEach { ensureShortEpg(session, it) }
        count
    }

    private suspend fun parseXmltv(
        input: InputStream,
        known: Set<String>,
        from: Long,
        to: Long,
        flush: suspend (List<EpgEntity>) -> Unit,
    ): Int {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val batch = ArrayList<EpgEntity>(500)
        var total = 0
        var channel: String? = null
        var start: Long? = null
        var stop: Long? = null
        var title = ""
        var desc = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        channel = parser.getAttributeValue(null, "channel")
                        start = TimeFormat.parseXmltv(parser.getAttributeValue(null, "start"))
                        stop = TimeFormat.parseXmltv(parser.getAttributeValue(null, "stop"))
                        title = ""
                        desc = ""
                    }
                    "title" -> if (channel != null && title.isEmpty()) title = parser.nextText().orEmpty()
                    "desc" -> if (channel != null && desc.isEmpty()) desc = parser.nextText().orEmpty()
                }
                XmlPullParser.END_TAG -> if (parser.name == "programme") {
                    val ch = channel
                    val s = start
                    val e = stop
                    if (ch != null && s != null && e != null && ch in known && e > from && s < to) {
                        batch += EpgEntity(0, ch, s, e, title.trim().ifEmpty { "No information" }, desc.trim())
                        if (batch.size >= 500) {
                            flush(batch.toList()); total += batch.size; batch.clear()
                        }
                    }
                    channel = null
                }
            }
            event = parser.next()
        }
        if (batch.isNotEmpty()) {
            flush(batch.toList()); total += batch.size
        }
        return total
    }

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
    }
}

fun EpgEntity.toDomain() = EpgEntry(epgChannelId, start, end, title, description)
