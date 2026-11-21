package com.salatlock.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Notifs {
    const val CH_ADHAN = "adhan"
    const val CH_LOCK = "lock"
    const val CH_INFO = "info"
    const val ID_LOCK = 1
    const val ID_ADHAN = 2
    const val ID_INFO = 3

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ADHAN, "Heure de la prière", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rappel à l'heure de l'adhan"
            enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_LOCK, "Verrouillage", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_INFO, "Infos", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun scanIntent(ctx: Context, p: Prayer?, day: String?, test: Boolean = false): PendingIntent {
        val i = Intent(ctx, ScanActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("prayer", p?.name).putExtra("day", day).putExtra("test", test)
        return PendingIntent.getActivity(ctx, 10, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun adhan(ctx: Context, p: Prayer, day: String, graceMin: Int) {
        channels(ctx)
        if (!canPost(ctx)) return
        val n = Notification.Builder(ctx, CH_ADHAN)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("🕌 C'est l'heure de ${p.fr} (${p.ar})")
            .setContentText("Ton téléphone se bloque dans $graceMin min. Va faire tes ablutions !")
            .setAutoCancel(true)
            .setContentIntent(scanIntent(ctx, p, day))
            .addAction(Notification.Action.Builder(null, "🚰 Je vais prier", scanIntent(ctx, p, day)).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_ADHAN, n)
    }

    fun info(ctx: Context, title: String, text: String) {
        channels(ctx)
        if (!canPost(ctx)) return
        val open = PendingIntent.getActivity(ctx, 11, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(open).build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_INFO, n)
    }

    fun lockNotification(ctx: Context, text: String, p: Prayer?, day: String?): Notification {
        channels(ctx)
        return Notification.Builder(ctx, CH_LOCK)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Salat Lock")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(scanIntent(ctx, p, day))
            .build()
    }
}
