package com.salatlock.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Service au premier plan qui affiche un écran par-dessus toutes les applis
 * tant que la prière n'est pas faite.
 */
class LockService : Service() {

    companion object {
        const val ACTION_REFRESH = "refresh"
        const val ACTION_HIDE_FOR_SCAN = "hide_scan"
        const val ACTION_SCAN_CLOSED = "scan_closed"
        const val ACTION_START_COUNTDOWN = "countdown"

        fun start(ctx: Context, action: String = ACTION_REFRESH, extras: Intent.() -> Unit = {}) {
            val i = Intent(ctx, LockService::class.java).setAction(action).apply(extras)
            try {
                ctx.startForegroundService(i)
            } catch (e: Exception) {
                // Démarrage refusé (rare) : on retentera au prochain réveil.
            }
        }

        fun startCountdown(ctx: Context, p: Prayer, day: String, test: Boolean) = start(ctx, ACTION_START_COUNTDOWN) {
            putExtra("prayer", p.name); putExtra("day", day); putExtra("test", test)
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var overlayMode = 0          // 1 = bloqué, 2 = compte à rebours
    private var timerText: TextView? = null
    private var scanUntil = 0L           // écran caché pendant le scan
    private var hiddenUntil = 0L         // écran caché (appel d'urgence / pause)
    private var inCall = false
    private var phoneCallback: Any? = null

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        prefs = Prefs(this)
        goForeground()
        listenToCalls()
        handler.post(ticker)
    }

    private fun goForeground() {
        val p = prefs.lockPrayer ?: prefs.countdownPrayer
        val text = when {
            prefs.countdownEnd > 0 -> "🤲 Prière en cours…"
            p != null -> "🔒 Prière de ${p.fr} — fais tes ablutions"
            else -> "Salat Lock"
        }
        val n = Notifs.lockNotification(this, text, p, prefs.lockDay)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifs.ID_LOCK, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifs.ID_LOCK, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            ACTION_HIDE_FOR_SCAN -> {
                scanUntil = System.currentTimeMillis() + 3 * 60_000L
                removeOverlay()
            }
            ACTION_SCAN_CLOSED -> {
                scanUntil = 0
                render()
            }
            ACTION_START_COUNTDOWN -> {
                val p = Prayer.of(intent.getStringExtra("prayer"))
                val day = intent.getStringExtra("day")
                if (p != null && day != null) {
                    prefs.countdownPrayer = p
                    prefs.countdownDay = day
                    prefs.countdownIsTest = intent.getBooleanExtra("test", false)
                    prefs.countdownEnd = System.currentTimeMillis() +
                        if (prefs.countdownIsTest) 30_000L else prefs.countdownMin * 60_000L
                }
                scanUntil = 0
                render()
            }
            else -> render()
        }
        return START_STICKY
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (prefs.countdownEnd > 0 && now >= prefs.countdownEnd) {
            finishCountdown(); return
        }
        if (overlay == null) render() else updateTimer()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val mode = when {
            prefs.countdownEnd > 0 -> 2
            prefs.lockPrayer != null -> 1
            else -> 0
        }
        if (mode == 0) { shutdown(); return }
        if (mode == 2 && now >= prefs.countdownEnd) { finishCountdown(); return }

        val hidden = now < scanUntil || now < hiddenUntil || inCall || !Settings.canDrawOverlays(this)
        if (hidden) { removeOverlay(); return }
        if (overlay != null && overlayMode == mode) { updateTimer(); return }

        removeOverlay()
        val content = if (mode == 1) buildLockView() else buildCountdownView()
        val root = BlockingFrame(this).apply {
            background = bgGradient()
            addView(content, FrameLayout.LayoutParams(-1, -1))
            isFocusable = true; isFocusableInTouchMode = true
            @Suppress("DEPRECATION")
            systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                (if (mode == 2) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0),
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP
            if (Build.VERSION.SDK_INT >= 28)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            wm.addView(root, lp)
            root.requestFocus()
            overlay = root
            overlayMode = mode
            updateTimer()
        } catch (e: Exception) {
            overlay = null
        }
    }

    private fun removeOverlay() {
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
        overlayMode = 0
        timerText = null
    }

