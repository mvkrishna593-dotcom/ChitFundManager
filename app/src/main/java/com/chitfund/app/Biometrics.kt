package com.chitfund.app

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Finds the activity behind a Compose context. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Fingerprint or face unlock, using the phone's own screen. */
object Biometrics {
    private const val LEVEL = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun available(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(LEVEL) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(LEVEL)
            .build()
        BiometricPrompt(activity, executor, callback).authenticate(info)
    }
}
