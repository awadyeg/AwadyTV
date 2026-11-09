package com.awadytv.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeFormat {
    fun clock(epochMs: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

    fun date(epochSec: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochSec * 1000))

    /** "1h 42m" / "42m" / "58s". */
    fun duration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    /** Player clock "1:02:03" / "02:03". */
    fun playback(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0)) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /** Parses Xtream "yyyy-MM-dd HH:mm:ss" (server time zone unknown: treated as UTC, as panels do). */
    fun parseXtreamDateTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)?.time
        }.getOrNull()
    }

    /** Parses XMLTV "20241109120000 +0100". */
    fun parseXmltv(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val v = value.trim()
        return runCatching {
            if (v.length > 14) {
                SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).parse(v)?.time
            } else {
                SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(v)?.time
            }
        }.getOrNull()
    }

    /** Extracts a 4-digit year from "2019-05-02", "2019" or a title like "Heat (1995)". */
    fun year(vararg candidates: String?): Int? {
        val regex = Regex("(19\\d{2}|20\\d{2})")
        for (c in candidates) {
            if (c.isNullOrBlank()) continue
            regex.find(c)?.value?.toIntOrNull()?.let { return it }
        }
        return null
    }

    /** Converts Xtream durations ("01:42:00", "102 min", "6120") to seconds. */
    fun durationSeconds(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val v = value.trim()
        if (v.contains(':')) {
            val parts = v.split(':').mapNotNull { it.trim().toLongOrNull() }
            return when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                else -> null
            }
        }
        val number = Regex("\\d+").find(v)?.value?.toLongOrNull() ?: return null
        return if (v.contains("min", ignoreCase = true) || number < 400) number * 60 else number
    }
}
