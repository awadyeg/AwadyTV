package com.awadytv.data.xtream

import com.awadytv.core.network.XtreamJson
import com.awadytv.core.util.AppError
import com.awadytv.data.repository.ProfileRepository
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamParsingTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("xtream/$name")) { "missing fixture $name" }.readText()

    private fun element(name: String): JsonElement = XtreamJson.parseToJsonElement(fixture(name))

    @Test fun `auth response with string numbers`() {
        val auth = XtreamJson.decodeFromJsonElement(AuthResponseDto.serializer(), element("auth_ok.json"))
        val user = auth.userInfo!!
        assertEquals(1, user.auth)
        assertEquals("Active", user.status)
        assertEquals(4102444800L, user.expDate)
        assertEquals(2, user.maxConnections)
        assertEquals(listOf("m3u8", "ts", "rtmp"), user.allowedOutputFormats)
        assertEquals("8080", auth.serverInfo!!.port)
        assertEquals(1731150000L, auth.serverInfo!!.timestampNow)
        assertNull(ProfileRepository.validate(auth, nowMs = 1_731_150_000_000))
    }

    @Test fun `expired account with server_info as empty array and formats as string`() {
        val auth = XtreamJson.decodeFromJsonElement(AuthResponseDto.serializer(), element("auth_expired.json"))
        assertNull(auth.serverInfo)
        assertEquals(listOf("ts"), auth.userInfo!!.allowedOutputFormats)
        assertTrue(ProfileRepository.validate(auth) is AppError.Expired)
    }

    @Test fun `auth zero is wrong credentials`() {
        val auth = XtreamJson.decodeFromJsonElement(AuthResponseDto.serializer(), element("auth_failed.json"))
        assertEquals(AppError.WrongCredentials, ProfileRepository.validate(auth))
        assertEquals(AppError.WrongCredentials, ProfileRepository.validate(AuthResponseDto()))
    }

    @Test fun `categories tolerate numeric ids and drop blank ids`() {
        val cats = XtreamListOf(element("live_categories.json"), CategoryDto.serializer())
        assertEquals(4, cats.size)
        assertEquals("2", cats[1].categoryId)
        assertEquals(0, cats[1].parentId)
        val entities = cats.toEntities(profileId = 1, kind = "live")
        assertEquals(listOf("1", "2", "99"), entities.map { it.id })
    }

    @Test fun `live streams with mixed types skip garbage and missing ids`() {
        val streams = XtreamListOf(element("live_streams.json"), LiveStreamDto.serializer())
        assertEquals(4, streams.size) // the bare string is skipped
        assertEquals(102L, streams[1].streamId)
        assertEquals(2, streams[1].num)
        assertEquals("2", streams[1].categoryId)
        assertNull(streams[1].streamIcon)
        assertEquals(7, streams[0].tvArchiveDuration)
        assertEquals(103L, streams[3].streamId)
        val channels = streams.toChannelEntities(1)
        assertEquals(listOf(101L, 102L, 103L), channels.map { it.streamId })
        assertTrue(channels[0].tvArchive)
        assertEquals(false, channels[2].tvArchive)
    }

    @Test fun `object of items is accepted where a list is expected`() {
        val json = """{"a":{"category_id":"5","category_name":"Five"},"b":{"category_id":6,"category_name":"Six"}}"""
        val cats = XtreamListOf(XtreamJson.parseToJsonElement(json), CategoryDto.serializer())
        assertEquals(listOf("5", "6"), cats.map { it.categoryId })
    }

    @Test fun `vod streams ratings from string or five based`() {
        val vod = XtreamListOf(element("vod_streams.json"), VodStreamDto.serializer())
        val bases = vod.toMovieBases(1, stamp = 7)
        assertEquals(8.3, bases[0].rating!!, 0.001)
        assertEquals(8.0, bases[1].rating!!, 0.001) // "" rating -> 5-based * 2
        assertEquals("mkv", bases[0].containerExt)
        assertEquals(5002L, bases[1].streamId)
    }

    @Test fun `vod info with full metadata`() {
        val info = XtreamJson.decodeFromJsonElement(VodInfoResponseDto.serializer(), element("vod_info.json"))
        val mapped = info.toMovieInfo("Heat (1995)")
        assertEquals("Action, Crime, Drama", mapped.genre)
        assertEquals(1995, mapped.year)
        assertEquals(10224L, mapped.durationSecs)
        assertEquals("http://img.example/heat_bd.jpg", mapped.backdrop)
        assertEquals("Michael Mann", mapped.director)
        assertEquals(8.3, mapped.rating!!, 0.001)
    }

    @Test fun `vod info with info as empty array`() {
        val info = XtreamJson.decodeFromJsonElement(VodInfoResponseDto.serializer(), element("vod_info_empty.json"))
        assertNull(info.info)
        assertEquals(5002L, info.movieData!!.streamId)
        assertNull(info.toMovieInfo("Arrival").genre)
    }

    @Test fun `series list with backdrop as string or empty array`() {
        val series = XtreamListOf(element("series.json"), SeriesDto.serializer())
        val entities = series.toSeriesEntities(1)
        assertEquals(2, entities.size)
        assertEquals("http://img.example/dark_bd.jpg", entities[0].backdrop)
        assertEquals(8.7, entities[0].rating!!, 0.001)
        assertEquals(1700000500L, entities[0].lastModified)
        assertNull(entities[1].backdrop)
        assertNull(entities[1].cover)
        assertEquals("20", entities[1].categoryId)
    }

    @Test fun `series info episodes as map of season to list`() {
        val info = XtreamJson.decodeFromJsonElement(SeriesInfoResponseDto.serializer(), element("series_info.json"))
        assertEquals(listOf(1, 2), info.episodes.keys.toList())
        assertEquals(listOf(9011L, 9012L), info.episodes[1]!!.map { it.id })
        // season 2 is re-sorted by episode number
        assertEquals(listOf(9020L, 9021L), info.episodes[2]!!.map { it.id })
        assertNull(info.episodes[2]!![0].info) // "info": [] tolerated
        assertEquals(2, info.seasons.size)
        assertEquals(2, info.seasons[1].seasonNumber)
        assertEquals("Dark", info.info!!.name)

        val episodes = info.toEpisodeEntities(profileId = 1, seriesId = 301)
        assertEquals(4, episodes.size)
        val e11 = episodes.first { it.episodeId == 9011L }
        assertEquals(1, e11.season)
        assertEquals(3060L, e11.durationSecs) // from "00:51:00"
        val e12 = episodes.first { it.episodeId == 9012L }
        assertEquals(1, e12.season) // no season field: taken from map key
        assertEquals(2900L, e12.durationSecs)
        assertEquals(3300L, episodes.first { it.episodeId == 9021L }.durationSecs)
    }

    @Test fun `series info episodes as list of lists with empty info and seasons objects`() {
        val info = XtreamJson.decodeFromJsonElement(SeriesInfoResponseDto.serializer(), element("series_info_list.json"))
        assertNull(info.info)
        assertTrue(info.seasons.isEmpty())
        assertEquals(mapOf(1 to listOf(7001L), 2 to listOf(7002L, 7003L)), info.episodes.mapValues { (_, v) -> v.map { it.id } })
    }

    @Test fun `series info with empty episodes array`() {
        val info = XtreamJson.decodeFromString(SeriesInfoResponseDto.serializer(), """{"seasons":[],"info":{"name":"X"},"episodes":[]}""")
        assertTrue(info.episodes.isEmpty())
    }

    @Test fun `short epg decodes base64 and prefers timestamps`() {
        val epg = XtreamJson.decodeFromJsonElement(ShortEpgResponseDto.serializer(), element("short_epg.json"))
        val rows = epg.toEpgEntities(profileId = 1, epgChannelId = "bbcnews.uk")
        assertEquals(2, rows.size)
        assertEquals("BBC News at One", rows[0].title)
        assertEquals("The latest national and international news.", rows[0].description)
        assertEquals(1731157200000L, rows[0].start)
        assertEquals(1731159000000L, rows[0].end)
        assertEquals("World News Today", rows[1].title)
        // second listing has no timestamps -> parsed from "yyyy-MM-dd HH:mm:ss"
        assertEquals(1731159000000L, rows[1].start)
        assertEquals("", rows[1].description)
    }
}
