package com.awadytv.data.recommendations

import com.awadytv.domain.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeRecommenderTest {
    private val candidates = (1L..5L).map { ScoredItem(RecItem(ContentKind.MOVIE, it, "Movie $it", genres = listOf("Drama"), year = 2000 + it.toInt(), plot = "secret plot"), 1.0 / it, "local $it") }

    @Test fun `parses picks, drops unknown and duplicate ids, fills from local order`() {
        val text = """```json
            {"picks":[{"id":"movie:3","reason":"Slow-burn like Dark"},{"id":"movie:99","reason":"x"},{"id":"movie:3","reason":"dup"},{"id":"movie:1"}]}
            ```"""
        val out = ClaudeRecommender.parseReranking(text, candidates, limit = 4)!!
        assertEquals(listOf("movie:3", "movie:1", "movie:2", "movie:4"), out.map { it.item.key })
        assertEquals("Slow-burn like Dark", out[0].reason)
        assertEquals("local 1", out[1].reason) // no reason given -> keeps local reason
    }

    @Test fun `garbage or empty picks keep local order`() {
        assertNull(ClaudeRecommender.parseReranking("I can't help with that", candidates))
        assertNull(ClaudeRecommender.parseReranking("""{"picks":[{"id":"movie:42"}]}""", candidates))
    }

    @Test fun `prompt contains titles years and genres but never plots`() {
        val liked = listOf(RecItem(ContentKind.SERIES, 7, "Dark", genres = listOf("Mystery"), year = 2017, plot = "time travel secret"))
        val prompt = ClaudeRecommender.buildPrompt(liked, candidates)
        assertTrue(prompt.contains("Dark · 2017 · Mystery · series"))
        assertTrue(prompt.contains("movie:2 | Movie 2 · 2002 · Drama · movie"))
        assertFalse(prompt.contains("plot"))
        assertFalse(prompt.contains("secret"))
    }
}
