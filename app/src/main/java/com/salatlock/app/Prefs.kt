package com.salatlock.app

import android.content.Context
import java.time.LocalDate

/** Tous les réglages et l'état de l'app, stockés sur le téléphone. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("salat", Context.MODE_PRIVATE)

    private fun dbl(k: String) = sp.getString(k, null)?.toDoubleOrNull() ?: Double.NaN

    var lat: Double
        get() = dbl("lat")
        set(v) { sp.edit().putString("lat", v.toString()).apply() }
    var lng: Double
        get() = dbl("lng")
        set(v) { sp.edit().putString("lng", v.toString()).apply() }
    val hasLocation get() = !lat.isNaN() && !lng.isNaN()
    var cityLabel: String
        get() = sp.getString("city", "") ?: ""
        set(v) { sp.edit().putString("city", v).apply() }

    var method: String
        get() = sp.getString("method", "MWL") ?: "MWL"
        set(v) { sp.edit().putString("method", v).apply() }
    var hanafi: Boolean
        get() = sp.getBoolean("hanafi", false)
        set(v) { sp.edit().putBoolean("hanafi", v).apply() }

    var masterEnabled: Boolean
        get() = sp.getBoolean("master", true)
        set(v) { sp.edit().putBoolean("master", v).apply() }
    /** Minutes après l'adhan avant que le téléphone se bloque. */
    var graceMin: Int
        get() = sp.getInt("grace", 15)
        set(v) { sp.edit().putInt("grace", v).apply() }
    /** Durée du compte à rebours de prière (minutes). */
    var countdownMin: Int
        get() = sp.getInt("countdown", 10)
        set(v) { sp.edit().putInt("countdown", v).apply() }
    /** 0..100 : plus c'est haut, plus la détection du lavabo est facile. */
    var sensitivity: Int
        get() = sp.getInt("sens", 50)
        set(v) { sp.edit().putInt("sens", v).apply() }

    fun isPrayerEnabled(p: Prayer) = sp.getBoolean("en_${p.name}", true)
    fun setPrayerEnabled(p: Prayer, v: Boolean) { sp.edit().putBoolean("en_${p.name}", v).apply() }

    // Mode dispense (règles, maladie, voyage…)
    var excusedFrom: Long
        get() = sp.getLong("exc_from", 0)
        set(v) { sp.edit().putLong("exc_from", v).apply() }
    var excusedUntil: Long
        get() = sp.getLong("exc_until", 0)
        set(v) { sp.edit().putLong("exc_until", v).apply() }
    val isExcused get() = System.currentTimeMillis() < excusedUntil

    // ---- État du verrou ----
    var lockPrayer: Prayer?
        get() = Prayer.of(sp.getString("lock_p", null))
        set(v) { sp.edit().putString("lock_p", v?.name).commit() }
    var lockDay: String?
        get() = sp.getString("lock_d", null)
        set(v) { sp.edit().putString("lock_d", v).commit() }
    var lockSince: Long
        get() = sp.getLong("lock_since", 0)
        set(v) { sp.edit().putLong("lock_since", v).commit() }
    var lockIsTest: Boolean
        get() = sp.getBoolean("lock_test", false)
        set(v) { sp.edit().putBoolean("lock_test", v).commit() }

    var countdownEnd: Long
        get() = sp.getLong("cd_end", 0)
        set(v) { sp.edit().putLong("cd_end", v).commit() }
    var countdownPrayer: Prayer?
        get() = Prayer.of(sp.getString("cd_p", null))
        set(v) { sp.edit().putString("cd_p", v?.name).commit() }
    var countdownDay: String?
        get() = sp.getString("cd_d", null)
        set(v) { sp.edit().putString("cd_d", v).commit() }
    var countdownIsTest: Boolean
        get() = sp.getBoolean("cd_test", false)
        set(v) { sp.edit().putBoolean("cd_test", v).commit() }

    val isLockedOrPraying get() = lockPrayer != null || countdownEnd > 0

    fun clearLock() {
        sp.edit().remove("lock_p").remove("lock_d").remove("lock_since").remove("lock_test").commit()
    }
    fun clearCountdown() {
        sp.edit().remove("cd_end").remove("cd_p").remove("cd_d").remove("cd_test").commit()
    }

    // ---- Historique ----
    fun isDone(day: String, p: Prayer) = sp.getBoolean("done_${day}_${p.name}", false)
    fun markDone(day: String, p: Prayer) {
        if (isDone(day, p)) return
        sp.edit().putBoolean("done_${day}_${p.name}", true).putInt("total", total + 1).commit()
        queuePrayer(day, p)
    }
    val total get() = sp.getInt("total", 0)

    private fun allDone(d: LocalDate): Boolean {
        val en = Prayer.values().filter { isPrayerEnabled(it) }
        return en.isNotEmpty() && en.all { isDone(d.toString(), it) }
    }

    private fun wasExcused(d: LocalDate): Boolean {
        if (excusedFrom == 0L) return false
        val start = java.time.Instant.ofEpochMilli(excusedFrom).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val end = java.time.Instant.ofEpochMilli(excusedUntil).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return !d.isBefore(start) && !d.isAfter(end)
    }

    /** Nombre de jours d'affilée où toutes les prières ont été faites. */
    fun streak(): Int {
        var n = 0
        val today = LocalDate.now()
        if (allDone(today)) n++
        var d = today.minusDays(1)
        repeat(400) {
            when {
                allDone(d) -> n++
                wasExcused(d) -> {}
                else -> return n
            }
            d = d.minusDays(1)
        }
        return n
    }

    // ---- Pauses d'urgence (3 par jour) ----
    fun emergencyLeft(): Int {
        val today = LocalDate.now().toString()
        val used = if (sp.getString("emg_day", "") == today) sp.getInt("emg_n", 0) else 0
        return (MAX_EMERGENCY - used).coerceAtLeast(0)
    }
    fun useEmergency() {
        val today = LocalDate.now().toString()
        val used = if (sp.getString("emg_day", "") == today) sp.getInt("emg_n", 0) else 0
        sp.edit().putString("emg_day", today).putInt("emg_n", used + 1).commit()
    }

    // ---- File d'envoi vers le classement (fonctionne hors ligne) ----
    fun queuePrayer(day: String, p: Prayer) { sp.edit().putStringSet("q_pr", queuedPrayers() + "$day|${p.name}").apply() }
    fun queuedPrayers(): Set<String> = sp.getStringSet("q_pr", emptySet())!!.toSet()
    fun unqueuePrayer(item: String) { sp.edit().putStringSet("q_pr", queuedPrayers() - item).apply() }
    fun queueExcused(day: String) { sp.edit().putStringSet("q_ex", queuedExcused() + day).apply() }
    fun queuedExcused(): Set<String> = sp.getStringSet("q_ex", emptySet())!!.toSet()
    fun unqueueExcused(day: String) { sp.edit().putStringSet("q_ex", queuedExcused() - day).apply() }
    fun clearQueue() { sp.edit().remove("q_pr").remove("q_ex").apply() }

    // ---- Contrat d'engagement ----
    var contractDate: Long
        get() = sp.getLong("contract_date", 0)
        set(v) { sp.edit().putLong("contract_date", v).apply() }
    var contractName: String
        get() = sp.getString("contract_name", "") ?: ""
        set(v) { sp.edit().putString("contract_name", v).apply() }
    val contractSigned get() = contractDate > 0

    // ---- Adhan ----
    var adhanEnabled: Boolean
        get() = sp.getBoolean("adhan", true)
        set(v) { sp.edit().putBoolean("adhan", v).apply() }
    var adhanAtFajr: Boolean
        get() = sp.getBoolean("adhan_fajr", false)
        set(v) { sp.edit().putBoolean("adhan_fajr", v).apply() }
    var adhanInSilent: Boolean
        get() = sp.getBoolean("adhan_silent", false)
        set(v) { sp.edit().putBoolean("adhan_silent", v).apply() }

    var quoteIndex: Int
        get() = sp.getInt("quote", 0)
        set(v) { sp.edit().putInt("quote", v).apply() }

    // ---- Appels d'urgence « avec déblocage 3 min » (2 par jour) ----
    fun emergencyCallsLeft(): Int {
        val today = LocalDate.now().toString()
        val used = if (sp.getString("call_day", "") == today) sp.getInt("call_n", 0) else 0
        return (MAX_CALLS - used).coerceAtLeast(0)
    }
    fun useEmergencyCall() {
        val today = LocalDate.now().toString()
        val used = if (sp.getString("call_day", "") == today) sp.getInt("call_n", 0) else 0
        sp.edit().putString("call_day", today).putInt("call_n", used + 1).commit()
    }

    companion object { const val MAX_EMERGENCY = 3; const val MAX_CALLS = 2 }
}
