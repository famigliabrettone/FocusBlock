package com.example.bloccowhatsapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Timer
import java.util.TimerTask

/**
 * VPN locale che blocca TUTTO il traffico internet delle app selezionate.
 *
 * Quando il blocco è attivo:
 * - WhatsApp/WA Business non hanno connessione → i clienti sentono
 *   "l'utente non è disponibile" (esattamente come telefono spento)
 * - Nessuna notifica, nessuna chiamata, non risulti online
 * - Le altre app del telefono funzionano normalmente
 *
 * Non manda alcun dato a server esterni: la VPN resta tutta sul telefono.
 */
class BlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var checkTimer: Timer? = null
    private var isReceiverRegistered = false

    // BroadcastReceiver dinamico che rileva l'installazione di nuove app mentre la VPN è attiva
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_ADDED) {
                val pkgName = intent.data?.schemeSpecificPart
                if (pkgName != null && pkgName != packageName && isRunning) {
                    // Riavvia la VPN con un breve ritardo per consentire ad Android
                    // di finalizzare la registrazione del pacchetto e aggiornare le tabelle di routing
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isRunning) {
                            startVpn()
                        }
                    }, 500)
                }
            }
        }
    }

    companion object {
        var isRunning = false
            private set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        val manager = ScheduleManager(this)
        val blockedApps = manager.getBlockedApps()

        if (blockedApps.isEmpty()) {
            stopSelf()
            return
        }

        try {
            val builder = Builder()
                .setSession("FocusBlock")
                .addAddress("10.0.0.2", 32)           // Indirizzo fittizio locale
                .addRoute("0.0.0.0", 0)                // Intercetta tutto il traffico IPv4
                .allowBypass()                         // Consente esplicitamente alle altre app di bypassare la VPN

            // Aggiungi rotta e indirizzo IPv6 per bloccare anche il traffico IPv6
            try {
                builder.addAddress("fd00::1", 128)
                builder.addRoute("::", 0)
            } catch (_: Exception) { }

            // Instrada verso la VPN ESCLUSIVAMENTE le app da bloccare.
            // Con addAllowedApplication:
            // 1. Solo le app bloccate passano per l'interfaccia VPN (dove il traffico viene scartato).
            // 2. Tutte le altre app ignorano la VPN e navigano direttamente.
            // 3. Qualsiasi nuova app installata successivamente accede a Internet fin da subito.
            var allowedCount = 0
            for (pkg in blockedApps) {
                try {
                    builder.addAllowedApplication(pkg)
                    allowedCount++
                } catch (_: Exception) { }
            }

            // Se nessuna app da bloccare è installata, non avviare la VPN
            if (allowedCount == 0) {
                stopSelf()
                return
            }

            try {
                vpnInterface?.close()
            } catch (_: Exception) { }

            vpnInterface = builder.establish()
            isRunning = true

            // Registra il receiver dinamico per nuove installazioni
            registerPackageReceiver()

            showPersistentNotification(manager)

            // Controlla ogni 30 secondi se il blocco deve essere ancora attivo
            checkTimer?.cancel()
            checkTimer = Timer()
            checkTimer?.schedule(object : TimerTask() {
                override fun run() {
                    val mgr = ScheduleManager(this@BlockVpnService)
                    if (!mgr.shouldBlock()) {
                        stopVpn()
                        stopSelf()
                    }
                }
            }, 30_000, 30_000)

        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun registerPackageReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
                    addDataScheme("package")
                }
                ContextCompat.registerReceiver(
                    this,
                    packageReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isReceiverRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun unregisterPackageReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(packageReceiver)
            } catch (_: Exception) { }
            isReceiverRegistered = false
        }
    }

    private fun stopVpn() {
        unregisterPackageReceiver()
        checkTimer?.cancel()
        checkTimer = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) { }
        vpnInterface = null
        isRunning = false
        
        cancelPersistentNotification()
    }

    private fun showPersistentNotification(manager: ScheduleManager) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "blocco_attivo_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Blocco in corso",
                NotificationManager.IMPORTANCE_LOW // Low: mostra l'icona e la notifica senza suonare
            ).apply {
                description = "Notifica persistente quando il blocco è attivo"
            }
            nm.createNotificationChannel(channel)
        }

        val active = manager.activeSchedule()
        val blockName = active?.name ?: if (manager.isBlockNow()) "Manuale" else "Festivo"

        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("🔒 Blocco App in corso")
            .setContentText("Blocco attivo: $blockName")
            .setOngoing(true) // La rende persistente (non "cancellabile" con lo swipe)
            .setContentIntent(pi)
            .build()

        nm.notify(9002, notification)
    }

    private fun cancelPersistentNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(9002)
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
}
