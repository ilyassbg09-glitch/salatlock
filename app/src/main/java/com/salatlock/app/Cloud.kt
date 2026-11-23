package com.salatlock.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Connexion au serveur Supabase (classement + compteur anonyme d'installations).
 * Aucun e-mail, aucun mot de passe : une session anonyme est créée automatiquement.
 * Toutes les fonctions « blocking » doivent être appelées hors du fil principal (voir [bg]).
 */
object Cloud {
    const val URL_BASE = "https://efagehkrrahnjshhvdhp.supabase.co"
    // Clé publique (« publishable ») : faite pour être intégrée dans l'app.
    const val KEY = "sb_publishable_zsoHntdsdW6lfKKK6HHsIw_mWlpSzgc"

    private val pool = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    class CloudError(msg: String) : Exception(msg)

    /** Lance [work] en arrière-plan, puis [done] sur le fil principal avec le résultat ou l'erreur. */
    fun <T> bg(work: () -> T, done: (T?, String?) -> Unit = { _, _ -> }) {
        pool.execute {
            try { val r = work(); main.post { done(r, null) } }
            catch (e: Exception) { main.post { done(null, friendly(e)) } }
        }
    }

    private fun friendly(e: Exception): String = when {
        e is CloudError -> e.message ?: "Erreur"
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
            "Pas de connexion internet"
        else -> "Erreur : ${e.message}"
    }

    private fun sp(ctx: Context) = MemPrefs.of(ctx, "cloud")

    fun installId(ctx: Context): String {
        val s = sp(ctx)
        return s.getString("install_id", null) ?: UUID.randomUUID().toString().also { s.edit().putString("install_id", it).apply() }
    }

    // ---------- HTTP ----------
    private fun http(method: String, path: String, body: String?, token: String?): Pair<Int, String> {
        val c = URL(URL_BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("apikey", KEY)
        c.setRequestProperty("Content-Type", "application/json")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        c.disconnect()
        return code to text
    }

    private fun saveSession(ctx: Context, j: JSONObject) {
        sp(ctx).edit()
            .putString("access", j.getString("access_token"))
            .putString("refresh", j.getString("refresh_token"))
            .putLong("expires", System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000 - 60_000)
            .apply()
    }

    /** Renvoie un jeton valide (création ou renouvellement de la session anonyme si besoin). */
    @Synchronized
    private fun token(ctx: Context): String {
        val s = sp(ctx)
        val access = s.getString("access", null)
        if (access != null && System.currentTimeMillis() < s.getLong("expires", 0)) return access
        val refresh = s.getString("refresh", null)
        if (refresh != null) {
            val (code, txt) = http("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh).toString(), null)
            if (code in 200..299) { saveSession(ctx, JSONObject(txt)); return s.getString("access", "")!! }
            if (code in 500..599) throw CloudError("Serveur indisponible, réessaie plus tard")
        }
        val (code, txt) = http("POST", "/auth/v1/signup", JSONObject().put("data", JSONObject()).toString(), null)
        if (code !in 200..299) throw CloudError("Connexion au classement impossible ($code)")
        saveSession(ctx, JSONObject(txt))
        return s.getString("access", "")!!
    }

    private fun rpc(ctx: Context, fn: String, args: JSONObject = JSONObject(), auth: Boolean = true): String {
        var tok = if (auth) token(ctx) else null
        var (code, txt) = http("POST", "/rest/v1/rpc/$fn", args.toString(), tok)
        if (code == 401 && auth) { // jeton périmé : on recommence une fois
            sp(ctx).edit().remove("access").apply(); tok = token(ctx)
            val r = http("POST", "/rest/v1/rpc/$fn", args.toString(), tok); code = r.first; txt = r.second
        }
        if (code !in 200..299) {
            val msg = try { JSONObject(txt).optString("message") } catch (_: Exception) { "" }
            throw CloudError(if (msg.isNotBlank()) msg.replaceFirstChar { it.uppercase() } else "Erreur serveur ($code)")
        }
        return txt
    }

    // ---------- Profil / classement ----------
    class Profile(val pseudo: String, val friendCode: String, val total: Int, val streak: Int, val best: Int, val rank: Int, val players: Int)
    class Row(val rank: Int, val pseudo: String, val streak: Int, val total: Int, val best: Int, val isMe: Boolean)

    private fun profile(txt: String): Profile? {
        if (txt.isBlank() || txt == "null") return null
        val j = JSONObject(txt)
        return Profile(j.getString("pseudo"), j.getString("friend_code"), j.getInt("total"), j.getInt("streak"),
            j.getInt("best_streak"), j.getInt("rank"), j.getInt("players"))
    }

    private fun rows(txt: String): List<Row> {
        val a = JSONArray(txt)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Row(o.getInt("rank"), o.getString("pseudo"), o.getInt("streak"), o.getInt("total"), o.getInt("best_streak"), o.optBoolean("is_me"))
        }
    }

    fun hasProfile(ctx: Context) = sp(ctx).getBoolean("has_profile", false)

    fun myProfile(ctx: Context): Profile? = profile(rpc(ctx, "my_profile")).also {
        sp(ctx).edit().putBoolean("has_profile", it != null).apply()
    }

    fun setPseudo(ctx: Context, pseudo: String): Profile? {
        val p = profile(rpc(ctx, "set_pseudo", JSONObject().put("p_pseudo", pseudo)))
        sp(ctx).edit().putBoolean("has_profile", p != null).apply()
        // on envoie l'historique local récent pour démarrer avec les bons chiffres
        val prefs = Prefs(ctx)
        val today = java.time.LocalDate.now()
        for (k in 0..2L) for (pr in Prayer.values()) {
            val d = today.minusDays(k).toString()
            if (prefs.isDone(d, pr)) prefs.queuePrayer(d, pr)
        }
        flush(ctx)
        return myProfile(ctx) ?: p
    }

    fun global(ctx: Context): List<Row> = rows(rpc(ctx, "global_leaderboard", JSONObject().put("p_limit", 100)))
    fun friends(ctx: Context): List<Row> = rows(rpc(ctx, "friends_leaderboard"))
    fun addFriend(ctx: Context, code: String): String = rpc(ctx, "add_friend", JSONObject().put("p_code", code)).trim('"')
    fun removeFriend(ctx: Context, pseudo: String) { rpc(ctx, "remove_friend", JSONObject().put("p_pseudo", pseudo)) }

    fun deleteAccount(ctx: Context) {
        rpc(ctx, "delete_my_account")
        sp(ctx).edit().remove("access").remove("refresh").remove("expires").putBoolean("has_profile", false).apply()
        Prefs(ctx).clearQueue()
    }

    /** Envoie les prières et jours de dispense en attente (si un profil existe). */
    fun flush(ctx: Context) {
        val prefs = Prefs(ctx)
        val minDay = java.time.LocalDate.now().minusDays(2)
        if (!hasProfile(ctx)) { // pas de profil : on garde seulement les 3 derniers jours en mémoire
            prefs.queuedPrayers().filter { java.time.LocalDate.parse(it.substringBefore("|")).isBefore(minDay) }.forEach { prefs.unqueuePrayer(it) }
            prefs.queuedExcused().filter { java.time.LocalDate.parse(it).isBefore(minDay) }.forEach { prefs.unqueueExcused(it) }
            return
        }
        for (item in prefs.queuedPrayers()) {
            val (day, pr) = item.split("|")
            if (java.time.LocalDate.parse(day).isBefore(minDay)) { prefs.unqueuePrayer(item); continue }
            rpc(ctx, "record_prayer", JSONObject().put("p_day", day).put("p_prayer", pr))
            prefs.unqueuePrayer(item)
        }
        for (day in prefs.queuedExcused()) {
            if (java.time.LocalDate.parse(day).isBefore(minDay)) { prefs.unqueueExcused(day); continue }
            rpc(ctx, "record_excused", JSONObject().put("p_day", day))
            prefs.unqueueExcused(day)
        }
    }

    /** Compteur anonyme : une fois par jour au maximum. */
    fun dailyPing(ctx: Context) {
        val s = sp(ctx)
        val today = java.time.LocalDate.now().toString()
        if (s.getString("last_ping", "") == today) return
        bg({
            rpc(ctx, "ping", JSONObject().put("p_install", installId(ctx)).put("p_platform", "android")
                .put("p_version", BuildConfig.VERSION_NAME), auth = false)
            s.edit().putString("last_ping", today).apply()
            val prefs = Prefs(ctx)
            if (prefs.isExcused) prefs.queueExcused(today)
            flush(ctx)
        })
    }
}
