package com.chitfund.app

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Keeps the PIN and the security answer on this phone. Neither is stored as plain text:
 * only a salted, slowed-down fingerprint (PBKDF2) of each is saved.
 */
class SecurityStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("security", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.contains("pin_hash")

    var lockEnabled: Boolean
        get() = hasPin() && prefs.getBoolean("lock_on", false)
        set(v) { prefs.edit().putBoolean("lock_on", v).apply() }

    var biometricOn: Boolean
        get() = lockEnabled && prefs.getBoolean("bio_on", false)
        set(v) { prefs.edit().putBoolean("bio_on", v).apply() }

    val question: String?
        get() = prefs.getString("question", null)

    fun setPin(pin: String) = putSecret("pin", pin)
    fun checkPin(pin: String): Boolean = checkSecret("pin", pin)

    fun setQuestion(q: String, answer: String) {
        putSecret("ans", normalize(answer))
        prefs.edit().putString("question", q).apply()
    }

    fun checkAnswer(answer: String): Boolean = checkSecret("ans", normalize(answer))

    /** Seconds left before another try is allowed, or 0. */
    fun lockedSeconds(name: String): Long {
        val leftMs = prefs.getLong(name + "_until", 0L) - System.currentTimeMillis()
        return if (leftMs > 0) (leftMs + 999) / 1000 else 0L
    }

    /** Counts a wrong try. Returns the tries left, or 0 when the wait has just started. */
    fun recordFailure(name: String, maxTries: Int, lockMs: Long): Int {
        val count = prefs.getInt(name + "_fails", 0) + 1
        return if (count >= maxTries) {
            prefs.edit()
                .putInt(name + "_fails", 0)
                .putLong(name + "_until", System.currentTimeMillis() + lockMs)
                .apply()
            0
        } else {
            prefs.edit().putInt(name + "_fails", count).apply()
            maxTries - count
        }
    }

    fun recordSuccess(name: String) {
        prefs.edit().putInt(name + "_fails", 0).putLong(name + "_until", 0L).apply()
    }

    private fun normalize(s: String): String =
        s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun hash(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 20000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    private fun putSecret(name: String, secret: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        prefs.edit()
            .putString(name + "_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(name + "_hash", Base64.encodeToString(hash(secret, salt), Base64.NO_WRAP))
            .apply()
    }

    private fun checkSecret(name: String, secret: String): Boolean {
        val saltText = prefs.getString(name + "_salt", null) ?: return false
        val hashText = prefs.getString(name + "_hash", null) ?: return false
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val expected = Base64.decode(hashText, Base64.NO_WRAP)
        return MessageDigest.isEqual(expected, hash(secret, salt))
    }
}

/** ok = correct. Otherwise either triesLeft or lockedSeconds says what happens next. */
data class CheckResult(val ok: Boolean, val triesLeft: Int = 0, val lockedSeconds: Long = 0L)

class SecurityViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecurityStore(app)

    private val _lockEnabled = MutableStateFlow(store.lockEnabled)
    val lockEnabled: StateFlow<Boolean> = _lockEnabled

    private val _biometricOn = MutableStateFlow(store.biometricOn)
    val biometricOn: StateFlow<Boolean> = _biometricOn

    private val _hasPin = MutableStateFlow(store.hasPin())
    val hasPin: StateFlow<Boolean> = _hasPin

    private val _question = MutableStateFlow(store.question)
    val question: StateFlow<String?> = _question

    /** True from the moment the app starts until the PIN (or fingerprint) is accepted. */
    private val _locked = MutableStateFlow(store.lockEnabled)
    val locked: StateFlow<Boolean> = _locked

    fun unlock() {
        _locked.value = false
    }

    private fun refresh() {
        _lockEnabled.value = store.lockEnabled
        _biometricOn.value = store.biometricOn
        _hasPin.value = store.hasPin()
        _question.value = store.question
    }

    fun checkPin(pin: String): CheckResult = check("pin", 5, 30_000L) { store.checkPin(pin) }

    fun checkAnswer(answer: String): CheckResult =
        check("ans", 5, 30 * 60_000L) { store.checkAnswer(answer) }

    private fun check(name: String, maxTries: Int, lockMs: Long, test: () -> Boolean): CheckResult {
        val wait = store.lockedSeconds(name)
        if (wait > 0) return CheckResult(false, 0, wait)
        if (test()) {
            store.recordSuccess(name)
            return CheckResult(true)
        }
        val left = store.recordFailure(name, maxTries, lockMs)
        return if (left == 0) CheckResult(false, 0, store.lockedSeconds(name)) else CheckResult(false, left)
    }

    fun setPin(pin: String) {
        store.setPin(pin)
        store.lockEnabled = true
        refresh()
    }

    /** Used after the security question was answered correctly. */
    fun finishReset(pin: String) {
        setPin(pin)
        store.recordSuccess("pin")
        store.recordSuccess("ans")
    }

    fun saveQuestion(question: String, answer: String) {
        store.setQuestion(question, answer)
        refresh()
    }

    fun setLockEnabled(on: Boolean) {
        store.lockEnabled = on
        if (!on) store.biometricOn = false
        refresh()
    }

    fun setBiometric(on: Boolean) {
        store.biometricOn = on
        refresh()
    }
}
