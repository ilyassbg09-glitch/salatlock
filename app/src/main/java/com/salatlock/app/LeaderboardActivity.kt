package com.salatlock.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity

/** Classement en ligne : mondial + amis. Commun aux Android et aux iPhone. */
class LeaderboardActivity : ComponentActivity() {

    private lateinit var content: LinearLayout
    private var tab = 0 // 0 = mondial, 1 = amis
    private var profile: Cloud.Profile? = null
    private var rows: List<Cloud.Row>? = null
    private var error: String? = null
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = vbox().apply { setPadding(dp(18), dp(36), dp(18), dp(36)) }
        setContentView(ScrollView(this).apply { background = bgGradient(); addView(content) })
    }

    override fun onResume() { super.onResume(); load() }

    private fun load() {
        loading = true; build()
        Cloud.bg({
            Cloud.flush(this)
            val p = Cloud.myProfile(this)
            val r = if (p == null) Cloud.global(this) else if (tab == 0) Cloud.global(this) else Cloud.friends(this)
            p to r
        }) { res, err ->
            loading = false; error = err
            if (res != null) { profile = res.first; rows = res.second }
            build()
        }
    }

    private fun build() {
        content.removeAllViews()
        content.add(label("🏆 Classement", 30f, C.GOLD, bold = true))
        content.add(label("Android et iPhone · un seul classement", 13f, C.MUTED), top = 2)
        error?.let { content.add(label("⚠️ $it", 15f, C.RED), top = 12) }

        val p = profile
        if (p == null && !loading) joinCard() else if (p != null) meCard(p)

        val tabs = hbox()
        tabs.add(button("🌍 Mondial", if (tab == 0) C.GOLD else C.CARD, if (tab == 0) C.DARK else C.WHITE, 15f) { tab = 0; load() }, w = 0, weight = 1f)
        tabs.addView(android.view.View(this), lp(dp(10), 1))
        tabs.add(button("👥 Amis", if (tab == 1) C.GOLD else C.CARD, if (tab == 1) C.DARK else C.WHITE, 15f) {
            if (profile == null) Toasts.show(this, "Choisis d'abord un pseudo") else { tab = 1; load() }
        }, w = 0, weight = 1f)
        content.add(tabs, top = 18)

        if (tab == 1 && p != null) addFriendRow()

        if (loading && rows == null) content.add(label("Chargement…", 15f, C.MUTED), top = 16)
        val list = rows ?: emptyList()
        if (!loading && list.isEmpty()) content.add(label(if (tab == 0) "Personne pour l'instant. Sois le premier ! 🥇" else "Ajoute des amis avec leur code 👆", 15f, C.MUTED), top = 16)
        val box = card().apply { setPadding(dp(10), dp(6), dp(10), dp(6)) }
        for (r in list) box.addView(row(r))
        if (list.isNotEmpty()) content.add(box, top = 12)

        content.add(label("La série 🔥 compte les jours d'affilée avec les 5 prières faites. Les jours de dispense ne la cassent pas.", 12f, C.MUTED), top = 14)
        if (p != null) content.add(label("Supprimer mon profil", 13f, C.RED).apply {
            setPadding(0, dp(18), 0, dp(4)); setOnClickListener { confirmDelete() }
        })
    }

    private fun joinCard() {
        content.add(card().apply {
            addView(label("Rejoins le classement 🔥", 19f, C.WHITE, bold = true, gravity = Gravity.START))
            addView(label("Choisis un pseudo. Pas d'e-mail, pas de numéro : seuls ton pseudo, ta série et ton nombre de prières sont envoyés.", 14f, C.MUTED, gravity = Gravity.START))
            val e = EditText(this@LeaderboardActivity).apply {
                hint = "Pseudo (3 à 20 caractères)"; setTextColor(C.WHITE); setHintTextColor(C.MUTED); textSize = 16f
                inputType = InputType.TYPE_CLASS_TEXT; background = rounded(C.DARK, 14); setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            add(e, top = 12)
            add(button("Rejoindre") {
                val s = e.text.toString().trim()
                if (s.length !in 3..20) { Toasts.show(this@LeaderboardActivity, "3 à 20 caractères"); return@button }
                if (!Regex("^[\\p{L}\\p{N}_. -]+$").matches(s)) { Toasts.show(this@LeaderboardActivity, "Lettres, chiffres, espace, _ . - seulement"); return@button }
                loading = true; build()
                Cloud.bg({ Cloud.setPseudo(this@LeaderboardActivity, s) }) { _, err ->
                    if (err != null) { loading = false; error = err; build() } else { error = null; load() }
                }
            }, top = 10)
        }, top = 16)
    }

    private fun meCard(p: Cloud.Profile) {
        content.add(card().apply {
            addView(label("${p.pseudo}", 20f, C.WHITE, bold = true, gravity = Gravity.START))
            addView(label("Rang #${p.rank} sur ${p.players}", 15f, C.GOLD, bold = true, gravity = Gravity.START))
            val row = hbox().apply { setPadding(0, dp(10), 0, dp(4)) }
            row.add(stat("🔥 ${p.streak}", "série"), w = 0, weight = 1f)
            row.add(stat("🏅 ${p.best}", "record"), w = 0, weight = 1f)
            row.add(stat("🤲 ${p.total}", "prières"), w = 0, weight = 1f)
            addView(row)
            val code = hbox().apply { setPadding(0, dp(8), 0, 0) }
            code.add(label("Mon code ami : ${p.friendCode}", 15f, C.WHITE, gravity = Gravity.START), w = 0, weight = 1f)
            code.add(button("📤", C.DARK, C.WHITE, 15f) {
                val txt = "Ajoute-moi sur Salat Lock pour qu'on se motive à prier 🤲 Mon code ami : ${p.friendCode}\n${BuildConfig.SITE_URL}"
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Partager mon code"))
            })
            addView(code)
        }, top = 16)
    }

    private fun stat(big: String, small: String) = vbox().apply {
        add(label(big, 22f, C.GOLD, bold = true)); add(label(small, 12f, C.MUTED))
    }

    private fun addFriendRow() {
        val r = hbox()
        val e = EditText(this).apply {
            hint = "Code ami (6 caractères)"; setTextColor(C.WHITE); setHintTextColor(C.MUTED); textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            background = rounded(C.CARD, 14); setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        r.add(e, w = 0, weight = 1f)
        r.addView(android.view.View(this), lp(dp(8), 1))
        r.add(button("Ajouter", C.GOLD, C.DARK, 15f) {
            val code = e.text.toString().trim()
            if (code.length < 4) return@button
            Cloud.bg({ Cloud.addFriend(this, code) }) { name, err ->
                if (err != null) Toasts.show(this, err) else { Toasts.show(this, "✅ $name ajouté !"); load() }
            }
        })
        content.add(r, top = 12)
    }

    private fun row(r: Cloud.Row) = hbox().apply {
        setPadding(dp(8), dp(10), dp(8), dp(10))
        if (r.isMe) background = rounded(C.GREEN, 12)
        val medal = when (r.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${r.rank}" }
        add(label(medal, 16f, C.WHITE, bold = true), w = dp(48))
        add(label(r.pseudo + if (r.isMe) " (moi)" else "", 16f, C.WHITE, bold = r.isMe, gravity = Gravity.START), w = 0, weight = 1f)
        add(label("🔥 ${r.streak}", 16f, C.GOLD, bold = true, gravity = Gravity.END), w = dp(70))
        add(label("${r.total}", 14f, C.MUTED, gravity = Gravity.END), w = dp(56))
        if (tab == 1 && !r.isMe) setOnLongClickListener {
            AlertDialog.Builder(this@LeaderboardActivity).setMessage("Retirer ${r.pseudo} de tes amis ?")
                .setPositiveButton("Retirer") { _, _ -> Cloud.bg({ Cloud.removeFriend(this@LeaderboardActivity, r.pseudo) }) { _, _ -> load() } }
                .setNegativeButton("Annuler", null).show(); true
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this).setTitle("Supprimer mon profil ?")
            .setMessage("Ton pseudo, ta série et ton historique en ligne seront effacés définitivement. Tes données sur le téléphone restent.")
            .setPositiveButton("Supprimer") { _, _ ->
                Cloud.bg({ Cloud.deleteAccount(this) }) { _, err ->
                    if (err != null) Toasts.show(this, err) else { profile = null; rows = null; tab = 0; Toasts.show(this, "Profil supprimé"); load() }
                }
            }.setNegativeButton("Annuler", null).show()
    }
}
