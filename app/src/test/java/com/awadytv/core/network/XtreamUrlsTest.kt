package com.awadytv.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamUrlsTest {
    @Test fun `adds http scheme when missing`() {
        assertEquals("http://provider.example:8080", XtreamUrls.normalizeServer("provider.example:8080"))
    }

    @Test fun `keeps https and explicit port`() {
        assertEquals("https://provider.example:8443", XtreamUrls.normalizeServer("https://provider.example:8443/"))
    }

    @Test fun `strips trailing slashes and whitespace`() {
        assertEquals("http://provider.example", XtreamUrls.normalizeServer("  http://provider.example///  "))
    }

    @Test fun `strips pasted player_api php with query`() {
        assertEquals(
            "http://provider.example:8080",
            XtreamUrls.normalizeServer("http://provider.example:8080/player_api.php?username=a&password=b"),
        )
    }

    @Test fun `strips pasted get php m3u link`() {
        assertEquals(
            "http://provider.example:25461",
            XtreamUrls.normalizeServer("provider.example:25461/get.php?username=a&password=b&type=m3u_plus&output=ts"),
        )
    }

    @Test fun `keeps sub path of panels hosted below root`() {
        assertEquals("http://provider.example/iptv", XtreamUrls.normalizeServer("http://provider.example/iptv/player_api.php"))
    }

    @Test fun `lowercases scheme and host`() {
        assertEquals("http://provider.example:8080", XtreamUrls.normalizeServer("HTTP://Provider.Example:8080"))
    }

    @Test fun `rejects garbage and unsupported schemes`() {
        assertNull(XtreamUrls.normalizeServer(""))
        assertNull(XtreamUrls.normalizeServer("   "))
        assertNull(XtreamUrls.normalizeServer("rtmp://provider.example"))
        assertNull(XtreamUrls.normalizeServer("http://"))
    }

    private val creds = XtreamCredentials("http://provider.example:8080", "demo", "secret")

    @Test fun `builds live stream urls`() {
        assertEquals("http://provider.example:8080/live/demo/secret/101.m3u8", XtreamUrls.live(creds, 101, StreamFormat.M3U8))
        assertEquals("http://provider.example:8080/live/demo/secret/101.ts", XtreamUrls.live(creds, 101, StreamFormat.TS))
    }

    @Test fun `builds movie and episode urls with container extension`() {
        assertEquals("http://provider.example:8080/movie/demo/secret/5001.mkv", XtreamUrls.movie(creds, 5001, "mkv"))
        assertEquals("http://provider.example:8080/movie/demo/secret/5002.mp4", XtreamUrls.movie(creds, 5002, null))
        assertEquals("http://provider.example:8080/movie/demo/secret/5003.mp4", XtreamUrls.movie(creds, 5003, ".mp4"))
        assertEquals("http://provider.example:8080/series/demo/secret/9011.mkv", XtreamUrls.episode(creds, 9011, "mkv"))
    }

    @Test fun `encodes special characters in credentials`() {
        val c = XtreamCredentials("http://provider.example", "user name", "p/ss#?")
        val url = XtreamUrls.live(c, 1, StreamFormat.TS)
        assertEquals("http://provider.example/live/user%20name/p%2Fss%23%3F/1.ts", url)
    }

    @Test fun `stream urls work under a sub path`() {
        val c = XtreamCredentials("http://provider.example/iptv", "demo", "secret")
        assertEquals("http://provider.example/iptv/series/demo/secret/1.mp4", XtreamUrls.episode(c, 1, "mp4"))
    }

    @Test fun `player api and xmltv endpoints`() {
        assertEquals("http://provider.example:8080/player_api.php", XtreamUrls.playerApi(creds.server))
        assertEquals("http://provider.example:8080/xmltv.php?username=demo&password=secret", XtreamUrls.xmltv(creds))
    }

    @Test fun `live format honours preference and allowed formats`() {
        assertEquals(StreamFormat.M3U8, XtreamUrls.liveFormat(null, listOf("m3u8", "ts")))
        assertEquals(StreamFormat.TS, XtreamUrls.liveFormat(null, listOf("ts", "rtmp")))
        assertEquals(StreamFormat.TS, XtreamUrls.liveFormat(StreamFormat.TS, listOf("m3u8", "ts")))
        assertEquals(StreamFormat.TS, XtreamUrls.liveFormat(StreamFormat.M3U8, listOf("ts")))
        assertEquals(StreamFormat.M3U8, XtreamUrls.liveFormat(null, emptyList()))
    }

    @Test fun `credentials never appear in toString or redacted text`() {
        assertFalse(creds.toString().contains("secret"))
        val redacted = XtreamUrls.redact(XtreamUrls.movie(creds, 1, "mp4"), creds)
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("demo"))
        assertTrue(redacted.contains("***"))
    }
}
