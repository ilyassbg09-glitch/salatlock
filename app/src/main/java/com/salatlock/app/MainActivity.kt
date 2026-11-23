package com.salatlock.app

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate

/**
 * Écran principal avec des onglets en haut :
 * 🏠 Accueil · 🏆 Classement · 🧭 Qibla · ☰ Plus
 */
class MainActivity : ComponentActivity(), MainActions {

    private lateinit var prefs: Prefs
    private lateinit var tabBar: LinearLayout
    private lateinit var body: FrameLayout
    private var tab = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var board: LeaderboardTab
    private lateinit var screen: MainScreen

    private val ticker = object : Runnable {
        override fun run() {
            screen.countdownText?.let { t ->
                val left = ((screen.nextStart - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                t.text = if (left >= 3600) "dans %d h %02d min".format(left / 3600, (left % 3600) / 60)
                else "dans %02d:%02d".format(left / 60, left % 60)
                if (left == 0L && tab == 0) build()
            }
            handler.postDelayed(this, 1000)
        }
    }
    private val askPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { build() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (!prefs.contractSigned) {
            startActivity(Intent(this, ContractActivity::class.java)); finish(); return
        }
        Notifs.channels(this)
        tab = savedInstanceState?.getInt("tab") ?: intent.getIntExtra("tab", 0)
        board = LeaderboardTab(this).also { it.onChanged = { if (tab == 1) build() } }
        screen = MainScreen(this, this, board)

        val (root, tb, bd) = screen.createRoot()
        tabBar = tb; body = bd
        setContentView(root)
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putInt("tab", tab) }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra("tab")) { tab = intent.getIntExtra("tab", 0); build() }
    }

    override fun onResume() {
        super.onResume()
        Scheduler.reschedule(this)
        UpdateChecker.check(this) { if (tab == 0) build() }
        Cloud.dailyPing(this)
        build()
        handler.post(ticker)
    }

    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }

    override fun selectTab(i: Int) { if (i != tab) { tab = i; build() } }
    private fun build() = screen.render(tab, tabBar, body)
    override fun openContract() = startActivity(Intent(this, ContractActivity::class.java))
    override fun openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    override fun openIntent(i: Intent) = startActivity(i)
    override fun askPermission(p: String) = askPerm.launch(p)

    // =============================================================== actions
    override fun prayNow() {
        val w = PrayerCalc.currentWindow(this)
        when {
            !prefs.hasLocation -> openSettings()
            w == null -> Toasts.show(this, "Aucune prière en cours")
            prefs.isDone(w.day.toString(), w.prayer) -> Toasts.show(this, "${w.prayer.fr} est déjà faite ✅")
            else -> startActivity(Intent(this, ScanActivity::class.java).putExtra("prayer", w.prayer.name).putExtra("day", w.day.toString()))
        }
    }

    override fun testLock() {
        if (!Settings.canDrawOverlays(this)) { Toasts.show(this, "Donne d'abord l'autorisation « affichage par-dessus les autres applis »"); return }
        AlertDialog.Builder(this)
            .setTitle("Tester le blocage")
            .setMessage("Le téléphone va se bloquer comme à l'heure de la prière. Scanne un lavabo : un compte à rebours de 30 secondes démarre, puis tout se débloque. (Un bouton « Terminer le test » est aussi là.)")
            .setPositiveButton("C'est parti") { _, _ ->
                val p = PrayerCalc.currentWindow(this)?.prayer ?: Prayer.DHUHR
                Scheduler.lock(this, p, LocalDate.now().toString(), test = true)
            }
            .setNegativeButton("Annuler", null).show()
    }

    override fun excusedDialog() {
        val labels = arrayOf("1 jour", "3 jours", "5 jours", "7 jours", "10 jours", "Désactiver le mode dispense")
        val days = intArrayOf(1, 3, 5, 7, 10, 0)
        AlertDialog.Builder(this).setTitle("Mode dispense").setItems(labels) { _, i ->
            if (days[i] == 0) { prefs.excusedUntil = 0; prefs.excusedFrom = 0 }
            else { prefs.excusedFrom = System.currentTimeMillis(); prefs.excusedUntil = System.currentTimeMillis() + days[i] * 86_400_000L }
            build()
        }.show()
    }

    override fun share() {
        val url = if (BuildConfig.SITE_URL.contains("REPLACE_ME")) "" else " ${BuildConfig.SITE_URL}"
        val txt = "Salam ! J'utilise Salat Lock : le téléphone se bloque tant que tu n'as pas prié 🕌 Pour débloquer, tu scannes un lavabo (ablutions) puis tu pries. Gratuit, sans pub :$url"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Partager Salat Lock"))
    }

    override fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))
}

