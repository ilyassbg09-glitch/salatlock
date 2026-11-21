package com.salatlock.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vérifie sur le site s'il existe une nouvelle version de l'APK.
 * Le site publie un fichier version.json : {"versionCode":2,"versionName":"1.1","url":"…","notes":"…"}
 */
object UpdateChecker {
    class Update(val versionName: String, val url: String, val notes: String)

    private const val EVERY_MS = 6 * 3600_000L

    fun cached(ctx: Context): Update? {
        val sp = ctx.getSharedPreferences("update", Context.MODE_PRIVATE)
        val code = sp.getInt("code", 0)
        if (code <= BuildConfig.VERSION_CODE) return null
        return Update(sp.getString("name", "") ?: "", sp.getString("url", "") ?: "", sp.getString("notes", "") ?: "")
    }

    /** À appeler depuis l'écran d'accueil ; [onFound] est rappelé sur le fil principal. */
    fun check(ctx: Context, force: Boolean = false, onFound: (Update) -> Unit) {
        val app = ctx.applicationContext
        val sp = app.getSharedPreferences("update", Context.MODE_PRIVATE)
        if (BuildConfig.SITE_URL.contains("REPLACE_ME")) return
        if (!force && System.currentTimeMillis() - sp.getLong("last", 0) < EVERY_MS) return
        Thread {
            try {
                val c = URL("${BuildConfig.SITE_URL}/version.json?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val j = JSONObject(c.inputStream.bufferedReader().readText())
                c.disconnect()
                val url = j.optString("url").ifBlank { "${BuildConfig.SITE_URL}/SalatLock.apk" }
                sp.edit().putLong("last", System.currentTimeMillis())
                    .putInt("code", j.getInt("versionCode"))
                    .putString("name", j.optString("versionName"))
                    .putString("url", url)
                    .putString("notes", j.optString("notes")).apply()
                cached(app)?.let { u -> android.os.Handler(android.os.Looper.getMainLooper()).post { onFound(u) } }
            } catch (_: Exception) {
                // Pas d'internet : on réessaiera plus tard.
            }
        }.start()
    }
}
