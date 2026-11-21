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

/** Boussole qui montre la direction de la Kaaba. */
class QiblaView(ctx: Context) : View(ctx), SensorEventListener {
    private val prefs = Prefs(ctx)
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val qibla = if (prefs.hasLocation) PrayerCalc.qiblaBearing(prefs.lat, prefs.lng).toFloat() else Float.NaN
    private val declination = if (prefs.hasLocation)
        GeomagneticField(prefs.lat.toFloat(), prefs.lng.toFloat(), 0f, System.currentTimeMillis()).declination else 0f
    private var sinA = 0f
    private var cosA = 1f
    private var azimuth = 0f
    private val rot = FloatArray(9)
    private val orient = FloatArray(3)
    var onAligned: ((Boolean) -> Unit)? = null
    private var aligned = false

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = C.MUTED; strokeWidth = ctx.dpf(2f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.MUTED; textAlign = Paint.Align.CENTER; textSize = ctx.dpf(14f) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onDetachedFromWindow() {
        sm.unregisterListener(this)
        super.onDetachedFromWindow()
    }

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        SensorManager.getOrientation(rot, orient)
        val a = Math.toRadians(Math.toDegrees(orient[0].toDouble()) + declination)
        sinA = 0.85f * sinA + 0.15f * sin(a).toFloat()
        cosA = 0.85f * cosA + 0.15f * cos(a).toFloat()
        azimuth = ((Math.toDegrees(atan2(sinA, cosA).toDouble()) + 360) % 360).toFloat()
        if (!qibla.isNaN()) {
            var diff = abs(qibla - azimuth) % 360; if (diff > 180) diff = 360 - diff
            val now = diff < 6
            if (now != aligned) { aligned = now; onAligned?.invoke(now) }
        }
        invalidate()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(cx, cy) - context.dpf(30f)
        fill.color = if (aligned) C.GREEN else C.CARD
        c.drawCircle(cx, cy, r, fill)
        c.drawCircle(cx, cy, r, ring)
        // Nord
        c.save(); c.rotate(-azimuth, cx, cy)
        c.drawText("N", cx, cy - r + context.dpf(18f), txt)
        c.restore()
        if (qibla.isNaN()) {
            c.drawText("Position inconnue", cx, cy, txt); return
        }
        // Flèche vers la Kaaba
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
    }
}
