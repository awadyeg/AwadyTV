package com.awadytv.data.recommendations

import com.awadytv.domain.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {
    private val now = 1_731_150_000_000L // fixed clock
    private val day = 86_400_000L
    private val engine = RecommendationEngine()

    private fun movie(id: Long, title: String, genres: String, director: String = "", cast: String = "", year: Int? = null, rating: Double? = 7.0, plot: String? = null, addedDaysAgo: Long? = 400, category: String = "10") =
        RecItem(
            kind = ContentKind.MOVIE, id = id, title = title,
            genres = RecommendationEngine.splitList(genres), directors = RecommendationEngine.splitList(director),
            cast = RecommendationEngine.splitList(cast), year = year, plot = plot, rating = rating,
            addedAtSec = addedDaysAgo?.let { (now - it * day) / 1000 }, categoryId = category,
        )

    private fun series(id: Long, title: String, genres: String, rating: Double? = 7.0, plot: String? = null) =
        RecItem(ContentKind.SERIES, id, title, genres = RecommendationEngine.splitList(genres), rating = rating, plot = plot, addedAtSec = (now - 400 * day) / 1000, categoryId = "20")

    private val catalog = listOf(
        movie(1, "Heat", "Action, Crime, Drama", "Michael Mann", "Al Pacino, Robert De Niro, Val Kilmer", 1995, 8.3, "A master thief and a detective clash in Los Angeles heist"),
        movie(2, "Collateral", "Action, Crime, Thriller", "Michael Mann", "Tom Cruise, Jamie Foxx", 2004, 7.5, "A cab driver is held hostage by a hitman in Los Angeles"),
        movie(3, "The Departed", "Crime, Drama, Thriller", "Martin Scorsese", "Leonardo DiCaprio, Matt Damon", 2006, 8.5, "An undercover cop and a mole in the police"),
        movie(4, "Toy Story", "Animation, Comedy, Family", "John Lasseter", "Tom Hanks, Tim Allen", 1995, 8.3, "Toys come to life when humans are away"),
        movie(5, "Finding Nemo", "Animation, Comedy, Family", "Andrew Stanton", "Albert Brooks", 2003, 8.2, "A clownfish searches the ocean for his son"),
        movie(6, "Ronin", "Action, Crime, Thriller", "John Frankenheimer", "Robert De Niro, Jean Reno", 1998, 7.2, "Mercenaries plan a heist of a mysterious case"),
        movie(7, "The Notebook", "Drama, Romance", "Nick Cassavetes", "Ryan Gosling, Rachel McAdams", 2004, 7.8, "A poor young man falls in love with a rich young woman"),
        series(101, "Narcos", "Crime, Drama, Thriller", 8.8, "The rise of drug cartels and the agents hunting them"),
        series(102, "Bluey", "Animation, Family, Kids", 9.3, "A playful puppy and her family"),
    )
    private val index = engine.buildIndex(catalog)

    private fun like(kind: ContentKind, id: Long, daysAgo: Long = 1, explicit: Boolean = true, weight: Double = 1.0) =
        Signal(kind, id, weight, now - daysAgo * day, explicit)

    @Test fun `liked crime movie ranks similar crime titles above family animation`() {
        val out = engine.recommend(index, EngineInput(signals = listOf(like(ContentKind.MOVIE, 1)), nowMs = now))
        assertFalse(out.coldStart)
        val rec = out.shelves.first { it.id == RecommendationEngine.SHELF_RECOMMENDED }.items.map { it.item.title }
        assertEquals("Collateral", rec.first()) // same director, genres, LA plot
        fun rank(title: String) = rec.indexOf(title).let { if (it < 0) Int.MAX_VALUE else it }
        assertTrue(rank("Ronin") < rank("Toy Story"))
        assertTrue(rank("The Departed") < rank("Finding Nemo"))
        assertTrue("family animation must rank below every crime title", rank("Finding Nemo") > rank("Narcos"))
        assertTrue(rec.contains("Narcos")) // mixed movies + series
    }

    @Test fun `liked and watched items are excluded everywhere`() {
        val input = EngineInput(
            signals = listOf(like(ContentKind.MOVIE, 1), like(ContentKind.SERIES, 101, daysAgo = 2)),
            watchedKeys = setOf(RecItem.key(ContentKind.MOVIE, 2)),
            nowMs = now,
        )
        val out = engine.recommend(index, input)
        val all = out.shelves.flatMap { s -> s.items.map { it.item.key } }
        assertFalse(all.contains("movie:1"))
        assertFalse(all.contains("series:101"))
        assertFalse(all.contains("movie:2"))
        assertTrue(all.isNotEmpty())
    }

    @Test fun `blocked keys are never recommended`() {
        val out = engine.recommend(index, EngineInput(listOf(like(ContentKind.MOVIE, 1)), blockedKeys = setOf("movie:2"), nowMs = now))
        assertFalse(out.shelves.flatMap { it.items }.any { it.item.key == "movie:2" })
    }

    @Test fun `because you liked shelves follow the most recent likes`() {
        val input = EngineInput(
            signals = listOf(
                like(ContentKind.MOVIE, 4, daysAgo = 1), // Toy Story most recent
                like(ContentKind.MOVIE, 1, daysAgo = 5), // Heat
                like(ContentKind.MOVIE, 7, daysAgo = 9), // The Notebook
                like(ContentKind.MOVIE, 3, daysAgo = 30), // fourth like: no shelf
            ),
            nowMs = now,
        )
        val because = engine.recommend(index, input).shelves.filter { it.id.startsWith(RecommendationEngine.SHELF_BECAUSE) }
        assertEquals(listOf("Because you liked «Toy Story»", "Because you liked «Heat»", "Because you liked «The Notebook»"), because.map { it.title })
        assertEquals("Finding Nemo", because[0].items.first().item.title)
        assertEquals("Collateral", because[1].items.first().item.title)
    }

    @Test fun `recency decay favours recent likes`() {
        val recentFamily = EngineInput(listOf(like(ContentKind.MOVIE, 4, daysAgo = 1), like(ContentKind.MOVIE, 1, daysAgo = 365)), nowMs = now)
        val rec = engine.recommend(index, recentFamily).shelves.first().items.map { it.item.title }
        assertEquals("Finding Nemo", rec.first())
        val recentCrime = EngineInput(listOf(like(ContentKind.MOVIE, 4, daysAgo = 365), like(ContentKind.MOVIE, 1, daysAgo = 1)), nowMs = now)
        assertEquals("Collateral", engine.recommend(index, recentCrime).shelves.first().items.first().item.title)
    }

    @Test fun `reason names the closest liked title`() {
        val out = engine.recommend(index, EngineInput(listOf(like(ContentKind.MOVIE, 1), like(ContentKind.MOVIE, 4, daysAgo = 3)), nowMs = now))
        val rec = out.shelves.first().items
        assertEquals("Because you liked Heat", rec.first { it.item.title == "Collateral" }.reason)
        assertEquals("Because you liked Toy Story", rec.first { it.item.title == "Finding Nemo" }.reason)
    }

    @Test fun `top rated shelf uses the favourite genre`() {
        val likes = listOf(like(ContentKind.MOVIE, 1), like(ContentKind.MOVIE, 3, daysAgo = 2), like(ContentKind.MOVIE, 6, daysAgo = 3))
        val out = engine.recommend(index, EngineInput(likes, nowMs = now))
        val top = out.shelves.firstOrNull { it.id.startsWith(RecommendationEngine.SHELF_TOP_RATED) }
        assertNotNull(top)
        assertEquals("crime", out.favouriteGenre)
        assertEquals("Top rated in Crime", top!!.title)
        val ratings = top.items.map { it.item.rating!! }
        assertEquals(ratings.sortedDescending(), ratings)
        assertEquals("Narcos", top.items.first().item.title)
    }

    @Test fun `negative implicit signals push similar titles down`() {
        val base = engine.recommend(index, EngineInput(listOf(like(ContentKind.MOVIE, 3)), nowMs = now)).shelves.first().items.map { it.item.title }
        val withDislike = engine.recommend(
            index,
            EngineInput(listOf(like(ContentKind.MOVIE, 3), Signal(ContentKind.MOVIE, 7, -0.5, now - day, explicit = false)), nowMs = now),
        ).shelves.first().items.map { it.item.title }
        assertFalse(withDislike.contains("The Notebook"))
        assertTrue(base.isNotEmpty())
    }

    @Test fun `cold start falls back to top rated and recently added`() {
        val fresh = catalog + movie(8, "Brand New Movie", "Drama", rating = 4.0, addedDaysAgo = 2)
        val out = engine.recommend(engine.buildIndex(fresh), EngineInput(signals = emptyList(), nowMs = now))
        assertTrue(out.coldStart)
        val rec = out.shelves.single().items
        assertEquals("Bluey", rec.first().item.title) // 9.3 rating
        assertTrue(rec.any { it.item.title == "Brand New Movie" && it.reason == "New on your service" })
        val scores = rec.map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test fun `ordering is deterministic for ties`() {
        val twins = listOf(movie(1, "A", "Drama", rating = 7.0), movie(2, "B", "Drama", rating = 7.0), movie(3, "C", "Drama", rating = 7.0))
        val idx = engine.buildIndex(twins)
        val first = engine.recommend(idx, EngineInput(listOf(like(ContentKind.MOVIE, 1)), nowMs = now)).shelves.first().items.map { it.item.key }
        repeat(5) {
            assertEquals(first, engine.recommend(idx, EngineInput(listOf(like(ContentKind.MOVIE, 1)), nowMs = now)).shelves.first().items.map { it.item.key })
        }
        assertEquals(listOf("movie:2", "movie:3"), first)
    }

    @Test fun `unopened categories are penalised and new items get a novelty bonus`() {
        val items = listOf(
            movie(1, "Seed", "Thriller", category = "1"),
            movie(2, "Opened cat", "Thriller", category = "1"),
            movie(3, "Never opened cat", "Thriller", category = "2"),
            movie(4, "New arrival", "Thriller", category = "1", addedDaysAgo = 3),
        )
        val idx = engine.buildIndex(items)
        val out = engine.recommend(idx, EngineInput(listOf(like(ContentKind.MOVIE, 1)), openedCategories = setOf("movie:1"), nowMs = now))
        assertEquals(listOf("New arrival", "Opened cat", "Never opened cat"), out.shelves.first().items.map { it.item.title })
    }

    @Test fun `genres fall back to category names when metadata is missing`() {
        assertEquals(listOf("action", "comedy"), RecommendationEngine.genresFromCategory("VOD | Action & Comedy"))
        assertEquals(listOf("sci-fi"), RecommendationEngine.genresFromCategory("Science Fiction Movies"))
        val items = listOf(
            RecItem(ContentKind.MOVIE, 1, "X", categoryName = "Horror Movies", categoryId = "h"),
            RecItem(ContentKind.MOVIE, 2, "Y", categoryName = "Horror", categoryId = "h2"),
            RecItem(ContentKind.MOVIE, 3, "Z", categoryName = "Kids", categoryId = "k"),
        )
        val similar = engine.similar(engine.buildIndex(items), "movie:1")
        assertEquals(listOf("movie:2"), similar.map { it.item.key })
    }

    @Test fun `similar excludes the source and respects exclusions`() {
        val similar = engine.similar(index, "movie:1", excluded = setOf("movie:2"), limit = 3)
        assertFalse(similar.any { it.item.key == "movie:1" || it.item.key == "movie:2" })
        assertEquals(3, similar.size)
    }
}
