package com.awadytv.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class PreferredFormat { AUTO, M3U8, TS }
enum class DecoderMode { HARDWARE, SOFTWARE }

data class AppSettings(
    val activeProfileId: Long? = null,
    val preferredFormat: PreferredFormat = PreferredFormat.AUTO,
    val decoder: DecoderMode = DecoderMode.HARDWARE,
    val bufferSeconds: Int = 30,
    val subtitleScale: Float = 1f,
    val subtitleBackground: Boolean = true,
    val epgRefreshHours: Int = 6,
    val aiRecommendations: Boolean = true,
    val claudeEnabled: Boolean = false,
    val forceLowEnd: Boolean = false,
    val autoplayNextEpisode: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val activeProfile = longPreferencesKey("active_profile")
        val format = stringPreferencesKey("stream_format")
        val decoder = stringPreferencesKey("decoder")
        val buffer = intPreferencesKey("buffer_seconds")
        val subtitleScale = floatPreferencesKey("subtitle_scale")
        val subtitleBackground = booleanPreferencesKey("subtitle_background")
        val epgHours = intPreferencesKey("epg_refresh_hours")
        val ai = booleanPreferencesKey("ai_recommendations")
        val claude = booleanPreferencesKey("claude_enabled")
        val lowEnd = booleanPreferencesKey("force_low_end")
        val autoplay = booleanPreferencesKey("autoplay_next")
        val lastPlayback = stringPreferencesKey("last_playback")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            activeProfileId = p[Keys.activeProfile],
            preferredFormat = p[Keys.format]?.let { runCatching { PreferredFormat.valueOf(it) }.getOrNull() } ?: PreferredFormat.AUTO,
            decoder = p[Keys.decoder]?.let { runCatching { DecoderMode.valueOf(it) }.getOrNull() } ?: DecoderMode.HARDWARE,
            bufferSeconds = p[Keys.buffer] ?: 30,
            subtitleScale = p[Keys.subtitleScale] ?: 1f,
            subtitleBackground = p[Keys.subtitleBackground] ?: true,
            epgRefreshHours = p[Keys.epgHours] ?: 6,
            aiRecommendations = p[Keys.ai] ?: true,
            claudeEnabled = p[Keys.claude] ?: false,
            forceLowEnd = p[Keys.lowEnd] ?: false,
            autoplayNextEpisode = p[Keys.autoplay] ?: true,
        )
    }.distinctUntilChanged()

    val activeProfileId: Flow<Long?> = settings.map { it.activeProfileId }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun setActiveProfile(id: Long?) = context.settingsStore.edit { if (id == null) it.remove(Keys.activeProfile) else it[Keys.activeProfile] = id }
    suspend fun setPreferredFormat(v: PreferredFormat) = context.settingsStore.edit { it[Keys.format] = v.name }
    suspend fun setDecoder(v: DecoderMode) = context.settingsStore.edit { it[Keys.decoder] = v.name }
    suspend fun setBufferSeconds(v: Int) = context.settingsStore.edit { it[Keys.buffer] = v }
    suspend fun setSubtitleScale(v: Float) = context.settingsStore.edit { it[Keys.subtitleScale] = v }
    suspend fun setSubtitleBackground(v: Boolean) = context.settingsStore.edit { it[Keys.subtitleBackground] = v }
    suspend fun setEpgRefreshHours(v: Int) = context.settingsStore.edit { it[Keys.epgHours] = v }
    suspend fun setAiRecommendations(v: Boolean) = context.settingsStore.edit { it[Keys.ai] = v }
    suspend fun setClaudeEnabled(v: Boolean) = context.settingsStore.edit { it[Keys.claude] = v }
    suspend fun setForceLowEnd(v: Boolean) = context.settingsStore.edit { it[Keys.lowEnd] = v }
    suspend fun setAutoplayNext(v: Boolean) = context.settingsStore.edit { it[Keys.autoplay] = v }

    /** User order of the category rail ("live", "movie", "series"); ids not listed keep provider order after these. */
    fun categoryOrder(profileId: Long, kind: String): Flow<List<String>> =
        context.settingsStore.data.map { p -> p[orderKey(profileId, kind)]?.split(ORDER_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty() }
            .distinctUntilChanged()

    suspend fun setCategoryOrder(profileId: Long, kind: String, ids: List<String>) =
        context.settingsStore.edit { it[orderKey(profileId, kind)] = ids.joinToString(ORDER_SEPARATOR) }

    private fun orderKey(profileId: Long, kind: String) = stringPreferencesKey("category_order_${profileId}_$kind")

    /**
     * The VOD title on screen ("movie:<id>" or "episode:<id>:<seriesId>"), kept while the player is open and
     * cleared when the user leaves it, so a relaunch after the app was closed mid-film resumes playback.
     */
    suspend fun lastPlayback(): String? = context.settingsStore.data.first()[Keys.lastPlayback]
    suspend fun setLastPlayback(v: String?) = context.settingsStore.edit { if (v == null) it.remove(Keys.lastPlayback) else it[Keys.lastPlayback] = v }

    private companion object {
        /** Separator that never appears in provider category ids. */
        const val ORDER_SEPARATOR = "|/|"
    }
}
