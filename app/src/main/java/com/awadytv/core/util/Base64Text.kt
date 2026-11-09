package com.awadytv.core.util

import okio.ByteString.Companion.decodeBase64

/**
 * Xtream EPG titles/descriptions arrive base64 encoded (some panels send plain text instead).
 * Decodes when the payload is valid base64 *and* decodes to readable UTF-8; otherwise returns the input.
 */
object Base64Text {
    private val base64Chars = Regex("^[A-Za-z0-9+/=_\\-\\s]+$")

    fun decode(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val trimmed = value.trim()
        if (!base64Chars.matches(trimmed)) return trimmed
        val decoded = trimmed.replace("\\s".toRegex(), "").decodeBase64()?.utf8() ?: return trimmed
        return if (decoded.isReadable()) decoded.trim() else trimmed
    }

    private fun String.isReadable(): Boolean {
        if (isEmpty()) return false
        if (contains('�')) return false
        val controls = count { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
        return controls == 0
    }
}
