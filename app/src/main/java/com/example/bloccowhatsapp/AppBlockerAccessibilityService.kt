package com.example.bloccowhatsapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.VpnService
import android.view.accessibility.AccessibilityEvent
import java.util.Timer
import java.util.TimerTask

/**
 * Servizio di Accessibilità che:
 * 1. Monitora le app aperte e blocca l'accesso se necessario
 * 2. Controlla periodicamente se attivare/disattivare la VPN blocker
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private lateinit var manager: ScheduleManager
    private var vpnCheckTimer: Timer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        manager = ScheduleManager(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        serviceInfo = info

        // Controlla ogni 15 secondi se avviare/fermare la VPN
        startVpnMonitor()
    }

    /**
     * Monitora periodicamente lo stato del blocco e
     * attiva/disattiva la VPN di conseguenza.
     */
    private fun startVpnMonitor() {
        vpnCheckTimer?.cancel()
        vpnCheckTimer = Timer()
        vpnCheckTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    val shouldBlock = manager.shouldBlock()

                    if (shouldBlock && !BlockVpnService.isRunning) {
                        // Attiva la VPN
                        val vpnIntent = VpnService.prepare(this@AppBlockerAccessibilityService)
                        if (vpnIntent == null) {
                            // Permesso VPN già accordato, avvia
                            val startIntent = Intent(
                                this@AppBlockerAccessibilityService,
                                BlockVpnService::class.java
                            )
                            startService(startIntent)
                        }
                        // Se vpnIntent != null, l'utente deve dare il permesso dalla MainActivity
                    } else if (!shouldBlock && BlockVpnService.isRunning) {
                        // Disattiva la VPN
                        val stopIntent = Intent(
                            this@AppBlockerAccessibilityService,
                            BlockVpnService::class.java
                        ).apply { action = "STOP" }
                        startService(stopIntent)
                    }
                } catch (_: Exception) { }
            }
        }, 5_000, 15_000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Ignora la nostra stessa app e i launcher di sistema
        if (packageName == this.packageName) return
        if (packageName == "com.android.launcher" ||
            packageName == "com.android.launcher3" ||
            packageName == "com.google.android.apps.nexuslauncher" ||
            packageName == "com.sec.android.app.launcher" ||
            packageName == "com.android.systemui"
        ) return

        // Controlla se l'app è tra quelle selezionate dall'utente da bloccare
        val blockedApps = manager.getBlockedApps()
        if (!blockedApps.contains(packageName)) return

        // Controlla se in questo momento il blocco è attivo
        if (manager.shouldBlock()) {
            val intent = Intent(this, BlockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("blocked_app", packageName)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {
        // Nessuna azione richiesta
    }

    override fun onDestroy() {
        vpnCheckTimer?.cancel()
        vpnCheckTimer = null
        super.onDestroy()
    }
}
