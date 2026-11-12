package com.awadytv.data.recommendations

import com.awadytv.domain.ContentKind
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** A catalog item as the engine sees it (movie or series), built from Xtream metadata only. */
data class RecItem(
    val kind: ContentKind,
    val id: Long,
    val title: String,
    val genres: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val year: Int? = null,
    val plot: String? = null,
    /** 0..10 */
    val rating: Double? = null,
    val addedAtSec: Long? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
) {
    val key: String get() = key(kind, id)

    companion object {
        fun key(kind: ContentKind, id: Long) = "${kind.key}:$id"
    }
}

/**
 * A preference signal: explicit like (1.0), implicit like from finishing (0.5),
 * implicit dislike from abandoning (negative).
 */
data class Signal(val kind: ContentKind, val id: Long, val weight: Double, val atMs: Long, val explicit: Boolean) {
    val key: String get() = RecItem.key(kind, id)
}

data class EngineInput(
    val signals: List<Signal>,
    /** Items the user has watched/started (excluded from recommendations). */
    val watchedKeys: Set<String> = emptySet(),
    /** "kind:categoryId" of categories the user has opened at least once. */
    val openedCategories: Set<String> = emptySet(),
    /** Keys that must never be recommended (e.g. adult categories while the parental lock is on). */
    val blockedKeys: Set<String> = emptySet(),
    val nowMs: Long,
)

data class ScoredItem(val item: RecItem, val score: Double, val reason: String?)

data class RecShelf(val id: String, val title: String, val items: List<ScoredItem>)

data class EngineOutput(val shelves: List<RecShelf>, val coldStart: Boolean, val favouriteGenre: String?)

/** Pre-computed, L2-normalised feature vectors for a catalog. Build once, reuse for many queries. */
class CatalogIndex internal constructor(
    val items: List<RecItem>,
    internal val vectors: Map<String, Map<String, Double>>,
) {
    val byKey: Map<String, RecItem> = items.associateBy { it.key }
}

/**
 * Local, content-based recommender (plan 6.2). No network, deterministic, unit-tested.
 *
 * Features per item: genre (weight 3), director (2), top-5 cast (1.5), decade (1), plot keywords by
 * TF-IDF over the catalog (1), plus what IPTV catalogs have for *every* title even without metadata
 * (Xtream movie lists carry no genre, cast or plot until a title's page is opened): the name prefix tags
 * ("AR:", "4K-AR:", "AR-SUBS:" = language / subtitles / quality, 2.5), the category (1.5) and the words of
 * its name ("Turkish dubbed", "Indian", 1.5, shared across movies and series), and title words (1, catches
 * sequels and franchises). Every feature is scaled by a soft IDF so one shared by thousands of titles
 * (e.g. "AR") counts less than a rare one (same director, same franchise).
 * The taste profile is the recency-decayed (`0.5^(days/60)`) weighted sum of liked items' vectors.
 * Candidates are scored by cosine similarity + `0.1 x rating/10` + a novelty bonus for items added in the
 * last 14 days - a penalty for categories the user never opens.
 */
