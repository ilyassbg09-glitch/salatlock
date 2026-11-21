package com.salatlock.app

import android.content.Context
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

object PrayerCalc {

    /** Méthodes de calcul proposées (clé -> nom affiché). */
    val METHODS = linkedMapOf(
        "MWL" to "Ligue islamique mondiale (18° / 17°)",
        "FRANCE12" to "France – UOIF (12° / 12°)",
        "FRANCE15" to "France – 15° / 15°",
        "FRANCE18" to "France – Mosquée de Paris (18° / 18°)",
        "MAROC" to "Maroc – Habous (19° / 17°)",
        "ALGERIE" to "Algérie (18° / 17°)",
        "TUNISIE" to "Tunisie (18° / 18°)",
        "EGYPT" to "Égypte (19,5° / 17,5°)",
        "UMM_AL_QURA" to "Umm al-Qura (La Mecque)",
        "ISNA" to "Amérique du Nord – ISNA (15° / 15°)",
        "KARACHI" to "Karachi (18° / 18°)",
        "DUBAI" to "Dubaï",
        "KUWAIT" to "Koweït",
        "QATAR" to "Qatar",
        "SINGAPORE" to "Singapour",
    )

    private fun custom(fajr: Double, isha: Double): CalculationParameters =
        CalculationMethod.OTHER.parameters.apply { fajrAngle = fajr; ishaAngle = isha }

    private fun params(key: String): CalculationParameters = when (key) {
        "FRANCE12" -> custom(12.0, 12.0)
        "FRANCE15" -> custom(15.0, 15.0)
        "FRANCE18" -> custom(18.0, 18.0)
        "MAROC" -> custom(19.0, 17.0)
        "ALGERIE" -> custom(18.0, 17.0)
        "TUNISIE" -> custom(18.0, 18.0)
        "EGYPT" -> CalculationMethod.EGYPTIAN.parameters
        "UMM_AL_QURA" -> CalculationMethod.UMM_AL_QURA.parameters
        "ISNA" -> CalculationMethod.NORTH_AMERICA.parameters
        "KARACHI" -> CalculationMethod.KARACHI.parameters
        "DUBAI" -> CalculationMethod.DUBAI.parameters
        "KUWAIT" -> CalculationMethod.KUWAIT.parameters
        "QATAR" -> CalculationMethod.QATAR.parameters
        "SINGAPORE" -> CalculationMethod.SINGAPORE.parameters
        else -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
    }

    class DayTimes(val date: LocalDate, val times: Map<Prayer, Long>, val sunrise: Long)

    fun forDate(ctx: Context, date: LocalDate): DayTimes? {
        val p = Prefs(ctx)
        if (!p.hasLocation) return null
        val params = params(p.method)
        params.madhab = if (p.hanafi) Madhab.HANAFI else Madhab.SHAFI
        val pt = PrayerTimes(
            Coordinates(p.lat, p.lng),
            DateComponents(date.year, date.monthValue, date.dayOfMonth),
            params
        )
        val m = mapOf(
            Prayer.FAJR to pt.fajr.time,
            Prayer.DHUHR to pt.dhuhr.time,
            Prayer.ASR to pt.asr.time,
            Prayer.MAGHRIB to pt.maghrib.time,
            Prayer.ISHA to pt.isha.time,
        )
        return DayTimes(date, m, pt.sunrise.time)
    }

    fun today(ctx: Context) = forDate(ctx, LocalDate.now())

    /** La prière « en cours » : son heure est passée et la suivante pas encore arrivée. */
    class Window(val prayer: Prayer, val day: LocalDate, val start: Long, val end: Long)

    fun windows(ctx: Context, around: LocalDate = LocalDate.now()): List<Window> {
        val list = mutableListOf<Window>()
        for (offset in -1L..1L) {
            val d = around.plusDays(offset)
            val t = forDate(ctx, d) ?: return emptyList()
            val next = forDate(ctx, d.plusDays(1)) ?: return emptyList()
            val ps = Prayer.values()
            for ((i, pr) in ps.withIndex()) {
                val start = t.times[pr]!!
                val end = if (i < ps.size - 1) t.times[ps[i + 1]]!! else next.times[Prayer.FAJR]!!
                list += Window(pr, d, start, end)
            }
        }
        return list
    }

    fun currentWindow(ctx: Context, now: Long = System.currentTimeMillis()): Window? =
        windows(ctx).firstOrNull { now >= it.start && now < it.end }

    fun nextPrayer(ctx: Context, now: Long = System.currentTimeMillis()): Window? =
        windows(ctx).filter { it.start > now }.minByOrNull { it.start }

    /** Direction de la Qibla en degrés depuis le nord. */
    fun qiblaBearing(lat: Double, lng: Double): Double {
        val kLat = Math.toRadians(21.4225)
        val kLng = Math.toRadians(39.8262)
        val phi = Math.toRadians(lat)
        val dl = kLng - Math.toRadians(lng)
        val b = Math.toDegrees(atan2(sin(dl), cos(phi) * tan(kLat) - sin(phi) * cos(dl)))
        return (b + 360) % 360
    }

    fun fmt(t: Long): String =
        java.time.Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalTime()
            .let { String.format("%02d:%02d", it.hour, it.minute) }
}
