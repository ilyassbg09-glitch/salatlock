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
    val CARD2 = Color.parseColor("#13392D")
    val LINE = Color.parseColor("#2A5A4A")
}

/** Ligne de menu : icône + titre + sous-titre + chevron. */
fun Context.menuRow(icon: String, title: String, sub: String? = null, onClick: () -> Unit) = hbox().apply {
    setPadding(dp(16), dp(14), dp(14), dp(14))
    background = rounded(C.CARD, 18)
    isClickable = true; isFocusable = true; setOnClickListener { onClick() }
    add(label(icon, 22f).apply { gravity = Gravity.CENTER }, w = dp(40))
    val col = vbox(Gravity.START)
    col.add(label(title, 16f, C.WHITE, weight = 700, gravity = Gravity.START))
    if (sub != null) col.add(label(sub, 13f, C.MUTED, gravity = Gravity.START), top = 2)
    add(col, w = 0, weight = 1f)
    add(label("›", 24f, C.MUTED), w = dp(20))
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun bgGradient() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(C.BG_TOP, C.BG_BOTTOM))

fun Context.rounded(color: Int, radiusDp: Int = 16, stroke: Int? = null) = GradientDrawable().apply {
    setColor(color); cornerRadius = dpf(radiusDp.toFloat())
    if (stroke != null) setStroke(dp(1), stroke)
}

private val fontCache = HashMap<Int, Typeface?>()
/** Police Inter (400 normal, 700 gras, 800 extra-gras), avec repli sur la police système. */
fun Context.font(weight: Int): Typeface {
    val id = when { weight >= 800 -> R.font.inter_extrabold; weight >= 700 -> R.font.inter_bold; else -> R.font.inter_regular }
    val tf = fontCache.getOrPut(id) { try { androidx.core.content.res.ResourcesCompat.getFont(this, id) } catch (_: Exception) { null } }
    return tf ?: if (weight >= 700) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
}

fun Context.label(
    s: CharSequence, size: Float = 16f, color: Int = C.WHITE, bold: Boolean = false,
    gravity: Int = Gravity.CENTER, weight: Int = if (bold) 700 else 400
) = TextView(this).apply {
    text = s; textSize = size; setTextColor(color); this.gravity = gravity
    typeface = font(weight)
    setLineSpacing(0f, 1.15f)
}

fun Context.button(s: String, bg: Int = C.GOLD, fg: Int = C.DARK, size: Float = 17f, onClick: (View) -> Unit) =
    label(s, size, fg, weight = 800).apply {
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

private const val AUTO = -100

/** Ajoute une vue. Largeur par défaut : toute la largeur dans une colonne, juste la place nécessaire dans une ligne. */
fun LinearLayout.add(v: View, top: Int = 0, w: Int = AUTO, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f): View {
    @Suppress("NAME_SHADOWING")
    val w = if (w != AUTO) w else if (orientation == LinearLayout.HORIZONTAL) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
    // Un texte « qui prend la place restante » est mis dans un conteneur : certains rendus
    // lui donnaient sinon une largeur nulle (texte invisible sur 10 lignes).
    if (weight > 0f && v is TextView && orientation == LinearLayout.HORIZONTAL) {
        val box = android.widget.FrameLayout(context)
        box.addView(v, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER_VERTICAL))
        addView(box, lp(w, h, context.dp(top), weight)); return v
    }
    addView(v, lp(w, h, context.dp(top), weight)); return v
}