class RecommendationEngine(
    private val recommendedSize: Int = 20,
    private val becauseSize: Int = 12,
    private val becauseShelves: Int = 3,
) {
    fun buildIndex(items: List<RecItem>): CatalogIndex {
        val unique = items.distinctBy { it.key }
        val plotTokens = unique.associate { it.key to tokenize(it.plot) }
        val df = HashMap<String, Int>()
        plotTokens.values.forEach { tokens -> tokens.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = unique.size.coerceAtLeast(1)
        val idf = df.mapValues { (_, d) -> ln((n + 1.0) / (d + 1.0)) + 1.0 }
        val maxIdf = idf.values.maxOrNull() ?: 1.0
        val raw = HashMap<String, HashMap<String, Double>>(unique.size * 2)
        for (item in unique) {
            val v = HashMap<String, Double>()
            val genres = item.genres.ifEmpty { genresFromCategory(item.categoryName) }
            genres.forEach { v.add("g:${norm(it)}", W_GENRE) }
            item.directors.forEach { v.add("d:${norm(it)}", W_DIRECTOR) }
            item.cast.take(5).forEach { v.add("a:${norm(it)}", W_CAST) }
            item.year?.let { v.add("y:${it / 10 * 10}", W_DECADE) }
            item.categoryId?.let { v.add("c:${item.kind.key}:$it", W_CATEGORY) }
            tokenize(item.categoryName).distinct().forEach { v.add("cw:$it", W_CATEGORY_WORDS) }
            val (tags, rest) = titleParts(item.title)
            tags.forEach { v.add("t:$it", W_TAG) }
            tokenize(rest).distinct().take(MAX_TITLE_TERMS).forEach { v.add("w:$it", W_TITLE) }
            val tokens = plotTokens[item.key].orEmpty()
            if (tokens.isNotEmpty()) {
                val tf = tokens.groupingBy { it }.eachCount()
                val maxTf = tf.values.max().toDouble()
                tf.entries
                    .map { (t, c) -> t to (c / maxTf) * ((idf[t] ?: 1.0) / maxIdf) }
                    .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
                    .take(MAX_PLOT_TERMS)
                    .forEach { (t, w) -> v.add("p:$t", W_PLOT * w) }
            }
            raw[item.key] = v
        }
        // Soft IDF over every non-plot feature (plot terms already carry their own TF-IDF).
        val featureDf = HashMap<String, Int>()
        raw.values.forEach { v -> v.keys.forEach { if (!it.startsWith("p:")) featureDf[it] = (featureDf[it] ?: 0) + 1 } }
        val maxFeatureIdf = ln(n + 1.0) + 1.0
        val vectors = HashMap<String, Map<String, Double>>(unique.size * 2)
        for ((key, v) in raw) {
            val scaled = v.mapValues { (term, w) ->
                val d = featureDf[term] ?: return@mapValues w
                w * (0.4 + 0.6 * (ln((n + 1.0) / (d + 1.0)) + 1.0) / maxFeatureIdf)
            }
            vectors[key] = normalise(scaled)
        }
        return CatalogIndex(unique, vectors)
    }

    fun recommend(index: CatalogIndex, input: EngineInput): EngineOutput {
        val positives = input.signals.filter { it.weight > 0 && index.vectors.containsKey(it.key) }
        val signalKeys = input.signals.map { it.key }.toSet()
        val excluded = signalKeys + input.watchedKeys + input.blockedKeys
        val candidates = index.items.filter { it.key !in excluded }
        if (positives.isEmpty()) return coldStart(candidates, input)

        val profile = HashMap<String, Double>()
        for (s in input.signals) {
            val vec = index.vectors[s.key] ?: continue
            val days = ((input.nowMs - s.atMs).coerceAtLeast(0)) / DAY_MS
            val w = s.weight * 0.5.pow(days / HALF_LIFE_DAYS)
            vec.forEach { (t, x) -> profile.add(t, w * x) }
        }
        val profileVec = normalise(profile.filterValues { it > 0 })
        val openedByKind = input.openedCategories.groupBy { it.substringBefore(':') }

        val likedItems = positives.sortedWith(compareByDescending<Signal> { it.atMs }.thenBy { it.key }).mapNotNull { index.byKey[it.key] }
        val scored = candidates.mapNotNull { item ->
            val sim = cosine(profileVec, index.vectors[item.key].orEmpty())
            if (sim <= 0.0) null else ScoredItem(item, sim + bonuses(item, input, openedByKind), null)
        }.sortedWith(compareByDescending<ScoredItem> { it.score }.thenBy { it.item.key })

        val recommended = scored.take(recommendedSize).map { it.copy(reason = reasonFor(index, it.item, likedItems)) }
        val shelves = mutableListOf(RecShelf(SHELF_RECOMMENDED, "Recommended for you", recommended))

        positives.sortedWith(compareByDescending<Signal> { it.explicit }.thenByDescending { it.atMs }.thenBy { it.key })
            .distinctBy { it.key }
            .take(becauseShelves)
            .forEach { s ->
                val source = index.byKey[s.key] ?: return@forEach
                val similar = similar(index, source.key, excluded, becauseSize)
                if (similar.isNotEmpty()) shelves += RecShelf("$SHELF_BECAUSE${source.key}", "Because you liked «${source.title}»", similar)
            }

        val favouriteGenre = profile.entries.filter { it.key.startsWith("g:") }
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key?.removePrefix("g:")
        if (favouriteGenre != null) {
            val top = candidates.filter { item -> item.allGenres().any { norm(it) == favouriteGenre } && (item.rating ?: 0.0) > 0 }
                .sortedWith(compareByDescending<RecItem> { it.rating ?: 0.0 }.thenBy { it.key })
                .take(recommendedSize)
                .map { ScoredItem(it, (it.rating ?: 0.0) / 10, null) }
            if (top.isNotEmpty()) shelves += RecShelf("$SHELF_TOP_RATED$favouriteGenre", "Top rated in ${favouriteGenre.titleCase()}", top)
        }
        return EngineOutput(shelves, coldStart = false, favouriteGenre = favouriteGenre)
    }

    /** Nearest neighbours of a single item ("Because you liked", "More like this"). */
    fun similar(index: CatalogIndex, key: String, excluded: Set<String> = emptySet(), limit: Int = becauseSize): List<ScoredItem> {
        val source = index.vectors[key] ?: return emptyList()
        return index.items.asSequence()
            .filter { it.key != key && it.key !in excluded }
            .map { ScoredItem(it, cosine(source, index.vectors[it.key].orEmpty()) + 0.02 * ((it.rating ?: 0.0) / 10), null) }
            .filter { it.score > 0.03 }
            .sortedWith(compareByDescending<ScoredItem> { it.score }.thenBy { it.item.key })
            .take(limit)
            .toList()
    }

    /** No likes yet: top-rated blended with recently added. */
    private fun coldStart(candidates: List<RecItem>, input: EngineInput): EngineOutput {
        val nowSec = input.nowMs / 1000
        val items = candidates.map { item ->
            val rating = (item.rating ?: 0.0) / 10
            val ageDays = item.addedAtSec?.let { (nowSec - it).coerceAtLeast(0) / 86_400.0 }
            val recency = ageDays?.let { (1.0 - it / 30.0).coerceIn(0.0, 1.0) } ?: 0.0
            val reason = when {
                ageDays != null && ageDays <= 14 -> "New on your service"
                rating >= 0.75 -> "Highly rated"
                else -> null
            }
            ScoredItem(item, 0.7 * rating + 0.3 * recency, reason)
        }.filter { it.score > 0 }
            .sortedWith(compareByDescending<ScoredItem> { it.score }.thenBy { it.item.key })
            .take(recommendedSize)
        return EngineOutput(listOf(RecShelf(SHELF_RECOMMENDED, "Recommended for you", items)), coldStart = true, favouriteGenre = null)
    }

    private fun bonuses(item: RecItem, input: EngineInput, openedByKind: Map<String, List<String>>): Double {
        var bonus = 0.1 * ((item.rating ?: 0.0).coerceIn(0.0, 10.0) / 10)
        val added = item.addedAtSec
        if (added != null && input.nowMs / 1000 - added in 0..NOVELTY_DAYS * 86_400) bonus += NOVELTY_BONUS
        val opened = openedByKind[item.kind.key]
        if (!opened.isNullOrEmpty() && item.categoryId != null && "${item.kind.key}:${item.categoryId}" !in opened) bonus -= UNOPENED_PENALTY
        return bonus
    }

    private fun reasonFor(index: CatalogIndex, item: RecItem, liked: List<RecItem>): String? {
        val vec = index.vectors[item.key] ?: return null
        val best = liked.maxWithOrNull(compareBy<RecItem> { cosine(vec, index.vectors[it.key].orEmpty()) }.thenByDescending { it.key }) ?: return null
        return if (cosine(vec, index.vectors[best.key].orEmpty()) > 0.05) "Because you liked ${best.title}" else null
    }

    private fun RecItem.allGenres(): List<String> = genres.ifEmpty { genresFromCategory(categoryName) }

    companion object {
        const val SHELF_RECOMMENDED = "recommended"
        const val SHELF_BECAUSE = "because:"
        const val SHELF_TOP_RATED = "toprated:"

        const val W_GENRE = 3.0
        const val W_DIRECTOR = 2.0
        const val W_CAST = 1.5
        const val W_DECADE = 1.0
        const val W_PLOT = 1.0
        const val W_CATEGORY = 1.5
        const val W_CATEGORY_WORDS = 1.5
        const val W_TAG = 2.5
        const val W_TITLE = 1.0
        private const val MAX_TITLE_TERMS = 6
        const val HALF_LIFE_DAYS = 60.0
        const val NOVELTY_DAYS = 14L
        const val NOVELTY_BONUS = 0.05
        const val UNOPENED_PENALTY = 0.05
        private const val MAX_PLOT_TERMS = 12
        private const val DAY_MS = 86_400_000.0

        private val knownGenres = listOf(
            "action", "adventure", "animation", "anime", "biography", "comedy", "crime", "documentary", "drama", "family",
            "fantasy", "history", "horror", "kids", "music", "musical", "mystery", "romance", "sci-fi", "science fiction",
            "sport", "thriller", "war", "western",
        )

        private val stopWords = setOf(
            "a", "about", "after", "again", "against", "all", "an", "and", "any", "are", "as", "at", "be", "been", "before",
            "being", "between", "both", "but", "by", "can", "could", "did", "do", "does", "during", "each", "for", "from",
            "further", "had", "has", "have", "he", "her", "here", "hers", "him", "his", "how", "i", "if", "in", "into", "is",
            "it", "its", "just", "more", "most", "must", "new", "no", "not", "now", "of", "off", "on", "once", "one", "only",
            "or", "other", "our", "out", "over", "own", "same", "she", "should", "so", "some", "such", "than", "that", "the",
            "their", "them", "then", "there", "these", "they", "this", "those", "through", "to", "too", "two", "under", "until",
            "up", "very", "was", "we", "were", "what", "when", "where", "which", "while", "who", "whom", "why", "will", "with",
            "would", "you", "your", "life", "find", "finds", "must", "when", "become", "becomes", "world", "story", "film",
        )

        fun splitList(value: String?): List<String> =
            value?.split(',', '/', '|', ';')?.map { it.trim() }?.filter { it.isNotEmpty() && it.lowercase() != "n/a" }.orEmpty()

        fun genresFromCategory(name: String?): List<String> {
            if (name.isNullOrBlank()) return emptyList()
            val lower = name.lowercase()
            return knownGenres.filter { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lower) }
                .map { if (it == "science fiction") "sci-fi" else it }
                .distinct()
        }

        internal fun tokenize(text: String?): List<String> {
            if (text.isNullOrBlank()) return emptyList()
            return text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 && it !in stopWords && !it.all(Char::isDigit) }
        }

        /**
         * Splits an IPTV title into its prefix tags and the rest: "4K-AR: The Odyssey (2026)" -> ([4k, ar],
         * "The Odyssey (2026)"), "AR| News" -> ([ar], "News"). A prefix is a short run (<= 14 chars) of
         * capital letters, digits, '-' or spaces before the first ':' or '|'; anything else has no tags.
         */
        internal fun titleParts(title: String): Pair<List<String>, String> {
            val start = if (title.startsWith("|")) 1 else 0
            val cut = (start until title.length).firstOrNull { title[it] == ':' || title[it] == '|' } ?: -1
            if (cut <= start || cut - start > 14) return emptyList<String>() to title
            val prefix = title.substring(start, cut)
            // Tags are written in capitals ("AR", "4K-AR", "AR-SUBS"); "Mission: Impossible" is a title, not a tag.
            if (!prefix.all { it.isLetterOrDigit() || it == '-' || it == ' ' || it == '_' } || prefix.any { it.isLowerCase() }) return emptyList<String>() to title
            val tags = prefix.split('-', ' ', '_').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            return tags to title.substring(cut + 1).trim()
        }

        internal fun norm(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ").let { if (it == "science fiction") "sci-fi" else it }

        private fun String.titleCase() = split(' ', '-').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

        private fun HashMap<String, Double>.add(term: String, w: Double) {
            this[term] = (this[term] ?: 0.0) + w
        }

        private fun normalise(v: Map<String, Double>): Map<String, Double> {
            val len = sqrt(v.values.sumOf { it * it })
            return if (len == 0.0) emptyMap() else v.mapValues { it.value / len }
        }

        internal fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            val (small, large) = if (a.size <= b.size) a to b else b to a
            var dot = 0.0
            for ((k, x) in small) {
                val y = large[k] ?: continue
                dot += x * y
            }
            return dot // both are unit vectors
        }
    }
}
