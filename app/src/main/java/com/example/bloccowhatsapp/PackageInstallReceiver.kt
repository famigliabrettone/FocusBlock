package com.example.bloccowhatsapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Receiver registrato nel Manifest che intercetta l'installazione di nuove app
 * sul dispositivo (ACTION_PACKAGE_ADDED).
 * Se la VPN è attiva, richiede al servizio di aggiornare il routing
 * così che la nuova app abbia subito accesso a Internet.
 */
class PackageInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_PACKAGE_ADDED) {
            val packageName = intent.data?.schemeSpecificPart
            if (packageName != null && packageName != context.packageName && BlockVpnService.isRunning) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (BlockVpnService.isRunning) {
                        try {
                            val vpnIntent = Intent(context, BlockVpnService::class.java).apply {
                                action = "RESTART"
                            }
                            context.startService(vpnIntent)
                        } catch (_: Exception) { }
                    }
                }, 500)
            }
        }
    }
}
