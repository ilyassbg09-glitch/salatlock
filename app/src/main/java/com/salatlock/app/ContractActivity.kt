package com.salatlock.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Contrat d'engagement : une intention sincère (niyya), signée avec le doigt.
 * Volontairement PAS un serment par Allah (dont la rupture obligerait à une expiation).
 */
class ContractActivity : ComponentActivity() {

    companion object {
        const val TEXT = "Bismillah.\n\n" +
            "Je prends l'intention sincère, pour Allah, de faire tout mon possible pour accomplir mes cinq prières à leur heure.\n\n" +
            "• Quand mon téléphone se bloque, je vais faire mes ablutions et je prie, au lieu de chercher à contourner l'application.\n" +
            "• Je n'utilise les pauses d'urgence que pour de vraies urgences.\n" +
            "• Si je rate une prière, je ne me décourage pas : je la rattrape et je recommence.\n\n" +
            "Ceci est une intention (niyya) que je prends envers moi-même, pas un serment.\n" +
            "Qu'Allah me facilite et accepte mes prières."
        fun signatureFile(ctx: Context) = File(ctx.filesDir, "signature.png")
        fun dateText(ctx: Context): String {
            val d = Instant.ofEpochMilli(Prefs(ctx).contractDate).atZone(ZoneId.systemDefault()).toLocalDate()
            return "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)
        }
    }

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val col = vbox().apply { setPadding(dp(22), dp(40), dp(22), dp(40)) }
        setContentView(ScrollView(this).apply { background = bgGradient(); addView(col) })

        col.add(label("📜", 54f))
        col.add(label("Mon engagement", 28f, C.GOLD, bold = true), top = 4)
        col.add(label("عهد مع نفسي", 20f, C.MUTED), top = 2)
        col.add(card().apply { addView(label(TEXT, 16f, C.WHITE, gravity = Gravity.START)) }, top = 18)

        if (prefs.contractSigned) {
            // Lecture seule
            col.add(label("Signé par ${prefs.contractName.ifBlank { "moi" }} le ${dateText(this)}", 15f, C.MUTED), top = 18)
            val f = signatureFile(this)
            if (f.exists()) col.add(ImageView(this).apply {
                setImageBitmap(BitmapFactory.decodeFile(f.absolutePath)); background = rounded(Color.WHITE, 16)
                adjustViewBounds = true; setPadding(dp(8), dp(8), dp(8), dp(8))
            }, top = 8)
            col.add(button("Fermer") { finish() }, top = 20)
            return
        }

        val name = EditText(this).apply {
            hint = "Ton prénom (facultatif)"; setTextColor(C.WHITE); setHintTextColor(C.MUTED); textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = rounded(C.CARD, 14); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        col.add(name, top = 18)
        col.add(label("✍️ Signe avec ton doigt dans le cadre :", 15f, C.WHITE, gravity = Gravity.START), top = 16)
        val pad = SignatureView(this)
        col.add(pad, top = 8, h = dp(190))
        col.add(label("Effacer", 14f, C.GOLD, bold = true, gravity = Gravity.END).apply {
            setPadding(0, dp(6), dp(4), dp(6)); setOnClickListener { pad.clear() }
        })
        val check = CheckBox(this).apply {
            text = "Je prends cet engagement sincèrement"; setTextColor(C.WHITE); textSize = 15f
            buttonTintList = android.content.res.ColorStateList.valueOf(C.GOLD)
        }
        col.add(check, top = 4)
        col.add(button("🤲 Je m'engage") {
            when {
                !pad.hasInk -> Toasts.show(this, "Signe d'abord dans le cadre ✍️")
                !check.isChecked -> Toasts.show(this, "Coche la case pour confirmer")
                else -> {
                    signatureFile(this).outputStream().use { pad.export().compress(Bitmap.CompressFormat.PNG, 100, it) }
                    prefs.contractName = name.text.toString().trim()
                    prefs.contractDate = System.currentTimeMillis()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
        }, top = 16)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (prefs.contractSigned) super.onBackPressed() else finishAffinity()
    }

    /** Zone de signature au doigt. */
    class SignatureView(ctx: Context) : View(ctx) {
        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10291F"); style = Paint.Style.STROKE; strokeWidth = ctx.dpf(3.2f)
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        var hasInk = false; private set
        init { background = ctx.rounded(Color.WHITE, 16) }
        fun clear() { path.reset(); hasInk = false; invalidate() }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            parent.requestDisallowInterceptTouchEvent(true)
            when (e.action) {
                MotionEvent.ACTION_DOWN -> path.moveTo(e.x, e.y)
                MotionEvent.ACTION_MOVE -> { for (i in 0 until e.historySize) path.lineTo(e.getHistoricalX(i), e.getHistoricalY(i)); path.lineTo(e.x, e.y); hasInk = true }
            }
            invalidate(); return true
        }
        override fun onDraw(c: Canvas) { c.drawPath(path, paint) }
        fun export(): Bitmap {
            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(b); c.drawColor(Color.WHITE); c.drawPath(path, paint); return b
        }
    }
}