/** Ce que l'écran demande à l'Activity (clics). */
interface MainActions {
    fun selectTab(i: Int); fun prayNow(); fun openSettings(); fun testLock(); fun excusedDialog(); fun share()
    fun openContract(); fun openUrl(url: String); fun openIntent(i: Intent); fun askPermission(p: String)
}

/** Dessin des onglets (séparé de l'Activity pour pouvoir prévisualiser le design). */
class MainScreen(private val ctx: Context, private val actions: MainActions, private val board: LeaderboardTab) : android.content.ContextWrapper(ctx) {
    private val prefs = Prefs(ctx)
    var countdownText: TextView? = null
    var nextStart = 0L
    private fun safe(f: () -> Boolean): Boolean = try { f() } catch (_: Throwable) { true }

    /** Structure de l'écran : en-tête fixe (titre + onglets) puis contenu défilant. */
    fun createRoot(): Triple<LinearLayout, LinearLayout, FrameLayout> {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bgGradient() }
        val header = vbox().apply { setPadding(dp(16), dp(30), dp(16), dp(10)) }
        header.add(hbox().apply {
            add(label("🕌", 22f), w = dp(34))
            add(label("Salat Lock", 22f, C.GOLD, weight = 800, gravity = Gravity.START), w = 0, weight = 1f)
            add(label("v${BuildConfig.VERSION_NAME}", 12f, C.MUTED))
        })
        val tabBar = hbox().apply { background = rounded(C.CARD2, 26); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        header.add(tabBar, top = 12)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        val body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return Triple(root, tabBar, body)
    }

    fun render(tab: Int, tabBar: LinearLayout, body: FrameLayout) {
        // onglets
        tabBar.removeAllViews()
        listOf("🏠" to "Accueil", "🏆" to "Classement", "🧭" to "Qibla", "☰" to "Plus").forEachIndexed { i, (ic, name) ->
            val sel = i == tab
            val v = vbox().apply {
                setPadding(0, dp(7), 0, dp(7))
                background = if (sel) rounded(C.GOLD, 22) else null
                isClickable = true; isFocusable = true; setOnClickListener { actions.selectTab(i) }
                add(label(ic, 17f))
                add(label(name, 11.5f, if (sel) C.DARK else C.MUTED, weight = if (sel) 800 else 700))
            }
            tabBar.add(v, w = 0, weight = 1f)
        }
        // contenu
        body.removeAllViews(); countdownText = null
        val col = vbox().apply { setPadding(dp(16), dp(6), dp(16), dp(36)) }
        when (tab) {
            0 -> buildHome(col)
            1 -> board.build(col)
            2 -> buildQibla(col)
            else -> buildMore(col)
        }
        body.addView(ScrollView(ctx).apply { isFillViewport = true; addView(col) })
    }

