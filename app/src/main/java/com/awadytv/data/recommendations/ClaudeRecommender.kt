package com.awadytv.data.recommendations

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.awadytv.core.network.XtreamJson
import com.awadytv.core.security.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional Claude re-ranker (plan 6.3, v1.1). Uses the user's own API key (encrypted on device).
 *
 * Sends only: liked titles (title, year, genre) and the local engine's top candidates (id, title, year,
 * genre). Never credentials, plot text or the server URL. Any failure returns null so the local order is kept.
 * It is only invoked from [RecommendationRepository.refresh], which runs at most every 6h or after a like.
 */
@Singleton
class ClaudeRecommender @Inject constructor(private val secrets: SecretStore) : ReRanker {

    override suspend fun rerank(liked: List<RecItem>, candidates: List<ScoredItem>): List<ScoredItem>? = withContext(Dispatchers.IO) {
        val key = secrets.claudeApiKey ?: return@withContext null
        if (liked.isEmpty() || candidates.size < 2) return@withContext null
        val client = AnthropicOkHttpClient.builder()
            .apiKey(key)
            .maxRetries(1)
            .timeout(Duration.ofSeconds(45))
            .build()
        try {
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(4_000L)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .system(SYSTEM_PROMPT)
                .addUserMessage(buildPrompt(liked, candidates))
                .build()
            val response = client.messages().create(params)
            val stop = response.stopReason().map { it.toString() }.orElse("")
            if (stop == "refusal" || stop == "max_tokens") return@withContext null
            val text = response.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("")
            parseReranking(text, candidates, LIMIT)
        } catch (e: CancellationException) {
            throw e
        } catch (_: RateLimitException) {
            null
        } catch (_: AnthropicServiceException) {
            null
        } catch (_: AnthropicIoException) {
            null
        } catch (_: Exception) {
            null
        } finally {
            client.close()
        }
    }

    companion object {
        /** A small, fast model as the plan asks for; the request is a short ranking task. */
        const val MODEL = "claude-haiku-5-5"
        const val LIMIT = 20

        private const val SYSTEM_PROMPT =
            "You re-rank movie and TV series recommendations for one viewer of a TV app. " +
                "You get the titles they liked and a list of candidates from their provider's catalog. " +
                "Pick the 20 candidates they are most likely to enjoy, best first, using only candidates from the list. " +
                "For each, write one short, specific reason (max 12 words) that references what they liked. " +
                "Reply with JSON only, no prose: {\"picks\":[{\"id\":\"<candidate id>\",\"reason\":\"<reason>\"}]}"

        @Serializable
        private data class Pick(val id: String = "", val reason: String? = null)

        @Serializable
        private data class Picks(val picks: List<Pick> = emptyList())

        private val json = Json(XtreamJson) { }

        internal fun buildPrompt(liked: List<RecItem>, candidates: List<ScoredItem>): String = buildString {
            appendLine("Liked:")
            liked.take(30).forEach { appendLine("- ${describe(it)}") }
            appendLine()
            appendLine("Candidates:")
            candidates.forEach { appendLine("- ${it.item.key} | ${describe(it.item)}") }
        }

        private fun describe(item: RecItem): String {
            val kind = if (item.kind.key == "series") "series" else "movie"
            val genre = item.genres.take(3).joinToString(", ").ifBlank { null }
            return listOfNotNull(item.title.take(80), item.year?.toString(), genre, kind).joinToString(" · ")
        }

        /**
         * Parses the model's JSON (tolerating code fences or stray text around it). Unknown or duplicate ids are
         * dropped; if fewer than [limit] valid picks come back, the rest of the local order fills the shelf.
         * Returns null when nothing usable was returned.
         */
        fun parseReranking(text: String, candidates: List<ScoredItem>, limit: Int = LIMIT): List<ScoredItem>? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val picks = runCatching { json.decodeFromString(Picks.serializer(), text.substring(start, end + 1)).picks }.getOrNull() ?: return null
            val byKey = candidates.associateBy { it.item.key }
            val chosen = LinkedHashMap<String, ScoredItem>()
            picks.forEach { pick ->
                val candidate = byKey[pick.id.trim()] ?: return@forEach
                if (candidate.item.key in chosen) return@forEach
                val reason = pick.reason?.trim()?.take(120)?.ifBlank { null } ?: candidate.reason
                chosen[candidate.item.key] = candidate.copy(reason = reason)
            }
            if (chosen.isEmpty()) return null
            val result = chosen.values.take(limit).toMutableList()
            for (c in candidates) {
                if (result.size >= limit) break
                if (c.item.key !in chosen) result += c
            }
            return result
        }
    }
}
