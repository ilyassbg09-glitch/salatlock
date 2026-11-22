package com.salatlock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import java.time.LocalDate

/**
 * Joue l'adhan à l'heure de la prière (un enregistrement différent chaque jour).
 * Enregistrements libres de droits : voir CREDITS dans SettingsActivity.
 */
class AdhanService : Service() {
    companion object {
        private const val ACTION_STOP = "stop"
        private const val CH = "adhan_play"
        private const val ID = 4
        private val TRACKS = intArrayOf(R.raw.adhan_1, R.raw.adhan_2, R.raw.adhan_3)

        /** Lance l'adhan si les réglages le permettent. */
        fun maybePlay(ctx: Context, p: Prayer) {
            val prefs = Prefs(ctx)
            if (!prefs.adhanEnabled) return
            if (p == Prayer.FAJR && !prefs.adhanAtFajr) return
            val am = ctx.getSystemService(AudioManager::class.java)
            if (!prefs.adhanInSilent && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            val i = Intent(ctx, AdhanService::class.java).putExtra("prayer", p.name)
            try { ctx.startForegroundService(i) } catch (_: Exception) {}
        }

        fun stop(ctx: Context) {
            try { ctx.startService(Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP)) } catch (_: Exception) {}
        }

        /** Pour l'écouter depuis les réglages. */
        fun preview(ctx: Context) {
            try { ctx.startForegroundService(Intent(ctx, AdhanService::class.java).putExtra("prayer", Prayer.DHUHR.name).putExtra("preview", true)) } catch (_: Exception) {}
        }
    }

    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopAll(); return START_NOT_STICKY }
        val p = Prayer.of(intent?.getStringExtra("prayer")) ?: Prayer.DHUHR
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Adhan", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(this, 20, Intent(this, AdhanService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("🔊 Adhan · ${p.fr} · ${p.ar}")
            .setContentText("Appuie sur Arrêter pour couper le son")
            .setOngoing(true)
            .setDeleteIntent(stopPi)
            .addAction(Notification.Action.Builder(null, "⏹ Arrêter", stopPi).build())
            .setContentIntent(stopPi)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, n)

        player?.release()
        val track = TRACKS[LocalDate.now().dayOfYear % TRACKS.size]
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            val afd = resources.openRawResourceFd(track)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close()
            setOnCompletionListener { stopAll() }
            setOnErrorListener { _, _, _ -> stopAll(); true }
            prepare(); start()
        }
        return START_NOT_STICKY
    }

    private fun stopAll() {
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
