package com.awadytv.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Credentials for one Xtream Codes account. [toString] is redacted so it can never leak into logs. */
data class XtreamCredentials(val server: String, val username: String, val password: String) {
    override fun toString(): String = "XtreamCredentials(server=$server, username=***, password=***)"
}

enum class StreamFormat(val extension: String) { M3U8("m3u8"), TS("ts") }

/**
 * Server URL normalisation and stream URL construction (plan section 3).
 */
object XtreamUrls {
    private val strippedEndpoints = setOf("player_api.php", "get.php", "xmltv.php", "panel_api.php")

    /**
     * Normalises what the user typed: adds http:// when no scheme, drops query/fragment, strips a pasted
     * `/player_api.php` or `/get.php` (and friends) and trailing slashes, keeps an explicit port and sub-path.
     * Returns null when the input is not a usable http(s) URL.
     */
    fun normalizeServer(input: String): String? {
        var value = input.trim().replace(" ", "")
        if (value.isEmpty()) return null
        val schemeMatch = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://").find(value)
        if (schemeMatch == null) {
            value = "http://$value"
        } else {
            val scheme = schemeMatch.groupValues[1].lowercase()
            if (scheme != "http" && scheme != "https") return null
            value = scheme + value.substring(schemeMatch.groupValues[1].length)
        }
        val url = value.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank()) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        if (segments.isNotEmpty() && segments.last().lowercase() in strippedEndpoints) segments.removeAt(segments.lastIndex)
        val builder = HttpUrl.Builder().scheme(url.scheme).host(url.host).port(url.port)
        segments.forEach { builder.addPathSegment(it) }
        return builder.build().toString().trimEnd('/')
    }

    fun playerApi(server: String): String = "$server/player_api.php"

    fun live(c: XtreamCredentials, streamId: Long, format: StreamFormat): String =
        stream(c, "live", "$streamId.${format.extension}")

    fun movie(c: XtreamCredentials, streamId: Long, containerExtension: String?): String =
        stream(c, "movie", "$streamId.${containerExtension.orDefault("mp4")}")

    fun episode(c: XtreamCredentials, episodeId: Long, containerExtension: String?): String =
        stream(c, "series", "$episodeId.${containerExtension.orDefault("mp4")}")

    fun xmltv(c: XtreamCredentials): String =
        c.server.toHttpUrlOrNull()!!.newBuilder()
            .addPathSegment("xmltv.php")
            .addQueryParameter("username", c.username)
            .addQueryParameter("password", c.password)
            .build().toString()

    /** Picks the live format: the user's preference if the account allows it, else whatever the account allows. */
    fun liveFormat(preferred: StreamFormat?, allowed: List<String>): StreamFormat {
        val allowedLower = allowed.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        if (preferred != null && (allowedLower.isEmpty() || preferred.extension in allowedLower)) return preferred
        return when {
            allowedLower.isEmpty() || "m3u8" in allowedLower -> StreamFormat.M3U8
            "ts" in allowedLower -> StreamFormat.TS
            else -> StreamFormat.M3U8
        }
    }

    /** Replaces credentials in any URL-ish text, for safe logging/error messages. */
    fun redact(text: String, c: XtreamCredentials): String {
        var out = text
        if (c.password.isNotEmpty()) out = out.replace(c.password, "***")
        if (c.username.isNotEmpty()) out = out.replace(c.username, "***")
        return out
    }

    private fun stream(c: XtreamCredentials, type: String, file: String): String {
        val base = c.server.toHttpUrlOrNull() ?: error("Invalid server URL")
        return base.newBuilder()
            .addPathSegment(type)
            .addPathSegment(c.username)
            .addPathSegment(c.password)
            .addPathSegment(file)
            .build()
            .toString()
    }

    private fun String?.orDefault(default: String): String =
        this?.trim()?.trimStart('.')?.takeIf { it.isNotEmpty() && it.all { ch -> ch.isLetterOrDigit() } } ?: default
}