    // ---------- Écran « bloqué » ----------
    private fun buildLockView(): View {
        val p = prefs.lockPrayer ?: Prayer.DHUHR
        val col = vbox().apply { setPadding(dp(24), dp(48), dp(24), dp(32)) }
        if (prefs.lockIsTest) col.add(label("MODE TEST", 13f, C.RED, bold = true))
        col.add(label("🕌", 64f), top = 8)
        col.add(label("حان وقت الصلاة", 32f, C.GOLD, bold = true), top = 8)
        col.add(label("C'est l'heure de ${p.fr} · ${p.ar}", 22f, C.WHITE, bold = true), top = 8)
        val today = PrayerCalc.forDate(this, java.time.LocalDate.parse(prefs.lockDay ?: java.time.LocalDate.now().toString()))
        today?.times?.get(p)?.let { col.add(label("Adhan à ${PrayerCalc.fmt(it)}", 15f, C.MUTED), top = 4) }

        val q = card().apply { addView(label(Quotes.next(prefs), 15f, C.WHITE, gravity = Gravity.START)) }
        col.add(q, top = 24)

        col.add(button("🚰  Scanner un lavabo") { openScan() }, top = 28)
        col.add(label("Fais tes ablutions, montre un lavabo à la caméra,\npuis prie pendant le compte à rebours.", 14f, C.MUTED), top = 10)

        val row = hbox()
        row.add(button("📞 Urgence (${prefs.emergencyCallsLeft()})", C.CARD, C.WHITE, 15f) { emergencyCall() }, w = 0, weight = 1f)
        row.addView(View(this), lp(dp(10), 1))
        val left = prefs.emergencyLeft()
        val pause = button("⏸ Pause 5 min ($left)", C.CARD, if (left > 0) C.WHITE else C.MUTED, 15f) {
            Toasts.show(this, "Maintiens appuyé 3 secondes pour une pause d'urgence")
        }
        setupLongHold(pause) { emergencyPause() }
        row.add(pause, w = 0, weight = 1f)
        col.add(row, top = 28)
        if (prefs.contractSigned) col.add(label("✍️ Tu t'es engagé le ${ContractActivity.dateText(this)} à faire tout ton possible pour prier à l'heure.", 13f, C.MUTED), top = 14)

        if (prefs.lockIsTest) {
            col.add(button("Terminer le test", C.RED, C.WHITE, 15f) {
                prefs.clearLock(); render()
            }, top = 16)
        }
        return ScrollView(this).apply { isFillViewport = true; addView(col) }
    }

    // ---------- Écran « compte à rebours » ----------
    private fun buildCountdownView(): View {
        val p = prefs.countdownPrayer ?: Prayer.DHUHR
        val col = vbox().apply { setPadding(dp(24), dp(48), dp(24), dp(32)) }
        if (prefs.countdownIsTest) col.add(label("MODE TEST (30 s)", 13f, C.RED, bold = true))
        col.add(label("🤲 Prie maintenant", 26f, C.GOLD, bold = true), top = 8)
        col.add(label("${p.fr} · ${p.ar}", 18f, C.WHITE), top = 4)
        val t = label("--:--", 76f, C.WHITE, bold = true).apply { typeface = android.graphics.Typeface.MONOSPACE }
        timerText = t
        col.add(t, top = 8)
        col.add(label("Le téléphone se débloque à la fin du compte à rebours.", 14f, C.MUTED), top = 2)

        val qText = label("Tourne-toi : la flèche dorée indique la Qibla", 14f, C.MUTED)
        val qibla = QiblaView(this).apply {
            onStatus = { ok, hint -> qText.text = hint ?: if (ok) "✅ Tu es face à la Qibla" else "Tourne-toi : la flèche dorée indique la Qibla" }
        }
        col.add(qibla, top = 16, w = dp(230), h = dp(230))
        col.add(qText, top = 4)

        val dua = card().apply {
            addView(label("Invocation après les ablutions", 13f, C.GOLD, bold = true, gravity = Gravity.START))
            addView(label(Quotes.DUA_WUDU_AR, 19f, C.WHITE).apply { setPadding(0, dp(8), 0, dp(8)) })
            addView(label(Quotes.DUA_WUDU_FR, 13f, C.MUTED, gravity = Gravity.START))
        }
        col.add(dua, top = 20)
        col.add(button("📞 Urgence", C.CARD, C.WHITE, 15f) { emergencyCall() }, top = 20)
        return ScrollView(this).apply { isFillViewport = true; addView(col) }
    }

