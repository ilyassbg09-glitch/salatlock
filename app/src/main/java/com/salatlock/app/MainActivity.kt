package com.salatlock.app

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var content: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() { build(); handler.postDelayed(this, 30_000) }
    }
    private val askPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { build() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (!prefs.contractSigned) {
            startActivity(Intent(this, ContractActivity::class.java)); finish(); return
        }
        Notifs.channels(this)
        content = vbox().apply { setPadding(dp(18), dp(36), dp(18), dp(36)) }
        setContentView(ScrollView(this).apply { background = bgGradient(); addView(content) })
    }

    override fun onResume() {
        super.onResume()
        Scheduler.reschedule(this)
        handler.post(refresher)
        UpdateChecker.check(this) { build() }
        Cloud.dailyPing(this)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun build() {
        content.removeAllViews()
        content.add(label("🕌 Salat Lock", 30f, C.GOLD, bold = true))
        content.add(label("Ton téléphone se bloque tant que tu n'as pas prié.", 14f, C.MUTED), top = 4)

        UpdateChecker.cached(this)?.let { u ->
            content.add(card().apply {
                background = rounded(C.GREEN, 20)
                addView(label("🎉 Nouvelle version ${u.versionName} disponible", 18f, C.WHITE, bold = true, gravity = Gravity.START))
                if (u.notes.isNotBlank()) addView(label(u.notes, 14f, C.WHITE, gravity = Gravity.START))
                add(button("⬇️ Mettre à jour") {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                }, top = 10)
                addView(label("Télécharge le fichier puis ouvre-le : il s'installe par-dessus, tes réglages sont gardés.", 12f, C.WHITE, gravity = Gravity.START))
            }, top = 16)
        }

        if (!prefs.hasLocation) {
            content.add(card().apply {
                addView(label("📍 Choisis d'abord ta ville", 18f, C.WHITE, bold = true, gravity = Gravity.START))
                addView(label("Pour calculer les heures de prière, l'app a besoin de ta position.", 14f, C.MUTED, gravity = Gravity.START))
                add(button("Choisir ma ville / ma position") { openSettings() }, top = 12)
            }, top = 20)
        } else {
            content.add(timesCard(), top = 20)
        }

        setupCard()?.let { content.add(it, top = 16) }

        // Statistiques
        content.add(card().apply {
            val row = hbox()
            row.add(stat("🔥 ${prefs.streak()}", "jours d'affilée"), w = 0, weight = 1f)
            row.add(stat("🤲 ${prefs.total}", "prières faites"), w = 0, weight = 1f)
            addView(row)
        }, top = 16)

        if (prefs.isExcused) {
            val until = PrayerCalc.fmt(prefs.excusedUntil)
            val d = java.time.Instant.ofEpochMilli(prefs.excusedUntil).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            content.add(card().apply {
                addView(label("🌙 Mode dispense actif jusqu'au ${d.dayOfMonth}/${d.monthValue} à $until", 15f, C.GOLD, gravity = Gravity.START))
                addView(label("Aucun blocage pendant cette période.", 13f, C.MUTED, gravity = Gravity.START))
            }, top = 16)
        }
        if (!prefs.masterEnabled) {
            content.add(label("⏸ Le blocage est désactivé dans les réglages.", 15f, C.RED), top = 16)
        }

        content.add(button("🚰 Je vais prier maintenant") { prayNow() }, top = 20)
        content.add(label("Tu peux prier avant le blocage : scanne un lavabo et la prière est comptée.", 13f, C.MUTED), top = 6)

        content.add(button("🏆 Classement en ligne") { startActivity(Intent(this, LeaderboardActivity::class.java)) }, top = 16)

        content.add(label("Direction de la Qibla", 16f, C.GOLD, bold = true), top = 24)
        val qInfo = label("", 14f, C.MUTED)
        val q = QiblaView(this).apply {
            onStatus = { ok, hint -> qInfo.text = (if (!qibla.isNaN()) "Qibla : ${qibla.toInt()}° depuis le nord" else "") +
                (hint?.let { "\n$it" } ?: if (ok) "\n✅ Tu es face à la Qibla" else "\nTiens le téléphone à plat ou devant toi") }
        }
        if (!q.qibla.isNaN()) qInfo.text = "Qibla : ${q.qibla.toInt()}° depuis le nord"
        content.add(q, top = 6, w = dp(240), h = dp(240))
        content.add(qInfo, top = 2)

        val row = hbox()
        row.add(button("⚙️ Réglages", C.CARD, C.WHITE, 15f) { openSettings() }, w = 0, weight = 1f)
        row.addView(android.view.View(this), lp(dp(10), 1))
        row.add(button("🧪 Tester", C.CARD, C.WHITE, 15f) { testLock() }, w = 0, weight = 1f)
        content.add(row, top = 20)
        content.add(button("🌙 Mode dispense (règles, maladie…)", C.CARD, C.WHITE, 15f) { excusedDialog() }, top = 10)
        content.add(button("📜 Mon engagement", C.CARD, C.WHITE, 15f) { startActivity(Intent(this, ContractActivity::class.java)) }, top = 10)
        content.add(button("📤 Partager l'app à un proche", C.CARD, C.WHITE, 15f) { share() }, top = 10)
        content.add(label("Version ${BuildConfig.VERSION_NAME} · sans pub · sans pistage", 12f, C.MUTED), top = 16)
    }

    private fun stat(big: String, small: String) = vbox().apply {
        add(label(big, 26f, C.GOLD, bold = true))
        add(label(small, 13f, C.MUTED))
    }

    private fun timesCard() = card().apply {
        val t = PrayerCalc.today(this@MainActivity) ?: return@apply
        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        val next = PrayerCalc.nextPrayer(this@MainActivity)
        addView(label("📍 ${prefs.cityLabel.ifBlank { "Ma position" }}", 14f, C.MUTED, gravity = Gravity.START))
        if (next != null) {
            val mins = (next.start - now) / 60_000
            val inTxt = if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min"
            addView(label("Prochaine : ${next.prayer.fr} à ${PrayerCalc.fmt(next.start)} (dans $inTxt)", 17f, C.WHITE, bold = true, gravity = Gravity.START).apply {
                setPadding(0, dp(4), 0, dp(10))
            })
        }
        for (p in Prayer.values()) {
            val row = hbox().apply { setPadding(0, dp(6), 0, dp(6)) }
            val done = prefs.isDone(today, p)
            val passed = t.times[p]!! <= now
            val icon = when {
                !prefs.isPrayerEnabled(p) -> "⚪"
                done -> "✅"
                passed -> "⏳"
                else -> "🕐"
            }
            row.add(label("$icon  ${p.fr}", 17f, C.WHITE, gravity = Gravity.START), w = 0, weight = 1f)
            row.add(label(p.ar, 17f, C.MUTED), w = 0, weight = 1f)
            row.add(label(PrayerCalc.fmt(t.times[p]!!), 17f, C.GOLD, bold = true, gravity = Gravity.END), w = 0, weight = 1f)
            addView(row)
        }
        addView(label("Lever du soleil : ${PrayerCalc.fmt(t.sunrise)} · blocage ${prefs.graceMin} min après l'adhan", 12f, C.MUTED, gravity = Gravity.START).apply {
            setPadding(0, dp(8), 0, 0)
        })
    }

    /** Liste des autorisations à donner pour que le blocage marche. */
    private fun setupCard(): LinearLayout? {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (!Settings.canDrawOverlays(this)) items += "🔴 Autoriser l'affichage par-dessus les autres applis (obligatoire)" to {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            items += "🔔 Autoriser les notifications (rappel à l'adhan)" to { askPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
        if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
            items += "⏰ Autoriser les alarmes exactes" to {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName))
            items += "🔋 Ne pas restreindre la batterie (sinon le blocage peut sauter)" to {
                @Suppress("BatteryLife")
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            items += "📷 Autoriser la caméra (pour scanner le lavabo)" to { askPerm.launch(Manifest.permission.CAMERA) }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            items += "📞 Autoriser l'état des appels (pour ne jamais bloquer un appel entrant)" to {
                askPerm.launch(Manifest.permission.READ_PHONE_STATE)
            }
        if (items.isEmpty()) return null
        return card().apply {
            addView(label("Configuration (${items.size} étape(s) restante(s))", 17f, C.GOLD, bold = true, gravity = Gravity.START))
            for ((txt, act) in items) {
                add(label(txt, 15f, C.WHITE, gravity = Gravity.START).apply {
                    background = rounded(C.DARK, 14)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    setOnClickListener { act() }
                }, top = 10)
            }
        }
    }

    private fun prayNow() {
        val w = PrayerCalc.currentWindow(this)
        when {
            !prefs.hasLocation -> openSettings()
            w == null -> Toasts.show(this, "Aucune prière en cours")
            prefs.isDone(w.day.toString(), w.prayer) -> Toasts.show(this, "${w.prayer.fr} est déjà faite ✅")
            else -> startActivity(Intent(this, ScanActivity::class.java)
                .putExtra("prayer", w.prayer.name).putExtra("day", w.day.toString()))
        }
    }

    private fun testLock() {
        if (!Settings.canDrawOverlays(this)) {
            Toasts.show(this, "Donne d'abord l'autorisation « affichage par-dessus les autres applis »"); return
        }
        AlertDialog.Builder(this)
            .setTitle("Tester le blocage")
            .setMessage("Le téléphone va se bloquer comme à l'heure de la prière. Scanne un lavabo : un compte à rebours de 30 secondes démarre, puis tout se débloque. (Un bouton « Terminer le test » est aussi là.)")
            .setPositiveButton("C'est parti") { _, _ ->
                val p = PrayerCalc.currentWindow(this)?.prayer ?: Prayer.DHUHR
                Scheduler.lock(this, p, LocalDate.now().toString(), test = true)
            }
            .setNegativeButton("Annuler", null).show()
    }

    private fun excusedDialog() {
        val labels = arrayOf("1 jour", "3 jours", "5 jours", "7 jours", "10 jours", "Désactiver le mode dispense")
        val days = intArrayOf(1, 3, 5, 7, 10, 0)
        AlertDialog.Builder(this)
            .setTitle("Mode dispense")
            .setItems(labels) { _, i ->
                if (days[i] == 0) { prefs.excusedUntil = 0; prefs.excusedFrom = 0 }
                else {
                    prefs.excusedFrom = System.currentTimeMillis()
                    prefs.excusedUntil = System.currentTimeMillis() + days[i] * 86_400_000L
                }
                build()
            }.show()
    }

    private fun share() {
        val url = if (BuildConfig.SITE_URL.contains("REPLACE_ME")) "" else " ${BuildConfig.SITE_URL}"
        val txt = "Salam ! J'utilise Salat Lock : le téléphone se bloque tant que tu n'as pas prié 🕌 Pour débloquer, tu scannes un lavabo (ablutions) puis tu pries. Gratuit, sans pub :$url"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Partager Salat Lock"))
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))
}
