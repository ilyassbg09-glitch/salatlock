package com.salatlock.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class SettingsActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var content: LinearLayout
    private lateinit var locLabel: android.widget.TextView

    private val cities = linkedMapOf(
        "— Choisir une ville —" to null,
        "Paris" to (48.8566 to 2.3522), "Lyon" to (45.7640 to 4.8357), "Marseille" to (43.2965 to 5.3698),
        "Lille" to (50.6292 to 3.0573), "Toulouse" to (43.6047 to 1.4442), "Nice" to (43.7102 to 7.2620),
        "Strasbourg" to (48.5734 to 7.7521), "Bordeaux" to (44.8378 to -0.5792), "Nantes" to (47.2184 to -1.5536),
        "Bruxelles" to (50.8503 to 4.3517), "Genève" to (46.2044 to 6.1432), "Montréal" to (45.5019 to -73.5674),
        "Casablanca" to (33.5731 to -7.5898), "Rabat" to (34.0209 to -6.8416), "Marrakech" to (31.6295 to -7.9811),
        "Fès" to (34.0181 to -5.0078), "Tanger" to (35.7595 to -5.8340), "Agadir" to (30.4278 to -9.5981),
        "Oujda" to (34.6814 to -1.9086), "Alger" to (36.7538 to 3.0588), "Oran" to (35.6971 to -0.6308),
        "Tunis" to (36.8065 to 10.1815), "Dakar" to (14.7167 to -17.4677), "Istanbul" to (41.0082 to 28.9784),
        "La Mecque" to (21.4225 to 39.8262), "Médine" to (24.4672 to 39.6111),
    )

    private val askLoc = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) fetchLocation() else Toasts.show(this, "Autorisation refusée")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        content = vbox(Gravity.START).apply { setPadding(dp(18), dp(36), dp(18), dp(36)) }
        setContentView(ScrollView(this).apply { background = bgGradient(); addView(content) })
        build()
    }

    override fun onPause() {
        super.onPause()
        Scheduler.reschedule(this)
    }

    private fun section(t: String) = content.add(label(t, 18f, C.GOLD, bold = true, gravity = Gravity.START), top = 22)

    private fun build() {
        content.removeAllViews()
        content.add(label("⚙️ Réglages", 28f, C.GOLD, bold = true, gravity = Gravity.START))

        // ---- Position ----
        section("📍 Position")
        locLabel = label(locText(), 14f, C.MUTED, gravity = Gravity.START)
        content.add(locLabel, top = 4)
        content.add(button("📡 Utiliser ma position GPS") { askLocation() }, top = 10)

        val citySp = Spinner(this)
        citySp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cities.keys.toList())
        citySp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val name = cities.keys.toList()[pos]
                val c = cities[name] ?: return
                saveLocation(c.first, c.second, name)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        content.add(citySp.apply { background = rounded(C.CARD, 14); setPadding(dp(8), dp(10), dp(8), dp(10)) }, top = 10)

        val manual = hbox()
        val latE = edit("Latitude", if (prefs.hasLocation) "%.4f".format(java.util.Locale.US, prefs.lat) else "")
        val lngE = edit("Longitude", if (prefs.hasLocation) "%.4f".format(java.util.Locale.US, prefs.lng) else "")
        manual.add(latE, w = 0, weight = 1f)
        manual.addView(View(this), lp(dp(8), 1))
        manual.add(lngE, w = 0, weight = 1f)
        manual.addView(View(this), lp(dp(8), 1))
        manual.add(button("OK", C.CARD, C.WHITE, 15f) {
            val la = latE.text.toString().replace(',', '.').toDoubleOrNull()
            val lo = lngE.text.toString().replace(',', '.').toDoubleOrNull()
            if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) Toasts.show(this, "Coordonnées invalides")
            else saveLocation(la, lo, "Coordonnées manuelles")
        })
        content.add(label("Ou entre les coordonnées :", 13f, C.MUTED, gravity = Gravity.START), top = 10)
        content.add(manual, top = 4)

        // ---- Calcul ----
        section("🧮 Méthode de calcul")
        val keys = PrayerCalc.METHODS.keys.toList()
        val methodSp = Spinner(this)
        methodSp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, PrayerCalc.METHODS.values.toList())
        methodSp.setSelection(keys.indexOf(prefs.method).coerceAtLeast(0))
        methodSp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { prefs.method = keys[pos] }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        content.add(methodSp.apply { background = rounded(C.CARD, 14); setPadding(dp(8), dp(10), dp(8), dp(10)) }, top = 6)
        content.add(switch("Asr selon l'école hanafite", prefs.hanafi) { prefs.hanafi = it }, top = 8)

        // ---- Blocage ----
        section("🔒 Blocage")
        content.add(switch("Blocage activé", prefs.masterEnabled) { prefs.masterEnabled = it }, top = 4)
        content.add(slider("Délai après l'adhan avant de bloquer", 0, 60, prefs.graceMin, "min") { prefs.graceMin = it }, top = 8)
        content.add(slider("Durée du compte à rebours pour prier", 3, 30, prefs.countdownMin, "min") { prefs.countdownMin = it }, top = 8)
        content.add(slider("Sensibilité du scan du lavabo", 0, 100, prefs.sensitivity, "%") { prefs.sensitivity = it }, top = 8)
        content.add(label("Si le lavabo n'est pas reconnu, augmente la sensibilité.", 12f, C.MUTED, gravity = Gravity.START), top = 2)

        section("🔊 Adhan")
        content.add(switch("Jouer l'adhan à l'heure de la prière", prefs.adhanEnabled) { prefs.adhanEnabled = it }, top = 4)
        content.add(switch("Aussi à Fajr", prefs.adhanAtFajr) { prefs.adhanAtFajr = it }, top = 2)
        content.add(label("Je n'ai pas encore d'adhan de Fajr libre de droits (avec « as-salâtu khayrun min an-nawm »). Si tu actives cette option, c'est l'adhan habituel qui sera joué.", 12f, C.MUTED, gravity = Gravity.START), top = 2)
        content.add(switch("Même en mode silencieux / vibreur", prefs.adhanInSilent) { prefs.adhanInSilent = it }, top = 2)
        val ad = hbox()
        ad.add(button("▶️ Écouter", C.CARD, C.WHITE, 15f) { AdhanService.preview(this) }, w = 0, weight = 1f)
        ad.addView(View(this), lp(dp(10), 1))
        ad.add(button("⏹ Arrêter", C.CARD, C.WHITE, 15f) { AdhanService.stop(this) }, w = 0, weight = 1f)
        content.add(ad, top = 8)
        content.add(label("Un adhan différent chaque jour. Le volume suit le volume « médias » du téléphone.", 12f, C.MUTED, gravity = Gravity.START), top = 4)

        section("🕌 Prières qui bloquent le téléphone")
        for (p in Prayer.values()) {
            content.add(CheckBox(this).apply {
                text = "${p.fr} · ${p.ar}"; textSize = 16f; setTextColor(C.WHITE)
                buttonTintList = android.content.res.ColorStateList.valueOf(C.GOLD)
                isChecked = prefs.isPrayerEnabled(p)
                setOnCheckedChangeListener { _, b -> prefs.setPrayerEnabled(p, b) }
            }, top = 2)
        }

        section("ℹ️ À savoir")
        content.add(label(
            "• Les appels entrants et le bouton Urgence (112) ne sont jamais bloqués.\n" +
            "• Bouton Urgence : 2 fois par jour avec 3 min sans blocage. Au-delà, appeler reste toujours possible (le blocage revient après 45 s, sauf pendant un appel).\n" +
            "• 3 pauses d'urgence de 5 min par jour (appui long 3 s).\n" +
            "• Après le scan du lavabo : au moins 3 min de prière avant le déblocage.\n" +
            "• Tout fonctionne sans internet : heures, Qibla et reconnaissance du lavabo.\n" +
            "• Vérifie tes heures avec ta mosquée et change la méthode si besoin.\n" +
            "• Classement : seuls ton pseudo, ta série et ton nombre de prières sont envoyés, et seulement si tu le rejoins. Un compteur anonyme d'installations est aussi envoyé (aucune donnée personnelle).",
            14f, C.MUTED, gravity = Gravity.START), top = 6)

        section("🎙️ Crédits des adhans")
        content.add(label(
            "Enregistrements libres de droits, venant de Wikimedia Commons :\n" +
            "• « Beautiful adhan » — Adam-synagda — CC0 (domaine public)\n" +
            "• « Azan » — Andrewler — CC BY-SA 4.0 (coupé avant la dua)\n" +
            "• « The Adhan - Muslim Call to Prayer » — Atcovi (récitation d'Aaqib Azeez) — CC BY-SA 4.0\n" +
            "Les versions modifiées (volume, coupe) sont diffusées sous la même licence CC BY-SA 4.0.",
            13f, C.MUTED, gravity = Gravity.START), top = 6)
    }

    private fun locText() =
        if (prefs.hasLocation) "Actuelle : ${prefs.cityLabel.ifBlank { "?" }} (%.3f, %.3f)".format(java.util.Locale.US, prefs.lat, prefs.lng)
        else "Aucune position enregistrée"

    private fun saveLocation(la: Double, lo: Double, name: String) {
        prefs.lat = la; prefs.lng = lo; prefs.cityLabel = name
        locLabel.text = locText()
        Toasts.show(this, "Position enregistrée : $name")
    }

    private fun askLocation() {
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (perms.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) fetchLocation()
        else askLoc.launch(perms)
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        val lm = getSystemService(LocationManager::class.java)
        val last: Location? = lm.getProviders(true).mapNotNull {
            try { lm.getLastKnownLocation(it) } catch (_: Exception) { null }
        }.maxByOrNull { it.time }
        if (last != null) saveLocation(last.latitude, last.longitude, "Position GPS")
        else Toasts.show(this, "Recherche de la position…")
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> { if (last == null) Toasts.show(this, "Active la localisation du téléphone"); return }
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, androidx.core.content.ContextCompat.getMainExecutor(this)) { loc ->
                    if (loc != null) saveLocation(loc.latitude, loc.longitude, "Position GPS")
                }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, { loc -> saveLocation(loc.latitude, loc.longitude, "Position GPS") }, mainLooper)
            }
        } catch (_: Exception) {}
    }

    private fun edit(hint: String, value: String) = EditText(this).apply {
        this.hint = hint; setText(value); setTextColor(C.WHITE); setHintTextColor(C.MUTED); textSize = 15f
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        background = rounded(C.CARD, 14); setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun switch(t: String, v: Boolean, on: (Boolean) -> Unit) = Switch(this).apply {
        text = t; textSize = 16f; setTextColor(C.WHITE); isChecked = v
        setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, b -> on(b) }
    }

    private fun slider(t: String, min: Int, max: Int, v: Int, unit: String, on: (Int) -> Unit) = vbox(Gravity.START).apply {
        val l = label("$t : $v $unit", 15f, C.WHITE, gravity = Gravity.START)
        addView(l)
        addView(SeekBar(context).apply {
            this.max = max - min; progress = v - min
            progressTintList = android.content.res.ColorStateList.valueOf(C.GOLD)
            thumbTintList = android.content.res.ColorStateList.valueOf(C.GOLD)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    l.text = "$t : ${p + min} $unit"; if (fromUser) on(p + min)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, lp(top = dp(4)))
    }
}
