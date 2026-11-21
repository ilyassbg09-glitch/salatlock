package com.salatlock.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.time.LocalDate
import java.util.concurrent.Executors

/** Écran de scan : il faut montrer un lavabo à la caméra pendant ~2 secondes. */
class ScanActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var seen: TextView
    private lateinit var bar: ProgressBar
    private val exec = Executors.newSingleThreadExecutor()
    private var detector: SinkDetector? = null
    private var progress = 0f
    private var success = false
    private var lastRun = 0L
    private var prayer: Prayer? = null
    private var day: String? = null
    private var test = false

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else status.text = "⚠️ Autorise la caméra pour pouvoir scanner le lavabo."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        readTarget()

        val root = FrameLayout(this).apply { setBackgroundColor(C.BG_BOTTOM) }
        previewView = PreviewView(this)
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        // Cadre de visée
        val frame = View(this).apply { background = rounded(0x00000000, 24, C.GOLD).apply { setStroke(dp(3), C.GOLD) } }
        root.addView(frame, FrameLayout.LayoutParams(dp(280), dp(280), Gravity.CENTER))

        val top = vbox().apply {
            setPadding(dp(20), dp(40), dp(20), dp(16))
            setBackgroundColor(0xAA041A12.toInt())
            add(label("🚰 Montre un lavabo", 24f, C.GOLD, bold = true))
            add(label(prayer?.let { "Prière de ${it.fr} · ${it.ar}" } ?: "", 15f, C.WHITE), top = 4)
        }
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = vbox().apply {
            setPadding(dp(20), dp(16), dp(20), dp(32))
            setBackgroundColor(0xAA041A12.toInt())
        }
        status = label("Je cherche un lavabo…", 18f, C.WHITE, bold = true)
        seen = label("", 13f, C.MUTED)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(C.GOLD)
        }
        bottom.add(status)
        bottom.add(bar, top = 12, h = dp(14))
        bottom.add(seen, top = 6)
        bottom.add(label("Astuce : recule un peu pour avoir tout le lavabo dans le cadre, avec de la lumière.", 13f, C.MUTED), top = 6)
        bottom.add(button("Retour", C.CARD, C.WHITE, 15f) { finish() }, top = 12)
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)

        if (prayer == null) {
            status.text = "Aucune prière en cours pour l'instant."
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else askCamera.launch(Manifest.permission.CAMERA)
    }

    private fun readTarget() {
        prayer = Prayer.of(intent.getStringExtra("prayer"))
        day = intent.getStringExtra("day")
        test = intent.getBooleanExtra("test", false)
        if (prayer == null || day == null) {
            // Ouvert sans cible : on prend le verrou en cours, sinon la prière du moment.
            prefs.lockPrayer?.let { prayer = it; day = prefs.lockDay; test = prefs.lockIsTest }
                ?: PrayerCalc.currentWindow(this)?.let { prayer = it.prayer; day = it.day.toString() }
        }
        if (day == null) day = LocalDate.now().toString()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.lockPrayer != null) LockService.start(this, LockService.ACTION_HIDE_FOR_SCAN)
    }

    override fun onStop() {
        super.onStop()
        if (!success) {
            if (prefs.lockPrayer != null) LockService.start(this, LockService.ACTION_SCAN_CLOSED)
            if (!isChangingConfigurations) finish()
        }
    }

    override fun onDestroy() {
        exec.execute { detector?.close() }
        exec.shutdown()
        super.onDestroy()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(exec) { analyze(it) }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                status.text = "⚠️ Impossible d'ouvrir la caméra"
            }
        }, mainExecutor)
    }

    private fun analyze(img: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (success || now - lastRun < 160) return
            lastRun = now
            val det = detector ?: SinkDetector(this).also { detector = it }
            var bmp = img.toBitmap()
            val rot = img.imageInfo.rotationDegrees
            if (rot != 0) {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val r = det.classify(bmp)
            runOnUiThread { onResult(r) }
        } catch (e: Exception) {
            runOnUiThread { status.text = "Erreur : ${e.message}" }
        } finally {
            img.close()
        }
    }

    private fun onResult(r: SinkDetector.Result) {
        if (success) return
        // Sensibilité 0..100 -> seuil 0,45..0,15
        val threshold = 0.45f - prefs.sensitivity * 0.003f
        val found = r.sinkScore >= threshold
        progress = (progress + if (found) 0.09f else -0.05f).coerceIn(0f, 1f)
        bar.progress = (progress * 100).toInt()
        status.text = when {
            found -> "✅ Lavabo détecté… ne bouge pas"
            progress > 0 -> "Presque… garde le lavabo dans le cadre"
            else -> "Je cherche un lavabo…"
        }
        seen.text = "Je vois : ${r.topLabel} (${(r.topScore * 100).toInt()} %) · lavabo ${(r.sinkScore * 100).toInt()} %"
        if (progress >= 1f) onSuccess()
    }

    private fun onSuccess() {
        success = true
        status.text = "✅ C'est bon ! Va prier 🤲"
        try {
            val v = getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
        LockService.startCountdown(this, prayer!!, day!!, test)
        previewView.postDelayed({ finish() }, 400)
    }
}
