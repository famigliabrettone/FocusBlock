package com.example.bloccowhatsapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Schermata a schermo intero che appare quando l'utente
 * prova ad aprire un'app bloccata.
 */
class BlockScreenActivity : AppCompatActivity() {

    private lateinit var manager: ScheduleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_screen)

        manager = ScheduleManager(this)

        val blockedApp = intent.getStringExtra("blocked_app") ?: ""

        // Ricava il nome dell'app reale dal PackageManager
        val pm = packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(blockedApp, 0)).toString()
        } catch (_: Exception) {
            blockedApp
        }

        val titleText = findViewById<TextView>(R.id.blockTitle)
        val messageText = findViewById<TextView>(R.id.blockMessage)
        val statusText = findViewById<TextView>(R.id.blockStatus)
        val btnBack = findViewById<Button>(R.id.btnGoBack)
        val btnTempUnblock = findViewById<Button>(R.id.btnTempUnblock)

        titleText.text = "🔒 $appName è bloccata"
        messageText.text = getString(R.string.block_screen_message)

        // Dettaglio dello stato di blocco
        statusText.text = manager.getActiveBlockDescription()

        btnBack.setOnClickListener {
            goToHome()
        }

        btnTempUnblock.setOnClickListener {
            showTempUnblockDialog()
        }

        // Gestione moderna del back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToHome()
            }
        })
    }

    private fun goToHome() {
        finishAffinity()
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
    }

    private fun showTempUnblockDialog() {
        val options = arrayOf("5 minuti", "10 minuti", "15 minuti", "30 minuti", "1 ora")
        val minutes = intArrayOf(5, 10, 15, 30, 60)

        MaterialAlertDialogBuilder(this)
            .setTitle("⏱ Sblocca temporaneamente")
            .setItems(options) { _, which ->
                val until = System.currentTimeMillis() + minutes[which] * 60 * 1000L
                manager.setBlockNow(false, 0L)
                manager.setTempUnblock(true, until)

                // Ferma immediatamente la VPN per consentire la connessione
                if (BlockVpnService.isRunning) {
                    val stopIntent = Intent(this, BlockVpnService::class.java).apply { action = "STOP" }
                    startService(stopIntent)
                }

                val sdf = SimpleDateFormat("HH:mm", Locale.ITALY)
                Toast.makeText(
                    this,
                    "App sbloccata fino alle ${sdf.format(Date(until))}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}
