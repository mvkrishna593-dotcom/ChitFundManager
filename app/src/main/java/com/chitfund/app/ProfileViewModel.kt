package com.chitfund.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class Profile(
    val name: String = "",
    val email: String = "",
    val mobile: String = "",
    val photoPath: String? = null
)

/** Keeps the user's profile on this phone (name, email, mobile and photo). */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<Profile> = _profile

    private fun read() = Profile(
        name = prefs.getString("name", "") ?: "",
        email = prefs.getString("email", "") ?: "",
        mobile = prefs.getString("mobile", "") ?: "",
        photoPath = prefs.getString("photo", null)?.takeIf { File(it).exists() }
    )

    fun save(name: String, email: String, mobile: String) {
        prefs.edit()
            .putString("name", name)
            .putString("email", email)
            .putString("mobile", mobile)
            .apply()
        _profile.value = _profile.value.copy(name = name, email = email, mobile = mobile)
    }

    /** Copies the chosen picture into the app's private folder, scaled down to save space. */
    fun setPhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return@launch
                val file = File(ctx.filesDir, "profile_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                val old = prefs.getString("photo", null)
                prefs.edit().putString("photo", file.absolutePath).apply()
                _profile.value = _profile.value.copy(photoPath = file.absolutePath)
                if (old != null) File(old).delete()
            } catch (e: Exception) {
                // Keep the old photo if the new one cannot be read.
            }
        }
    }
}
