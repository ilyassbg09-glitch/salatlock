package com.salatlock.app

import android.content.Intent
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Aperçus du design (sans téléphone). Lancer : ./gradlew recordPaparazziDebug */
class DesignTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    private val noop = object : MainActions {
        override fun selectTab(i: Int) {}; override fun prayNow() {}; override fun openSettings() {}; override fun testLock() {}
        override fun excusedDialog() {}; override fun share() {}; override fun openContract() {}; override fun openUrl(url: String) {}
        override fun openIntent(i: Intent) {}; override fun askPermission(p: String) {}
    }

    private fun shot(tab: Int) {
        val ctx = paparazzi.context
        val p = Prefs(ctx)
        p.lat = 48.8566; p.lng = 2.3522; p.cityLabel = "Paris"; p.method = "FRANCE12"; p.contractDate = 1_795_400_000_000L
        val today = LocalDate.now().toString()
        p.markDone(today, Prayer.FAJR); p.markDone(today, Prayer.DHUHR)
        val board = LeaderboardTab(ctx, autoLoad = false)
        board.preview(Cloud.Profile("Ilyass", "AB12CD", 86, 12, 19, 4, 248), listOf(
            Cloud.Row(1, "Yassine", 41, 210, 41, false), Cloud.Row(2, "Sara", 37, 190, 37, false), Cloud.Row(3, "Moussa", 30, 160, 33, false),
            Cloud.Row(4, "Ilyass", 12, 86, 19, true), Cloud.Row(5, "Inès", 9, 60, 14, false), Cloud.Row(6, "Karim", 6, 44, 10, false)))
        val screen = MainScreen(ctx, noop, board)
        val (root, tabBar, body) = screen.createRoot()
        screen.render(tab, tabBar, body)
        screen.countdownText?.text = "dans 1 h 12 min"
        paparazzi.snapshot(root, "onglet$tab")
    }

    @Test fun accueil() = shot(0)
    @Test fun classement() = shot(1)
    @Test fun qibla() = shot(2)
    @Test fun plus() = shot(3)
}
