package com.awadytv.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class Base64TextTest {
    @Test fun `decodes base64 epg titles`() {
        assertEquals("BBC News at One", Base64Text.decode("QkJDIE5ld3MgYXQgT25l"))
    }

    @Test fun `decodes utf8 content`() {
        // "Café – Noticias" in UTF-8
        assertEquals("Café – Noticias", Base64Text.decode("Q2Fmw6kg4oCTIE5vdGljaWFz"))
    }

    @Test fun `plain text passes through`() {
        assertEquals("Match of the Day", Base64Text.decode("Match of the Day"))
        assertEquals("", Base64Text.decode(null))
        assertEquals("", Base64Text.decode("   "))
    }

    @Test fun `plain word that is technically base64 but not readable stays as is`() {
        assertEquals("News", Base64Text.decode("News"))
    }
}

class TimeFormatTest {
    @Test fun `parses durations in all Xtream shapes`() {
        assertEquals(10224L, TimeFormat.durationSeconds("02:50:24"))
        assertEquals(3060L, TimeFormat.durationSeconds("51:00"))
        assertEquals(6120L, TimeFormat.durationSeconds("102 min"))
        assertEquals(3600L, TimeFormat.durationSeconds("60"))
        assertEquals(6120L, TimeFormat.durationSeconds("6120"))
        assertEquals(null, TimeFormat.durationSeconds(""))
    }

    @Test fun `extracts years`() {
        assertEquals(1995, TimeFormat.year(null, "", "Heat (1995)"))
        assertEquals(2017, TimeFormat.year("2017-12-01"))
        assertEquals(null, TimeFormat.year("n/a"))
    }

    @Test fun `parses xtream and xmltv timestamps`() {
        assertEquals(1731157200000L, TimeFormat.parseXtreamDateTime("2024-11-09 13:00:00"))
        assertEquals(1731157200000L, TimeFormat.parseXmltv("20241109140000 +0100"))
        assertEquals(1731157200000L, TimeFormat.parseXmltv("20241109130000"))
    }

    @Test fun `formats playback clock`() {
        assertEquals("02:03", TimeFormat.playback(123_000))
        assertEquals("1:02:03", TimeFormat.playback(3_723_000))
    }
}