    // =============================================================== 🏠 ACCUEIL
    private fun buildHome(content: LinearLayout) {
        UpdateChecker.cached(ctx)?.let { u ->
            content.add(card().apply {
                background = rounded(C.GREEN, 20)
                addView(label("🎉 Nouvelle version ${u.versionName} disponible", 17f, C.WHITE, weight = 800, gravity = Gravity.START))
                if (u.notes.isNotBlank()) addView(label(u.notes, 14f, C.WHITE, gravity = Gravity.START))
                add(button("⬇️ Mettre à jour") { actions.openUrl(u.url) }, top = 10)
                addView(label("Télécharge le fichier puis ouvre-le : il s'installe par-dessus, tes réglages sont gardés.", 12f, C.WHITE, gravity = Gravity.START))
            }, top = 10)
        }

        if (!prefs.hasLocation) {
            content.add(card().apply {
                addView(label("📍 Choisis d'abord ta ville", 18f, C.WHITE, weight = 800, gravity = Gravity.START))
                addView(label("Pour calculer les heures de prière, l'app a besoin de ta position.", 14f, C.MUTED, gravity = Gravity.START))
                add(button("Choisir ma ville") { actions.openSettings() }, top = 12)
            }, top = 10)
            setupCard()?.let { content.add(it, top = 12) }
            return
        }

        val t = PrayerCalc.today(ctx) ?: return
        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        val next = PrayerCalc.nextPrayer(ctx)
        val cur = PrayerCalc.currentWindow(ctx)

        // --- Carte « prochaine prière »
        content.add(vbox(Gravity.START).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF1F6B52.toInt(), 0xFF0F4334.toInt())).apply {
                cornerRadius = dpf(24f); setStroke(dp(1), C.LINE)
            }
            setPadding(dp(20), dp(18), dp(20), dp(20))
            addView(label("📍 ${prefs.cityLabel.ifBlank { "Ma position" }}", 13f, C.MUTED, gravity = Gravity.START))
            if (next != null) {
                nextStart = next.start
                addView(label("PROCHAINE PRIÈRE", 12f, C.GOLD, weight = 800, gravity = Gravity.START).apply { letterSpacing = 0.12f; setPadding(0, dp(12), 0, 0) })
                val row = hbox()
                row.add(label(next.prayer.fr, 34f, C.WHITE, weight = 800, gravity = Gravity.START), w = 0, weight = 1f)
                row.add(label(next.prayer.ar, 26f, C.GOLD))
                addView(row)
                val row2 = hbox()
                row2.add(label(PrayerCalc.fmt(next.start), 22f, C.WHITE, weight = 700, gravity = Gravity.START), w = 0, weight = 1f)
                val cd = label("", 16f, C.WHITE, weight = 700).apply { background = rounded(0x33000000, 16); setPadding(dp(12), dp(6), dp(12), dp(6)) }
                countdownText = cd
                row2.add(cd)
                addView(row2)
            }
            if (cur != null && prefs.isPrayerEnabled(cur.prayer) && !prefs.isDone(cur.day.toString(), cur.prayer)) {
                addView(label("⏳ ${cur.prayer.fr} n'est pas encore faite", 14f, C.GOLD, weight = 700, gravity = Gravity.START).apply { setPadding(0, dp(12), 0, 0) })
            }
        }, top = 10)

        // --- Les 5 prières du jour
        val chips = hbox().apply { setPadding(0, dp(2), 0, 0) }
        for (p in Prayer.values()) {
            val done = prefs.isDone(today, p)
            val passed = t.times[p]!! <= now
            val en = prefs.isPrayerEnabled(p)
            val isNext = next?.prayer == p && next.day.toString() == today
            val chip = vbox().apply {
                background = rounded(when { done -> C.GREEN; isNext -> C.GOLD; else -> C.CARD }, 18)
                setPadding(0, dp(10), 0, dp(10)); alpha = if (en) 1f else 0.45f
                val fg = if (isNext && !done) C.DARK else C.WHITE
                add(label(when { !en -> "–"; done -> "✓"; passed -> "!"; else -> "•" }, 16f, fg, weight = 800))
                add(label(p.fr, 12.5f, fg, weight = 700), top = 2)
                add(label(PrayerCalc.fmt(t.times[p]!!), 12f, if (isNext && !done) C.DARK else C.MUTED), top = 1)
            }
            chips.add(chip, w = 0, weight = 1f)
            if (p != Prayer.ISHA) chips.addView(View(ctx), lp(dp(6), 1))
        }
        content.add(chips, top = 12)
        content.add(label("Lever du soleil ${PrayerCalc.fmt(t.sunrise)} · blocage ${prefs.graceMin} min après l'adhan", 12f, C.MUTED), top = 8)

        // --- Bouton principal
        content.add(button("🚰  Je vais prier maintenant") { actions.prayNow() }, top = 14)

        // --- Statistiques
        content.add(hbox().apply {
            add(statCard("🔥", "${prefs.streak()}", "jours d'affilée"), w = 0, weight = 1f)
            addView(View(context), lp(dp(10), 1))
            add(statCard("🤲", "${prefs.total}", "prières faites"), w = 0, weight = 1f)
        }, top = 14)

        if (prefs.isExcused) {
            val d = java.time.Instant.ofEpochMilli(prefs.excusedUntil).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            content.add(card().apply {
                addView(label("🌙 Mode dispense jusqu'au ${d.dayOfMonth}/${d.monthValue}", 15f, C.GOLD, weight = 700, gravity = Gravity.START))
                addView(label("Aucun blocage pendant cette période.", 13f, C.MUTED, gravity = Gravity.START))
            }, top = 12)
        }
        if (!prefs.masterEnabled) content.add(label("⏸ Le blocage est désactivé dans les réglages.", 15f, C.RED), top = 12)
        setupCard()?.let { content.add(it, top = 12) }
    }

    private fun statCard(icon: String, value: String, sub: String) = vbox().apply {
        background = rounded(C.CARD, 20); setPadding(dp(12), dp(14), dp(12), dp(14))
        add(label("$icon $value", 26f, C.GOLD, weight = 800))
        add(label(sub, 12.5f, C.MUTED), top = 2)
    }

    /** Liste des autorisations à donner pour que le blocage marche. */
    private fun setupCard(): LinearLayout? {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (!safe { Settings.canDrawOverlays(ctx) }) items += "🔴 Autoriser l'affichage par-dessus les autres applis (obligatoire)" to {
            actions.openIntent(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        if (Build.VERSION.SDK_INT >= 33 && safe { ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }.not())
            items += "🔔 Autoriser les notifications (rappel à l'adhan)" to { actions.askPermission(Manifest.permission.POST_NOTIFICATIONS) }
        if (Build.VERSION.SDK_INT >= 31 && !safe { ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() })
            items += "⏰ Autoriser les alarmes exactes" to {
                actions.openIntent(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        if (!safe { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) })
            items += "🔋 Ne pas restreindre la batterie (sinon le blocage peut sauter)" to {
                @Suppress("BatteryLife")
                actions.openIntent(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        if (safe { ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }.not())
            items += "📷 Autoriser la caméra (pour scanner le lavabo)" to { actions.askPermission(Manifest.permission.CAMERA) }
        if (safe { ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED }.not())
            items += "📞 Autoriser l'état des appels (pour ne jamais bloquer un appel entrant)" to { actions.askPermission(Manifest.permission.READ_PHONE_STATE) }
        if (items.isEmpty()) return null
        return card().apply {
            background = rounded(C.CARD, 20, C.GOLD)
            addView(label("⚙️ Configuration · ${items.size} étape(s)", 16f, C.GOLD, weight = 800, gravity = Gravity.START))
            for ((txt, act) in items) {
                add(label(txt, 14.5f, C.WHITE, gravity = Gravity.START).apply {
                    background = rounded(C.DARK, 14); setPadding(dp(14), dp(12), dp(14), dp(12)); setOnClickListener { act() }
                }, top = 8)
            }
        }
    }

    // =============================================================== 🧭 QIBLA
    private fun buildQibla(content: LinearLayout) {
        content.add(label("Direction de la Qibla", 20f, C.WHITE, weight = 800), top = 8)
        val info = label("", 15f, C.MUTED)
        val q = QiblaView(ctx).apply {
            onStatus = { ok, hint ->
                info.text = hint ?: if (ok) "✅ Tu es face à la Qibla" else "Tourne-toi : la flèche dorée indique la Kaaba"
                info.setTextColor(if (ok && hint == null) C.WHITE else C.MUTED)
            }
        }
        content.add(q, top = 8, w = dp(300), h = dp(300))
        if (!q.qibla.isNaN()) content.add(label("${q.qibla.toInt()}°", 40f, C.GOLD, weight = 800), top = 2)
        content.add(label("depuis le nord · ${prefs.cityLabel.ifBlank { "ma position" }}", 13f, C.MUTED))
        content.add(info, top = 10)
        content.add(card().apply {
            addView(label("Conseils", 15f, C.GOLD, weight = 800, gravity = Gravity.START))
            addView(label("• Marche à plat ou téléphone tenu devant toi.\n• Éloigne-toi des aimants, enceintes et ordinateurs.\n• Si la flèche bouge bizarrement : fais un 8 dans l'air avec ton téléphone.", 14f, C.WHITE, gravity = Gravity.START))
        }, top = 16)
    }

    // =============================================================== ☰ PLUS
    private fun buildMore(content: LinearLayout) {
        content.add(label("Mon compte", 13f, C.MUTED, weight = 700, gravity = Gravity.START), top = 8)
        content.add(menuRow("📜", "Mon engagement", "Signé le ${ContractActivity.dateText(ctx)}") { actions.openContract() }, top = 8)
        content.add(menuRow("🌙", "Mode dispense", if (prefs.isExcused) "Actif" else "Règles, maladie, voyage…") { actions.excusedDialog() }, top = 8)
        content.add(label("Application", 13f, C.MUTED, weight = 700, gravity = Gravity.START), top = 20)
        content.add(menuRow("⚙️", "Réglages", "Ville, horaires, adhan, blocage") { actions.openSettings() }, top = 8)
        content.add(menuRow("🧪", "Tester le blocage", "Bloque le téléphone 30 secondes") { actions.testLock() }, top = 8)
        content.add(menuRow("📤", "Partager l'app", "Envoie Salat Lock à un proche") { actions.share() }, top = 8)
        content.add(label("Salat Lock ${BuildConfig.VERSION_NAME} · sans pub · aucune donnée personnelle", 12f, C.MUTED), top = 24)
    }


}
