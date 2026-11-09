package com.awadytv.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parental PIN for adult categories. When a PIN is set, adult categories are locked until the PIN is
 * entered; the unlock lasts for the app session only.
 */
@Singleton
class ParentalGate @Inject constructor(private val secrets: SecretStore) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked

    val pinSet: Boolean get() = secrets.hasParentalPin

    /** True when adult content must be hidden/locked right now. */
    fun locked(): Boolean = secrets.hasParentalPin && !_unlocked.value

    fun unlock(pin: String): Boolean {
        val ok = secrets.checkParentalPin(pin)
        if (ok) _unlocked.value = true
        return ok
    }

    fun lock() {
        _unlocked.value = false
    }

    fun setPin(pin: String?) {
        secrets.setParentalPin(pin)
        _unlocked.value = false
    }
}
