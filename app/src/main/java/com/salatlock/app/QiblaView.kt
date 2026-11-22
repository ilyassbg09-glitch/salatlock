package com.salatlock.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Boussole qui montre la direction de la Kaaba.
 * v2 : fiable à plat ET téléphone tenu debout, indique la précision et les perturbations magnétiques.
 */
class QiblaView(ctx: Context) : View(ctx), SensorEventListener {
    private val prefs = Prefs(ctx)
    private val sm = ctx.getSystemService(SensorManager::class.java)
    val qibla = if (prefs.hasLocation) PrayerCalc.qiblaBearing(prefs.lat, prefs.lng).toFloat() else Float.NaN
    private val declination = if (prefs.hasLocation)
        GeomagneticField(prefs.lat.toFloat(), prefs.lng.toFloat(), 0f, System.currentTimeMillis()).declination else 0f
    private var sinA = 0f
    private var cosA = 1f
    private var first = true
    var azimuth = 0f; private set
    private val rot = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orient = FloatArray(3)
    private var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var fieldStrength = 50f

    /** Appelé quand l'état change : aligné ?, et un message d'aide (ou null). */
    var onStatus: ((aligned: Boolean, hint: String?) -> Unit)? = null
    private var aligned = false
    private var lastHint: String? = "init"

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = C.MUTED; strokeWidth = ctx.dpf(2f) }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.MUTED; strokeWidth = ctx.dpf(1.5f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.MUTED; textAlign = Paint.Align.CENTER; textSize = ctx.dpf(14f) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onDetachedFromWindow() { sm.unregisterListener(this); super.onDetachedFromWindow() }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            fieldStrength = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
            accuracy = e.accuracy
            return
        }
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        // Inclinaison : si le téléphone est tenu debout, on change de repère (sinon le cap devient faux).
        val pitch = Math.toDegrees(kotlin.math.asin((-rot[7]).coerceIn(-1f, 1f)).toDouble())
        val upright = abs(pitch) > 50
        if (upright) {
            SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            SensorManager.getOrientation(remapped, orient)
        } else {
            SensorManager.getOrientation(rot, orient)
        }
        val a = Math.toRadians(Math.toDegrees(orient[0].toDouble()) + declination)
        if (first) { sinA = sin(a).toFloat(); cosA = cos(a).toFloat(); first = false }
        sinA = 0.8f * sinA + 0.2f * sin(a).toFloat()
        cosA = 0.8f * cosA + 0.2f * cos(a).toFloat()
        azimuth = ((Math.toDegrees(atan2(sinA, cosA).toDouble()) + 360) % 360).toFloat()

        val hint = when {
            accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "🔄 Boussole imprécise : fais un 8 dans l'air avec ton téléphone"
            fieldStrength < 20 || fieldStrength > 75 -> "🧲 Perturbation magnétique : éloigne-toi des aimants, enceintes, ordinateurs"
            else -> null
        }
        var now = false
        if (!qibla.isNaN()) {
            var diff = abs(qibla - azimuth) % 360; if (diff > 180) diff = 360 - diff
            now = diff < 5
        }
        if (now != aligned || hint != lastHint) { aligned = now; lastHint = hint; onStatus?.invoke(now, hint) }
        invalidate()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) { if (s?.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy = a }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(cx, cy) - context.dpf(30f)
        fill.color = if (aligned) C.GREEN else C.CARD
        c.drawCircle(cx, cy, r, fill)
        c.drawCircle(cx, cy, r, ring)
        // graduations tous les 30°
        c.save(); c.rotate(-azimuth, cx, cy)
        for (k in 0 until 12) { c.drawLine(cx, cy - r, cx, cy - r + context.dpf(if (k % 3 == 0) 12f else 7f), tick); c.rotate(30f, cx, cy) }
        c.restore()
        c.save(); c.rotate(-azimuth, cx, cy)
        c.drawText("N", cx, cy - r + context.dpf(28f), txt)
        c.restore()
        if (qibla.isNaN()) { c.drawText("Position inconnue", cx, cy, txt); return }
        c.save(); c.rotate(qibla - azimuth, cx, cy)
        fill.color = C.GOLD
        val path = Path().apply {
            moveTo(cx, cy - r + context.dpf(6f))
            lineTo(cx - context.dpf(14f), cy - r * 0.25f)
            lineTo(cx + context.dpf(14f), cy - r * 0.25f)
            close()
        }
        c.drawPath(path, fill)
        fill.strokeWidth = context.dpf(5f)
        c.drawLine(cx, cy - r * 0.3f, cx, cy + r * 0.45f, fill)
        txt.textSize = context.dpf(22f)
        c.drawText("🕋", cx, cy - r - context.dpf(6f), txt)
        txt.textSize = context.dpf(14f)
        c.restore()
        c.drawCircle(cx, cy, context.dpf(6f), fill)
        // repère fixe en haut : là où pointe le téléphone
        fill.color = C.WHITE
        c.drawRect(cx - context.dpf(2f), cy - r - context.dpf(2f), cx + context.dpf(2f), cy - r + context.dpf(16f), fill)
    }
}
