package com.example.bloccowhatsapp

import android.media.AudioManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Servizio che intercetta e cancella IMMEDIATAMENTE le notifiche
 * (messaggi E chiamate in arrivo) delle app bloccate durante gli orari programmati.
 *
 * NOTA: NotificationListenerService è un servizio LEGATO DAL SISTEMA (bind),
 * NON avviato dall'app. Su Android 12+ chiamare startForeground() qui causa
 * ForegroundServiceStartNotAllowedException e il crash silenzioso del servizio.
 * Il servizio è già mantenuto attivo dal sistema operativo tramite il permesso
 * BIND_NOTIFICATION_LISTENER_SERVICE — non serve startForeground().
 */
class NotificationBlockerService : NotificationListenerService() {

    private lateinit var manager: ScheduleManager
    private lateinit var audioManager: AudioManager

    companion object {
        // Package name delle app che fanno chiamate VoIP
        private val CALL_APPS = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.facebook.orca",
            "com.skype.raider",
            "com.viber.voip"
        )
    }

    override fun onCreate() {
        super.onCreate()
        manager = ScheduleManager(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        // ⚠️ NON chiamare startForeground() qui: questo servizio è bound dal sistema,
        // non avviato via startForegroundService(), e crasherebbe su Android 12+.
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val blockedApps = manager.getBlockedApps()

        // L'app non è tra quelle da bloccare → ignora
        if (!blockedApps.contains(packageName)) return

        // Il blocco non è attivo in questo momento → ignora
        if (!manager.shouldBlock()) return

        // ━━━ CANCELLA SUBITO LA NOTIFICA ━━━
        // Annulla prima che produca suono/vibrazione
        try {
            cancelNotification(sbn.key)
        } catch (_: Exception) {}

        // ━━━ SILENZIA EVENTUALE SQUILLO CHIAMATA ━━━
        // Fallback: se la VPN non è attiva, silenzia comunque lo squillo locale.
        // Con la VPN attiva invece la chiamata non arriva nemmeno sul dispositivo.
        if (CALL_APPS.contains(packageName)) {
            silenceRingerForCall()
        }
    }

    /**
     * Silenzia temporaneamente lo squillo per bloccare le chiamate VoIP
     * (WhatsApp, WA Business, Telegram, ecc.).
     * Il volume viene ripristinato automaticamente dopo 2 secondi.
     */
    private fun silenceRingerForCall() {
        try {
            val previousMode = audioManager.ringerMode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_RING,
                    AudioManager.ADJUST_MUTE,
                    0
                )
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            }

            // Ripristina dopo 2 secondi
            android.os.Handler(mainLooper).postDelayed({
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_RING,
                            AudioManager.ADJUST_UNMUTE,
                            0
                        )
                    } else {
                        audioManager.ringerMode = previousMode
                    }
                } catch (_: Exception) {}
            }, 2000)
        } catch (_: SecurityException) {
            // Il permesso DND potrebbe non essere garantito su alcuni dispositivi
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Nessuna azione richiesta
    }
}
