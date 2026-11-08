package com.chitfund.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Talks to the hidden app folder in the person's own Google Drive. */
object DriveApi {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val NAME = "chitfund-backup.json"

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    private fun open(url: String, token: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") setRequestProperty("X-HTTP-Method-Override", "PATCH")
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 20000
            readTimeout = 30000
        }

    private fun read(c: HttpURLConnection): String {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw IllegalStateException("Drive error $code")
        return text
    }

    private fun findId(token: String): String? {
        val q = java.net.URLEncoder.encode("name='$NAME'", "UTF-8")
        val c = open("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id)", token)
        val files = JSONObject(read(c)).getJSONArray("files")
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    fun upload(token: String, json: String) {
        val id = findId(token)
        if (id == null) {
            val boundary = "chitfund" + System.currentTimeMillis()
            val meta = JSONObject().put("name", NAME).put("parents", JSONArray().put("appDataFolder")).toString()
            val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
                "--$boundary\r\nContent-Type: application/json\r\n\r\n$json\r\n--$boundary--"
            val c = open("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", token, "POST")
            c.doOutput = true
            c.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            c.outputStream.use { it.write(body.toByteArray()) }
            read(c)
        } else {
            val c = open("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media", token, "PATCH")
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(json.toByteArray()) }
            read(c)
        }
    }

    /** Returns the saved backup text, or null if there is none yet. */
    fun download(token: String): String? {
        val id = findId(token) ?: return null
        return read(open("https://www.googleapis.com/drive/v3/files/$id?alt=media", token))
    }
}

private fun JSONObject.longOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
private fun JSONObject.intOrNull(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null

class BackupViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val prefs = app.getSharedPreferences("backup", Context.MODE_PRIVATE)

    private val _message = MutableStateFlow(statusText())
    val message: StateFlow<String> = _message
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _auto = MutableStateFlow(prefs.getBoolean("auto", false))
    val auto: StateFlow<Boolean> = _auto

    private fun statusText(): String {
        val t = prefs.getLong("last", 0L)
        return if (t == 0L) "No backup yet"
        else "Last backup: " + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(t))
    }

    fun setAuto(on: Boolean) {
        prefs.edit().putBoolean("auto", on).apply()
        _auto.value = on
    }

    fun backup(token: String) = run("Backing up...") {
        DriveApi.upload(token, snapshot())
        prefs.edit().putLong("last", System.currentTimeMillis()).apply()
        statusText()
    }

    fun restore(token: String) = run("Restoring...") {
        val text = DriveApi.download(token) ?: return@run "No backup found in this Google account"
        restoreFrom(text)
        "Restored from Drive"
    }

    fun fail(text: String) {
        _message.value = text
    }

    /** On app open: quietly back up if it is switched on and the last backup is old. */
    fun autoBackupIfDue() {
        if (!prefs.getBoolean("auto", false)) return
        if (System.currentTimeMillis() - prefs.getLong("last", 0L) < 6 * 3600_000L) return
        val ctx = getApplication<Application>()
        Identity.getAuthorizationClient(ctx).authorize(DriveApi.request())
            .addOnSuccessListener { r ->
                val token = r.accessToken
                if (!r.hasResolution() && token != null) backup(token)
            }
    }

    private fun run(start: String, work: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        _message.value = start
        viewModelScope.launch {
            _message.value = try {
                withContext(Dispatchers.IO) { work() }
            } catch (e: Exception) {
                "Failed: " + (e.message ?: "check your internet connection")
            }
            _busy.value = false
        }
    }

    private suspend fun snapshot(): String {
        val root = JSONObject().put("app", "chitfund").put("version", 3)
        root.put("chits", JSONArray().also { a ->
            dao.allChits().forEach { c ->
                a.put(JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("role", c.role); put("members", c.members)
                    put("monthlyAmount", c.monthlyAmount); put("commissionPercent", c.commissionPercent)
                    c.myWonMonth?.let { put("myWonMonth", it) }
                    put("organiserName", c.organiserName); put("organiserMobile", c.organiserMobile)
                    put("place", c.place)
                    c.startMillis?.let { put("startMillis", it) }
                    c.endMillis?.let { put("endMillis", it) }
                })
            }
        })
        root.put("members", JSONArray().also { a ->
            dao.allMembers().forEach { m ->
                a.put(JSONObject().put("id", m.id).put("chitId", m.chitId).put("name", m.name)
                    .put("phone", m.phone).put("place", m.place))
            }
        })
        root.put("auctions", JSONArray().also { a ->
            dao.allAuctions().forEach { x ->
                a.put(JSONObject().apply {
                    put("id", x.id); put("chitId", x.chitId); put("monthNo", x.monthNo); put("bid", x.bid)
                    x.winnerMemberId?.let { put("winnerMemberId", it) }
                })
            }
        })
        root.put("payments", JSONArray().also { a ->
            dao.allPayments().forEach { p ->
                a.put(JSONObject().apply {
                    put("id", p.id); put("chitId", p.chitId); put("memberId", p.memberId)
                    put("monthNo", p.monthNo); put("paid", p.paid)
                    p.paidMillis?.let { put("paidMillis", it) }
                })
            }
        })
        return root.toString()
    }

    private suspend fun restoreFrom(text: String) {
        val root = JSONObject(text)
        require(root.optString("app") == "chitfund") { "This is not a Chit Fund backup" }
        val chits = root.getJSONArray("chits").let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Chit(
                    id = o.getLong("id"), name = o.getString("name"), role = o.getString("role"),
                    members = o.getInt("members"), monthlyAmount = o.getDouble("monthlyAmount"),
                    commissionPercent = o.getDouble("commissionPercent"), myWonMonth = o.intOrNull("myWonMonth"),
                    organiserName = o.optString("organiserName"), organiserMobile = o.optString("organiserMobile"),
                    place = o.optString("place"), startMillis = o.longOrNull("startMillis"),
                    endMillis = o.longOrNull("endMillis")
                )
            }
        }
        val members = root.getJSONArray("members").let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Member(o.getLong("id"), o.getLong("chitId"), o.getString("name"), o.optString("phone"), o.optString("place"))
            }
        }
        val auctions = root.getJSONArray("auctions").let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Auction(o.getLong("id"), o.getLong("chitId"), o.getInt("monthNo"), o.getDouble("bid"), o.longOrNull("winnerMemberId"))
            }
        }
        val payments = root.getJSONArray("payments").let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Payment(o.getLong("id"), o.getLong("chitId"), o.getLong("memberId"), o.getInt("monthNo"), o.getBoolean("paid"), o.longOrNull("paidMillis"))
            }
        }
        dao.replaceAll(chits, members, auctions, payments)
    }
}
