package com.salatlock.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate

/** Programme le prochain réveil : rappel à l'adhan, puis blocage après le délai de grâce. */
object Scheduler {
    const val TYPE_NOTIFY = 1
    const val TYPE_LOCK = 2

    private class Ev(val t: Long, val type: Int, val p: Prayer, val day: LocalDate)

    fun reschedule(ctx: Context) {
        val prefs = Prefs(ctx)
        checkNow(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val base = Intent(ctx, AlarmReceiver::class.java)
        if (!prefs.masterEnabled || !prefs.hasLocation) {
            am.cancel(PendingIntent.getBroadcast(ctx, 0, base, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return
        }
        val now = System.currentTimeMillis()
        val grace = prefs.graceMin * 60_000L
        val evs = mutableListOf<Ev>()
        for (off in 0L..2L) {
            val d = LocalDate.now().plusDays(off)
            val t = PrayerCalc.forDate(ctx, d) ?: continue
            for (p in Prayer.values()) {
                if (!prefs.isPrayerEnabled(p)) continue
                val adhan = t.times[p]!!
                if (grace > 0) evs += Ev(adhan, TYPE_NOTIFY, p, d)
                evs += Ev(adhan + grace, TYPE_LOCK, p, d)
            }
        }
        val next = evs.filter { it.t > now + 1000 }.minByOrNull { it.t } ?: return
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .putExtra("type", next.type)
            .putExtra("prayer", next.p.name)
            .putExtra("day", next.day.toString())
        val pi = PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            val show = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(next.t, show), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.t, pi)
        }
    }

    /** Rattrapage : si on devrait être bloqué maintenant (réveil raté, redémarrage…), on bloque. */
    fun checkNow(ctx: Context) {
        val prefs = Prefs(ctx)
        if (prefs.isLockedOrPraying) {
            LockService.start(ctx)
            return
        }
        if (!prefs.masterEnabled || prefs.isExcused) return
        val w = PrayerCalc.currentWindow(ctx) ?: return
        if (!prefs.isPrayerEnabled(w.prayer)) return
        if (prefs.isDone(w.day.toString(), w.prayer)) return
        if (System.currentTimeMillis() >= w.start + prefs.graceMin * 60_000L) {
            lock(ctx, w.prayer, w.day.toString())
        }
    }

    fun lock(ctx: Context, p: Prayer, day: String, test: Boolean = false) {
        val prefs = Prefs(ctx)
        if (prefs.countdownEnd > 0) return // déjà en train de prier
        if (prefs.lockPrayer == null) prefs.lockSince = System.currentTimeMillis()
        prefs.lockPrayer = p
        prefs.lockDay = day
        prefs.lockIsTest = test
        LockService.start(ctx)
    }
}
