package com.salatlock.app

import android.content.Context
import android.app.AlertDialog
import android.content.Intent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout

/** Onglet « Classement » : mondial + amis. Commun aux Android et aux iPhone. */
class LeaderboardTab(private val act: Context, private val autoLoad: Boolean = true) {
    var onChanged: () -> Unit = {}
    private var sub = 0 // 0 = mondial, 1 = amis
    private var profile: Cloud.Profile? = null
    private var rows: List<Cloud.Row>? = null
    private var error: String? = null
    private var loading = false
    private var loadedAt = 0L
    private var loadedSub = -1

    fun build(content: LinearLayout) = with(act) {
        if (autoLoad && !loading && (System.currentTimeMillis() - loadedAt > 30_000 || loadedSub != sub)) load()

        error?.let { content.add(label("⚠️ $it", 14f, C.RED), top = 8) }
        val p = profile
        if (p == null && !loading && loadedAt > 0) joinCard(content) else if (p != null) meCard(content, p)

        // sous-onglets Mondial / Amis
        val seg = hbox().apply { background = rounded(C.CARD2, 22); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        listOf("🌍 Mondial", "👥 Amis").forEachIndexed { i, t ->
            val sel = i == sub
            seg.add(label(t, 14f, if (sel) C.DARK else C.MUTED, weight = 800).apply {
                setPadding(0, dp(10), 0, dp(10)); background = if (sel) rounded(C.GOLD, 18) else null
                setOnClickListener {
                    if (i == 1 && profile == null) Toasts.show(act, "Choisis d'abord un pseudo")
                    else if (i != sub) { sub = i; rows = null; onChanged() }
                }
            }, w = 0, weight = 1f)
        }
        content.add(seg, top = 14)

        if (sub == 1 && p != null) addFriendRow(content)
        if (loading && rows == null) content.add(label("Chargement…", 15f, C.MUTED), top = 18)
        val list = rows ?: emptyList()
        if (!loading && rows != null && list.isEmpty())
            content.add(label(if (sub == 0) "Personne pour l'instant. Sois le premier ! 🥇" else "Ajoute des amis avec leur code 👆", 15f, C.MUTED), top = 18)
        if (list.isNotEmpty()) {
            // podium pour les 3 premiers
            val top3 = list.take(3)
            if (top3.size == 3 && sub == 0) content.add(podium(top3), top = 14)
            val box = vbox().apply { background = rounded(C.CARD, 20); setPadding(dp(8), dp(6), dp(8), dp(6)) }
            for (r in (if (top3.size == 3 && sub == 0) list.drop(3) else list)) box.addView(row(r))
            if (box.childCount > 0) content.add(box, top = 12)
        }
        content.add(label("La série 🔥 compte les jours d'affilée avec les 5 prières faites. Les jours de dispense ne la cassent pas.", 12f, C.MUTED), top = 14)
        if (p != null) content.add(label("Supprimer mon profil", 13f, C.RED).apply { setPadding(0, dp(16), 0, dp(4)); setOnClickListener { confirmDelete() } })
    }

    private fun load() {
        loading = true
        val s = sub
        Cloud.bg({
            Cloud.flush(act)
            val p = Cloud.myProfile(act)
            p to (if (s == 1 && p != null) Cloud.friends(act) else Cloud.global(act))
        }) { res, err ->
            loading = false; error = err; loadedAt = System.currentTimeMillis(); loadedSub = s
            if (res != null) { profile = res.first; rows = res.second }
            onChanged()
        }
    }

    fun refresh() { loadedAt = 0; onChanged() }

    /** Pour les aperçus du design. */
    fun preview(p: Cloud.Profile?, r: List<Cloud.Row>, s: Int = 0) { profile = p; rows = r; sub = s; loadedAt = 1; loadedSub = s }

    private fun joinCard(content: LinearLayout) = with(act) {
        content.add(card().apply {
            background = rounded(C.CARD, 20, C.GOLD)
            addView(label("Rejoins le classement 🔥", 19f, C.WHITE, weight = 800, gravity = Gravity.START))
            addView(label("Choisis un pseudo. Pas d'e-mail, pas de numéro : seuls ton pseudo, ta série et ton nombre de prières sont envoyés.", 14f, C.MUTED, gravity = Gravity.START))
            val e = EditText(act).apply {
                hint = "Pseudo (3 à 20 caractères)"; setTextColor(C.WHITE); setHintTextColor(C.MUTED); textSize = 16f
                inputType = InputType.TYPE_CLASS_TEXT; background = rounded(C.DARK, 14); setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            add(e, top = 12)
            add(button("Rejoindre") {
                val s = e.text.toString().trim()
                if (s.length !in 3..20) { Toasts.show(act, "3 à 20 caractères"); return@button }
                if (!Regex("^[\\p{L}\\p{N}_. -]+$").matches(s)) { Toasts.show(act, "Lettres, chiffres, espace, _ . - seulement"); return@button }
                loading = true; onChanged()
                Cloud.bg({ Cloud.setPseudo(act, s) }) { _, err ->
                    loading = false
                    if (err != null) { error = err; onChanged() } else { error = null; refresh() }
                }
            }, top = 10)
        }, top = 8)
    }

    private fun meCard(content: LinearLayout, p: Cloud.Profile) = with(act) {
        content.add(vbox(Gravity.START).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF1F6B52.toInt(), 0xFF0F4334.toInt())).apply { cornerRadius = dpf(22f); setStroke(dp(1), C.LINE) }
            setPadding(dp(18), dp(16), dp(18), dp(16))
            val head = hbox()
            head.add(vbox(Gravity.START).apply {
                add(label(p.pseudo, 20f, C.WHITE, weight = 800, gravity = Gravity.START))
                add(label("Rang #${p.rank} sur ${p.players}", 14f, C.GOLD, weight = 700, gravity = Gravity.START))
            }, w = 0, weight = 1f)
            head.add(label("#${p.rank}", 30f, C.GOLD, weight = 800))
            addView(head)
            val row = hbox().apply { setPadding(0, dp(12), 0, dp(4)) }
            row.add(stat("🔥 ${p.streak}", "série"), w = 0, weight = 1f)
            row.add(stat("🏅 ${p.best}", "record"), w = 0, weight = 1f)
            row.add(stat("🤲 ${p.total}", "prières"), w = 0, weight = 1f)
            addView(row)
            val code = hbox().apply { setPadding(dp(12), dp(8), dp(6), dp(8)); background = rounded(0x33000000, 14) }
            code.add(label("Code ami : ${p.friendCode}", 15f, C.WHITE, weight = 700, gravity = Gravity.START), w = 0, weight = 1f)
            code.add(label("📤 Partager", 13f, C.GOLD, weight = 800).apply {
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener {
                    val txt = "Ajoute-moi sur Salat Lock pour qu'on se motive à prier 🤲 Mon code ami : ${p.friendCode}\n${BuildConfig.SITE_URL}"
                    act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Partager mon code"))
                }
            })
            add(code, top = 8)
        }, top = 8)
    }

    private fun stat(big: String, small: String) = act.vbox().apply {
        add(act.label(big, 20f, C.GOLD, weight = 800)); add(act.label(small, 12f, C.MUTED))
    }

    private fun podium(top3: List<Cloud.Row>) = with(act) {
        hbox().apply {
            gravity = Gravity.BOTTOM
            val order = listOf(top3[1] to 2, top3[0] to 1, top3[2] to 3)
            for ((r, place) in order) {
                val h = when (place) { 1 -> 120; 2 -> 96; else -> 80 }
                val col = vbox().apply {
                    add(label(listOf("🥇", "🥈", "🥉")[place - 1], 28f))
                    add(label(r.pseudo, 13.5f, C.WHITE, weight = 800).apply { maxLines = 1 }, top = 2)
                    add(label("🔥 ${r.streak}", 13f, C.GOLD, weight = 800))
                    add(View(act).apply { background = rounded(if (r.isMe) C.GREEN else if (place == 1) C.GOLD else C.CARD, 14) }, top = 6, h = dp(h))
                }
                add(col, w = 0, weight = 1f)
                if (place != 3) addView(View(act), lp(dp(8), 1))
            }
        }
    }

    private fun addFriendRow(content: LinearLayout) = with(act) {
        val r = hbox()
        val e = EditText(act).apply {
            hint = "Code ami (6 caractères)"; setTextColor(C.WHITE); setHintTextColor(C.MUTED); textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            background = rounded(C.CARD, 14); setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        r.add(e, w = 0, weight = 1f)
        r.addView(View(act), lp(dp(8), 1))
        r.add(button("Ajouter", C.GOLD, C.DARK, 15f) {
            val code = e.text.toString().trim()
            if (code.length < 4) return@button
            Cloud.bg({ Cloud.addFriend(act, code) }) { name, err ->
                if (err != null) Toasts.show(act, err) else { Toasts.show(act, "✅ $name ajouté !"); refresh() }
            }
        })
        content.add(r, top = 12)
    }

    private fun row(r: Cloud.Row) = with(act) {
        hbox().apply {
            setPadding(dp(10), dp(11), dp(10), dp(11))
            if (r.isMe) background = rounded(C.GREEN, 14)
            add(label(when (r.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${r.rank}" }, 15f, C.MUTED, weight = 800), w = dp(40))
            add(label(r.pseudo + if (r.isMe) " (moi)" else "", 16f, C.WHITE, weight = if (r.isMe) 800 else 700, gravity = Gravity.START), w = 0, weight = 1f)
            add(label("🔥 ${r.streak}", 15f, C.GOLD, weight = 800, gravity = Gravity.END), w = dp(70))
            add(label("${r.total}", 13f, C.MUTED, gravity = Gravity.END), w = dp(48))
            if (sub == 1 && !r.isMe) setOnLongClickListener {
                AlertDialog.Builder(act).setMessage("Retirer ${r.pseudo} de tes amis ?")
                    .setPositiveButton("Retirer") { _, _ -> Cloud.bg({ Cloud.removeFriend(act, r.pseudo) }) { _, _ -> refresh() } }
                    .setNegativeButton("Annuler", null).show(); true
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(act).setTitle("Supprimer mon profil ?")
            .setMessage("Ton pseudo, ta série et ton historique en ligne seront effacés définitivement. Tes données sur le téléphone restent.")
            .setPositiveButton("Supprimer") { _, _ ->
                Cloud.bg({ Cloud.deleteAccount(act) }) { _, err ->
                    if (err != null) Toasts.show(act, err) else { profile = null; rows = null; sub = 0; Toasts.show(act, "Profil supprimé"); refresh() }
                }
            }.setNegativeButton("Annuler", null).show()
    }
}
