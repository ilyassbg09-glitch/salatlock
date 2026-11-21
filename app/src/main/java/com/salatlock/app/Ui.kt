package com.salatlock.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

object C {
    val BG_TOP = Color.parseColor("#0B3D2E")
    val BG_BOTTOM = Color.parseColor("#041A12")
    val GOLD = Color.parseColor("#E8C468")
    val WHITE = Color.WHITE
    val MUTED = Color.parseColor("#A9C2B7")
    val CARD = Color.parseColor("#1A4D3D")
    val GREEN = Color.parseColor("#2E9E6B")
    val RED = Color.parseColor("#C0504D")
    val DARK = Color.parseColor("#10291F")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun bgGradient() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(C.BG_TOP, C.BG_BOTTOM))

fun Context.rounded(color: Int, radiusDp: Int = 16, stroke: Int? = null) = GradientDrawable().apply {
    setColor(color); cornerRadius = dpf(radiusDp.toFloat())
    if (stroke != null) setStroke(dp(1), stroke)
}

fun Context.label(
    s: CharSequence, size: Float = 16f, color: Int = C.WHITE, bold: Boolean = false,
    gravity: Int = Gravity.CENTER
) = TextView(this).apply {
    text = s; textSize = size; setTextColor(color); this.gravity = gravity
    if (bold) typeface = Typeface.DEFAULT_BOLD
    setLineSpacing(0f, 1.15f)
}

fun Context.button(s: String, bg: Int = C.GOLD, fg: Int = C.DARK, size: Float = 17f, onClick: (View) -> Unit) =
    label(s, size, fg, bold = true).apply {
        background = rounded(bg, 28)
        setPadding(dp(20), dp(14), dp(20), dp(14))
        isClickable = true; isFocusable = true
        setOnClickListener(onClick)
    }

fun Context.vbox(gravity: Int = Gravity.CENTER_HORIZONTAL) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; this.gravity = gravity
}

fun Context.hbox() = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
}

fun Context.card() = vbox(Gravity.START).apply {
    background = rounded(C.CARD, 20)
    setPadding(dp(18), dp(16), dp(18), dp(16))
}

fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0, weight: Float = 0f) =
    LinearLayout.LayoutParams(w, h, weight).apply { topMargin = top }

fun LinearLayout.add(v: View, top: Int = 0, w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f): View {
    addView(v, lp(w, h, context.dp(top), weight)); return v
}
