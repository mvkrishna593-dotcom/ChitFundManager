package com.chitfund.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub Releases for a newer build, downloads it and starts the installer. */
object Updater {
    const val REPO = "mvkrishna593-dotcom/ChitFundManager"

    data class Info(val build: Int, val name: String, val url: String)

    /** Build number of the installed app (set from the GitHub run number at build time). */
    fun currentBuild(ctx: Context): Int {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info).toInt()
    }

    suspend fun latest(): Info? = withContext(Dispatchers.IO) {
        try {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return@withContext null
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val build = tag.removePrefix("v").toIntOrNull() ?: return@withContext null
            val assets = json.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    url = a.getString("browser_download_url")
                    break
                }
            }
            if (url == null) return@withContext null
            Info(build, json.optString("name", tag), url)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(ctx.cacheDir, "updates")
                dir.mkdirs()
                val out = File(dir, "ChitFund.apk")
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.instanceFollowRedirects = true
                if (c.responseCode != 200) return@withContext null
                val total = c.contentLengthLong
                c.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(16 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done * 100 / total).toInt()
                                if (p != last) {
                                    last = p
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
                out
            } catch (e: Exception) {
                null
            }
        }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Opens the phone setting "Install unknown apps" for this app. */
    fun openInstallSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, "application/vnd.android.package-archive")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
