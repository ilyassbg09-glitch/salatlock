package com.salatlock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        val p = Prayer.of(intent.getStringExtra("prayer"))
        val day = intent.getStringExtra("day")
        if (p != null && day != null && prefs.masterEnabled && !prefs.isExcused &&
            prefs.isPrayerEnabled(p) && !prefs.isDone(day, p)
        ) {
            when (intent.getIntExtra("type", 0)) {
                Scheduler.TYPE_NOTIFY -> { Notifs.adhan(ctx, p, day, prefs.graceMin); AdhanService.maybePlay(ctx, p) }
                Scheduler.TYPE_LOCK -> { if (prefs.graceMin == 0) AdhanService.maybePlay(ctx, p); Scheduler.lock(ctx, p, day) }
            }
        }
        Scheduler.reschedule(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.reschedule(ctx)
    }
}
