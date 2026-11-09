package com.awadytv.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secrets (Xtream passwords, the optional Claude API key, the parental PIN hash) live only in
 * EncryptedSharedPreferences. If the device keystore is broken (seen on cheap TV boxes) the file is
 * recreated once; if it still fails, secrets are kept in memory for this session only and never written
 * in plain text.
 */
@Singleton
class SecretStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs: SharedPreferences? by lazy { open() ?: run { context.deleteSharedPreferences(FILE); open() } }
    private val memory = mutableMapOf<String, String>()

    val isPersistent: Boolean get() = prefs != null

    private fun open(): SharedPreferences? = runCatching {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.onFailure { Log.w("SecretStore", "Encrypted storage unavailable: ${it.javaClass.simpleName}") }.getOrNull()

    fun put(key: String, value: String?) {
        val p = prefs
        if (p == null) {
            if (value == null) memory.remove(key) else memory[key] = value
            return
        }
        p.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    fun get(key: String): String? = prefs?.getString(key, null) ?: memory[key]

    fun passwordKey(profileRef: String) = "xtream.password.$profileRef"

    var claudeApiKey: String?
        get() = get(KEY_CLAUDE)?.takeIf { it.isNotBlank() }
        set(value) = put(KEY_CLAUDE, value?.trim()?.takeIf { it.isNotEmpty() })

    val hasParentalPin: Boolean get() = get(KEY_PIN) != null

    fun setParentalPin(pin: String?) = put(KEY_PIN, pin?.let { hash(it) })

    fun checkParentalPin(pin: String): Boolean = get(KEY_PIN)?.let { it == hash(pin) } ?: true

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("awadytv-pin:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val FILE = "awadytv_secrets"
        private const val KEY_CLAUDE = "claude.api_key"
        private const val KEY_PIN = "parental.pin_sha256"
    }
}