    private fun updateTimer() {
        val t = timerText ?: return
        val left = ((prefs.countdownEnd - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        t.text = String.format("%02d:%02d", left / 60, left % 60)
    }

    private fun finishCountdown() {
        val p = prefs.countdownPrayer
        val day = prefs.countdownDay
        val test = prefs.countdownIsTest
        prefs.clearCountdown()
        if (!test) {
            if (p != null && day != null) prefs.markDone(day, p)
            prefs.clearLock()
            val s = prefs.streak()
            Cloud.bg({ Cloud.flush(this) })
            Notifs.info(this, "تقبل الله — Qu'Allah accepte ta prière 🤲",
                (if (p != null) "${p.fr} faite. " else "") + "Série : $s jour(s) 🔥 · Total : ${prefs.total} prières")
        } else {
            prefs.clearLock()
            Notifs.info(this, "Test réussi ✅", "Le blocage, le scan et le compte à rebours fonctionnent.")
        }
        removeOverlay()
        Scheduler.checkNow(this)   // si une autre prière est en retard, on rebloque
        if (!prefs.isLockedOrPraying) shutdown() else render()
    }

    private fun shutdown() {
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openScan() {
        AdhanService.stop(this)
        scanUntil = System.currentTimeMillis() + 3 * 60_000L
        removeOverlay()
        val i = Intent(this, ScanActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("prayer", prefs.lockPrayer?.name)
            .putExtra("day", prefs.lockDay)
            .putExtra("test", prefs.lockIsTest)
        startActivity(i)
    }

    private fun emergencyCall() {
        // 2 fois par jour : 3 minutes sans blocage. Au-delà, on peut TOUJOURS appeler
        // (sécurité), mais le blocage revient après 45 s, sauf pendant un appel en cours.
        val withUnlock = prefs.emergencyCallsLeft() > 0
        if (withUnlock) prefs.useEmergencyCall()
        hiddenUntil = System.currentTimeMillis() + if (withUnlock) 3 * 60_000L else 45_000L
        if (!withUnlock) Toasts.show(this, "Appel d'urgence possible. Le blocage revient dans 45 s (sauf pendant un appel).")
        removeOverlay()
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    private fun emergencyPause() {
        if (prefs.emergencyLeft() <= 0) {
            Toasts.show(this, "Plus de pause d'urgence aujourd'hui"); return
        }
        prefs.useEmergency()
        hiddenUntil = System.currentTimeMillis() + 5 * 60_000L
        removeOverlay()
        val rappel = if (prefs.contractSigned) " Rappel : tu t'es engagé le ${ContractActivity.dateText(this)} ✍️" else ""
        Toasts.show(this, "Pause de 5 minutes. Le blocage revient ensuite.$rappel")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLongHold(v: View, action: () -> Unit) {
        val fire = Runnable { action() }
        v.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { view.alpha = 0.6f; handler.postDelayed(fire, 3000) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 1f; handler.removeCallbacks(fire)
                    if (e.action == MotionEvent.ACTION_UP && e.eventTime - e.downTime < 3000) view.performClick()
                }
            }
            true
        }
    }

    // ---------- Appels téléphoniques : ne jamais bloquer un appel ----------
    private fun listenToCalls() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val tm = getSystemService(TelephonyManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onCallState(state)
                }
                tm.registerTelephonyCallback(mainExecutor, cb)
                phoneCallback = cb
            } else {
                @Suppress("DEPRECATION")
                val l = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
                }
                @Suppress("DEPRECATION")
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
                phoneCallback = l
            }
        } catch (_: Exception) {}
    }

    private fun onCallState(state: Int) {
        inCall = state != TelephonyManager.CALL_STATE_IDLE
        if (inCall) removeOverlay() else render()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        val tm = getSystemService(TelephonyManager::class.java)
        try {
            val cb = phoneCallback
            if (Build.VERSION.SDK_INT >= 31 && cb is TelephonyCallback) tm?.unregisterTelephonyCallback(cb)
            else if (cb is PhoneStateListener) {
                @Suppress("DEPRECATION") tm?.listen(cb, PhoneStateListener.LISTEN_NONE)
            }
        } catch (_: Exception) {}
        super.onDestroy()
        // Si on a été tué alors qu'on devait bloquer, on se relance.
        if (Prefs(this).isLockedOrPraying) {
            Handler(Looper.getMainLooper()).postDelayed({ start(applicationContext) }, 1500)
        }
    }

    /** Avale le bouton retour. */
    private class BlockingFrame(ctx: Context) : FrameLayout(ctx) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
            return super.dispatchKeyEvent(event)
        }
    }
}

object Toasts {
    fun show(ctx: Context, s: String) =
        android.widget.Toast.makeText(ctx.applicationContext, s, android.widget.Toast.LENGTH_LONG).show()
}
