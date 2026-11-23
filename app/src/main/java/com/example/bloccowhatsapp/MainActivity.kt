package com.example.bloccowhatsapp

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var manager: ScheduleManager

    // UI Generica
    private lateinit var tvStatus: TextView
    private lateinit var tvSelectedApps: TextView
    private lateinit var btnBlockNow: MaterialButton
    private lateinit var btnUnblockTemp: MaterialButton
    private lateinit var switchHolidays: MaterialSwitch
    private lateinit var schedulesContainer: LinearLayout
    private lateinit var tvNoSchedules: TextView
    private lateinit var holidaysContainer: LinearLayout
    private lateinit var cardPermissions: View

    // Orario di Lavoro Settimanale
    private lateinit var switchWorkSchedule: MaterialSwitch
    private lateinit var layoutWorkScheduleContent: View
    private lateinit var btnWorkMonFriFrom: MaterialButton
    private lateinit var btnWorkMonFriTo: MaterialButton
    private lateinit var switchWorkSat: MaterialSwitch
    private lateinit var layoutWorkSatTimes: View
    private lateinit var btnWorkSatFrom: MaterialButton
    private lateinit var btnWorkSatTo: MaterialButton
    private lateinit var tvWorkScheduleSummary: TextView
    private lateinit var btnResetWorkSchedule: MaterialButton

    // Ferie
    private lateinit var tvVacationStart: TextView
    private lateinit var tvVacationEnd: TextView
    private lateinit var btnVacationStart: MaterialButton
    private lateinit var btnVacationEnd: MaterialButton
    private lateinit var btnClearVacation: MaterialButton

    private val selectedHolidays = mutableSetOf<String>()

    // Launcher per AppPickerActivity
    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val apps = result.data?.getStringArrayListExtra(AppPickerActivity.RESULT_APPS)
            if (apps != null) {
                manager.setBlockedApps(apps.toSet())
                updateSelectedAppsUI()
                if (BlockVpnService.isRunning) {
                    val intent = Intent(this, BlockVpnService::class.java)
                    startService(intent)
                }
            }
        }
    }

    // Launcher per EditScheduleActivity
    private val editScheduleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        loadSchedulesList()
        updateStatusUI()
        checkAndStartVpn()
    }

    // Launcher per permesso VPN
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "✅ Permesso VPN concesso!", Toast.LENGTH_SHORT).show()
            startVpnIfNeeded()
        } else {
            Toast.makeText(this, "❌ Permesso VPN negato", Toast.LENGTH_SHORT).show()
        }
        updatePermissionsCard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom)
            insets
        }

        manager = ScheduleManager(this)

        // Canale notifiche
        ScheduleAlarmManager.createNotificationChannel(this)

        // Permesso notifiche Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        bindViews()
        setupListeners()
        setupWorkScheduleUI()
        setupHolidays()
        loadHolidayPrefs()
        updateVacationUI()
        updateSelectedAppsUI()
        loadSchedulesList()
        updateStatusUI()

        // Pianifica allarmi
        ScheduleAlarmManager.rescheduleAll(this, manager.getSchedules())
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsCard()
        updateStatusUI()
        checkAndStartVpn()
    }

    // ── Bind viste ────────────────────────────────────────────────────────────

    private fun bindViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvSelectedApps = findViewById(R.id.tvSelectedApps)
        btnBlockNow = findViewById(R.id.btnBlockNow)
        btnUnblockTemp = findViewById(R.id.btnUnblockTemp)
        switchHolidays = findViewById(R.id.switchHolidays)
        schedulesContainer = findViewById(R.id.schedulesContainer)
        tvNoSchedules = findViewById(R.id.tvNoSchedules)
        holidaysContainer = findViewById(R.id.holidaysContainer)
        cardPermissions = findViewById(R.id.cardPermissions)

        // Orario di lavoro
        switchWorkSchedule = findViewById(R.id.switchWorkSchedule)
        layoutWorkScheduleContent = findViewById(R.id.layoutWorkScheduleContent)
        btnWorkMonFriFrom = findViewById(R.id.btnWorkMonFriFrom)
        btnWorkMonFriTo = findViewById(R.id.btnWorkMonFriTo)
        switchWorkSat = findViewById(R.id.switchWorkSat)
        layoutWorkSatTimes = findViewById(R.id.layoutWorkSatTimes)
        btnWorkSatFrom = findViewById(R.id.btnWorkSatFrom)
        btnWorkSatTo = findViewById(R.id.btnWorkSatTo)
        tvWorkScheduleSummary = findViewById(R.id.tvWorkScheduleSummary)
        btnResetWorkSchedule = findViewById(R.id.btnResetWorkSchedule)

        // Ferie
        tvVacationStart = findViewById(R.id.tvVacationStart)
        tvVacationEnd = findViewById(R.id.tvVacationEnd)
        btnVacationStart = findViewById(R.id.btnVacationStart)
        btnVacationEnd = findViewById(R.id.btnVacationEnd)
        btnClearVacation = findViewById(R.id.btnClearVacation)
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private fun setupListeners() {
        // Permessi
        findViewById<MaterialButton>(R.id.btnEnableAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Cerca 'FocusBlock' e attivalo", Toast.LENGTH_LONG).show()
        }
        findViewById<MaterialButton>(R.id.btnEnableNotification).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(this, "Cerca 'FocusBlock' e attivalo", Toast.LENGTH_LONG).show()
        }
        findViewById<MaterialButton>(R.id.btnEnableVpn).setOnClickListener {
            requestVpnPermission()
        }

        // Seleziona app
        findViewById<MaterialButton>(R.id.btnPickApps).setOnClickListener {
            val intent = Intent(this, AppPickerActivity::class.java).apply {
                putStringArrayListExtra(
                    AppPickerActivity.EXTRA_SELECTED_APPS,
                    ArrayList(manager.getBlockedApps())
                )
            }
            appPickerLauncher.launch(intent)
        }

        // Blocca Adesso:
        // - Se blocco manuale attivo: disattiva e ripristina normale
        // - Se sblocco temporaneo attivo: annulla sblocco e blocca
        // - Se nessuna programmazione attiva: blocca fino alla prossima programmazione
        // - Se una programmazione è già attiva: informa l'utente che il blocco è già in corso
        btnBlockNow.setOnClickListener {
            handleBlockNowClick()
        }

        // Sblocco temporaneo: prevale su tutto (ferie, festivi, orari e blocco manuale)
        btnUnblockTemp.setOnClickListener {
            handleTempUnblockClick()
        }

        // Aggiungi programmazione extra
        findViewById<MaterialButton>(R.id.btnAddSchedule).setOnClickListener {
            val intent = Intent(this, EditScheduleActivity::class.java).apply {
                putExtra(EditScheduleActivity.EXTRA_SCHEDULE_ID, -1)
            }
            editScheduleLauncher.launch(intent)
        }

        // Switch festivi
        switchHolidays.setOnCheckedChangeListener { _, isChecked ->
            manager.setHolidaysEnabled(isChecked)
            holidaysContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateStatusUI()
            checkAndStartVpn()
        }

        // Ferie
        btnVacationStart.setOnClickListener { showDatePicker(true) }
        btnVacationEnd.setOnClickListener { showDatePicker(false) }
        btnClearVacation.setOnClickListener {
            manager.clearVacation()
            updateVacationUI()
            updateStatusUI()
            checkAndStartVpn()
        }
    }

    // ── Logica Pulsanti Rapidi ────────────────────────────────────────────────

    private fun handleBlockNowClick() {
        val sdf = SimpleDateFormat("HH:mm", Locale.ITALY)

        when {
            // 1) Se è attualmente in blocco manuale, disattiva il blocco manuale
            manager.isBlockNow() -> {
                manager.setBlockNow(false, 0L)
                checkAndStartVpn()
                Toast.makeText(this, "🔓 Blocco manuale disattivato, orario normale ripristinato", Toast.LENGTH_SHORT).show()
                updateStatusUI()
            }
            // 2) Se è attivo uno sblocco temporaneo, annullalo subito e riblocca
            manager.isTempUnblock() -> {
                manager.setTempUnblock(false, 0L)
                if (manager.isScheduledBlockActive()) {
                    checkAndStartVpn()
                    Toast.makeText(this, "🔒 Sblocco temporaneo terminato: blocco ripristinato", Toast.LENGTH_SHORT).show()
                } else {
                    val nextBlockMillis = manager.getNextScheduledBlockMillis()
                    manager.setBlockNow(true, nextBlockMillis)
                    startVpnIfNeeded()
                    if (nextBlockMillis > 0L) {
                        Toast.makeText(this, "🔒 Sblocco annullato: app bloccata fino alle ${sdf.format(Date(nextBlockMillis))}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "🔒 Sblocco annullato: app bloccata adesso", Toast.LENGTH_SHORT).show()
                    }
                }
                updateStatusUI()
            }
            // 3) Se è già attiva una programmazione (o ferie/festivi), l'app è già bloccata
            manager.isScheduledBlockActive() -> {
                val reason = manager.getActiveBlockDescription()
                MaterialAlertDialogBuilder(this)
                    .setTitle("🔒 Blocco Già Attivo")
                    .setMessage("WhatsApp è già bloccato in questo momento da:\n\"$reason\".\n\nSe hai bisogno di usare l'app adesso, usa il pulsante 'SBLOCCA TEMPORANEAMENTE'.")
                    .setPositiveButton("Capito", null)
                    .setNeutralButton("⏱ Sblocca Ora") { _, _ ->
                        showTempUnblockDialog()
                    }
                    .show()
            }
            // 4) Se siamo in orario libero (nessuna programmazione attiva), blocca fino alla prossima programmazione!
            else -> {
                val nextBlockMillis = manager.getNextScheduledBlockMillis()
                manager.setBlockNow(true, nextBlockMillis)
                startVpnIfNeeded()
                if (nextBlockMillis > 0L) {
                    Toast.makeText(
                        this,
                        "🔒 App bloccata adesso fino alle ${sdf.format(Date(nextBlockMillis))}\n(fino alla prossima programmazione)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, "🔒 App bloccata adesso!", Toast.LENGTH_SHORT).show()
                }
                updateStatusUI()
            }
        }
    }

    private fun handleTempUnblockClick() {
        if (manager.isTempUnblock() && System.currentTimeMillis() < manager.getTempUnblockUntil()) {
            val sdf = SimpleDateFormat("HH:mm", Locale.ITALY)
            val untilStr = sdf.format(Date(manager.getTempUnblockUntil()))
            MaterialAlertDialogBuilder(this)
                .setTitle("⏱ Sblocco Temporaneo Attivo")
                .setMessage("L'app è attualmente sbloccata fino alle $untilStr.\nCosa desideri fare?")
                .setPositiveButton("Modifica Tempo") { _, _ ->
                    showTempUnblockDialog()
                }
                .setNegativeButton("Termina e Riblocca Ora") { _, _ ->
                    manager.setTempUnblock(false, 0L)
                    checkAndStartVpn()
                    Toast.makeText(this, "🔒 Sblocco temporaneo annullato, blocco ripristinato", Toast.LENGTH_SHORT).show()
                    updateStatusUI()
                }
                .setNeutralButton("Annulla", null)
                .show()
        } else {
            showTempUnblockDialog()
        }
    }

    private fun showTempUnblockDialog() {
        val labels = arrayOf("5 minuti", "10 minuti", "15 minuti", "30 minuti", "1 ora")
        val mins = intArrayOf(5, 10, 15, 30, 60)
        MaterialAlertDialogBuilder(this)
            .setTitle("⏱ Sblocca temporaneamente per quanto tempo?")
            .setItems(labels) { _, i ->
                val until = System.currentTimeMillis() + mins[i] * 60_000L
                manager.setBlockNow(false, 0L) // Rimuove eventuale blocco manuale
                manager.setTempUnblock(true, until)
                stopVpn() // Ferma immediatamente la VPN

                val sdf = SimpleDateFormat("HH:mm", Locale.ITALY)
                Toast.makeText(
                    this,
                    "🟡 App sbloccata per ${labels[i]} (fino alle ${sdf.format(Date(until))})\nPrevale su qualsiasi blocco!",
                    Toast.LENGTH_LONG
                ).show()
                updateStatusUI()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ── Orario di Lavoro Settimanale UI ──────────────────────────────────────

    private fun setupWorkScheduleUI() {
        switchWorkSchedule.isChecked = manager.isWorkScheduleEnabled()
        layoutWorkScheduleContent.visibility = if (manager.isWorkScheduleEnabled()) View.VISIBLE else View.GONE

        switchWorkSchedule.setOnCheckedChangeListener { _, isChecked ->
            manager.setWorkScheduleEnabled(isChecked)
            layoutWorkScheduleContent.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateWorkScheduleSummary()
            updateStatusUI()
            checkAndStartVpn()
            ScheduleAlarmManager.rescheduleAll(this, manager.getSchedules())
        }

        // Lunedì - Venerdì
        btnWorkMonFriFrom.text = "Dalle %02d:%02d".format(manager.getWorkMonFriFromHour(), manager.getWorkMonFriFromMinute())
        btnWorkMonFriTo.text = "Alle %02d:%02d".format(manager.getWorkMonFriToHour(), manager.getWorkMonFriToMinute())

        btnWorkMonFriFrom.setOnClickListener {
            showTimePicker("Sblocca Lun-Ven dalle ore", manager.getWorkMonFriFromHour(), manager.getWorkMonFriFromMinute()) { h, m ->
                manager.setWorkMonFriTime(h, m, manager.getWorkMonFriToHour(), manager.getWorkMonFriToMinute())
                btnWorkMonFriFrom.text = "Dalle %02d:%02d".format(h, m)
                updateWorkScheduleSummary()
                updateStatusUI()
                checkAndStartVpn()
                ScheduleAlarmManager.rescheduleAll(this, manager.getSchedules())
            }
        }

        btnWorkMonFriTo.setOnClickListener {
            showTimePicker("Sblocca Lun-Ven alle ore", manager.getWorkMonFriToHour(), manager.getWorkMonFriToMinute()) { h, m ->
                manager.setWorkMonFriTime(manager.getWorkMonFriFromHour(), manager.getWorkMonFriFromMinute(), h, m)
                btnWorkMonFriTo.text = "Alle %02d:%02d".format(h, m)
                updateWorkScheduleSummary()
                updateStatusUI()
                checkAndStartVpn()
                ScheduleAlarmManager.rescheduleAll(this, manager.getSchedules())
            }
        }

        // Sabato
        switchWorkSat.isChecked = manager.isWorkSatEnabled()
        layoutWorkSatTimes.visibility = if (manager.isWorkSatEnabled()) View.VISIBLE else View.GONE

        switchWorkSat.setOnCheckedChangeListener { _, isChecked ->
            manager.setWorkSatEnabled(isChecked)
            layoutWorkSatTimes.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateWorkScheduleSummary()
            updateStatusUI()
            checkAndStartVpn()
            ScheduleAlarmManager.rescheduleAll(this, manager.getSchedules())
        }

        btnWorkSatFrom.text = "Dalle %02d:%02d".format(manager.getWorkSatFromHour(), manager.getWorkSatFromMinute())
        btnWorkSatTo.text = "Alle %02d:%02d".format(manager.getWorkSatToHour(), manager.getWorkSatToMinute())

        btnWorkSatFrom.setOnClickListener {
            showTimePicker("Sblocca Sabato dalle ore", manager.getWorkSatFromHour(), manager.getWorkSatFromMinute()) { h, m ->
                manager.setWorkSatTime(h, m, manager.getWorkSatToHour(), manager.getWorkSatToMinute())
                btnWorkSatFrom.text = "Dalle %02d:%02d".format(h, m)
                updateWorkScheduleSummary()
                updateStatusUI()
                checkAndStartVpn()
                ScheduleAlarmManager.rescheduleAll(this, manager.getSchedules())
            }
        }

        btnWorkSatTo.setOnClickListener {
            showTimePicker("Sblocca Sabato alle ore", manager.getWorkSatToHour(), manager.getWorkSatToMinute()) { h, m ->
                manager.setWorkSatTime(manager.getWorkSatFromHour(), manager.getWorkSatFromMinute(), h, m)
                btnWorkSatTo.text = "Alle %02d:%02d".format(h, m)
                updateWorkScheduleSummary()
                updateStatusUI()
                checkAndStartVpn()
                ScheduleAlarmManager.rescheduleAll(this, manager.getSchedules())
            }
        }

        // Tasto Reset orario
        btnResetWorkSchedule.setOnClickListener {
            manager.resetToDefaultWorkSchedule()
            switchWorkSchedule.isChecked = true
            btnWorkMonFriFrom.text = "Dalle 08:00"
            btnWorkMonFriTo.text = "Alle 18:10"
            switchWorkSat.isChecked = true
            btnWorkSatFrom.text = "Dalle 08:00"
            btnWorkSatTo.text = "Alle 13:10"
            layoutWorkScheduleContent.visibility = View.VISIBLE
            layoutWorkSatTimes.visibility = View.VISIBLE
            updateWorkScheduleSummary()
            updateStatusUI()
            checkAndStartVpn()
            ScheduleAlarmManager.rescheduleAll(this, manager.getSchedules())
            Toast.makeText(this, "✅ Orario reimpostato:\nLun-Ven 08:00-18:10, Sab 08:00-13:10", Toast.LENGTH_SHORT).show()
        }

        updateWorkScheduleSummary()
    }

    private fun updateWorkScheduleSummary() {
        val monFriFrom = "%02d:%02d".format(manager.getWorkMonFriFromHour(), manager.getWorkMonFriFromMinute())
        val monFriTo = "%02d:%02d".format(manager.getWorkMonFriToHour(), manager.getWorkMonFriToMinute())

        if (manager.isWorkSatEnabled()) {
            val satTo = "%02d:%02d".format(manager.getWorkSatToHour(), manager.getWorkSatToMinute())
            tvWorkScheduleSummary.text = "• Lun–Ven: bloccata dalle $monFriTo alle $monFriFrom del mattino\n• Weekend: bloccata da Sabato alle $satTo fino a Lunedì alle $monFriFrom\n• Domenica: bloccata 24h su 24"
        } else {
            tvWorkScheduleSummary.text = "• Lun–Ven: bloccata dalle $monFriTo alle $monFriFrom del mattino\n• Weekend: bloccata da Venerdì alle $monFriTo fino a Lunedì alle $monFriFrom (Sabato e Domenica sempre bloccata)"
        }
    }

    private fun showTimePicker(title: String, initialHour: Int, initialMinute: Int, onTimeSet: (Int, Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(initialHour)
            .setMinute(initialMinute)
            .setTitleText(title)
            .build()

        picker.addOnPositiveButtonClickListener {
            onTimeSet(picker.hour, picker.minute)
        }
        picker.show(supportFragmentManager, "time_picker")
    }

    // ── VPN ───────────────────────────────────────────────────────────────────

    private fun requestVpnPermission() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            Toast.makeText(this, "✅ Permesso VPN già attivo!", Toast.LENGTH_SHORT).show()
            updatePermissionsCard()
        }
    }

    private fun isVpnPermissionGranted(): Boolean {
        return VpnService.prepare(this) == null
    }

    private fun startVpnIfNeeded() {
        if (!isVpnPermissionGranted()) return
        if (manager.shouldBlock() && !BlockVpnService.isRunning) {
            val intent = Intent(this, BlockVpnService::class.java)
            startService(intent)
        }
    }

    private fun stopVpn() {
        if (BlockVpnService.isRunning) {
            val intent = Intent(this, BlockVpnService::class.java).apply { action = "STOP" }
            startService(intent)
        }
    }

    private fun checkAndStartVpn() {
        if (manager.shouldBlock()) {
            startVpnIfNeeded()
        } else {
            stopVpn()
        }
    }

    // ── Lista programmazioni extra ────────────────────────────────────────────

    private fun loadSchedulesList() {
        schedulesContainer.removeAllViews()
        val schedules = manager.getSchedules()

        tvNoSchedules.visibility = if (schedules.isEmpty()) View.VISIBLE else View.GONE

        for (schedule in schedules) {
            val cardView = layoutInflater.inflate(
                R.layout.item_schedule, schedulesContainer, false
            )

            cardView.findViewById<TextView>(R.id.tvScheduleName).text = schedule.name
            cardView.findViewById<TextView>(R.id.tvScheduleDays).text = schedule.daysDescription()
            cardView.findViewById<TextView>(R.id.tvScheduleTime).text = "⏰ ${schedule.timeDescription()}"

            val notifyBadge = cardView.findViewById<TextView>(R.id.tvNotifyBadge)
            notifyBadge.visibility = if (schedule.notifyBefore) View.VISIBLE else View.GONE

            val switchEnabled = cardView.findViewById<MaterialSwitch>(R.id.switchScheduleEnabled)
            switchEnabled.isChecked = schedule.enabled
            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                val list = manager.getSchedules()
                val idx = list.indexOfFirst { it.id == schedule.id }
                if (idx >= 0) {
                    list[idx] = list[idx].copy(enabled = isChecked)
                    manager.saveSchedules(list)
                    ScheduleAlarmManager.rescheduleAll(this, list)
                }
                updateStatusUI()
                checkAndStartVpn()
            }

            // Modifica
            cardView.findViewById<MaterialButton>(R.id.btnEditSchedule).setOnClickListener {
                val intent = Intent(this, EditScheduleActivity::class.java).apply {
                    putExtra(EditScheduleActivity.EXTRA_SCHEDULE_ID, schedule.id)
                }
                editScheduleLauncher.launch(intent)
            }

            // Elimina
            cardView.findViewById<MaterialButton>(R.id.btnDeleteSchedule).setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Elimina blocco extra")
                    .setMessage("Vuoi eliminare \"${schedule.name}\"?")
                    .setPositiveButton("Elimina") { _, _ ->
                        val list = manager.getSchedules()
                        list.removeAll { it.id == schedule.id }
                        manager.saveSchedules(list)
                        ScheduleAlarmManager.rescheduleAll(this, list)
                        loadSchedulesList()
                        updateStatusUI()
                        checkAndStartVpn()
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }

            schedulesContainer.addView(cardView)
        }
    }

    // ── App selezionate ───────────────────────────────────────────────────────

    private fun updateSelectedAppsUI() {
        val apps = manager.getBlockedApps()
        if (apps.isEmpty()) {
            tvSelectedApps.text = "Nessuna app selezionata"
            return
        }
        val pm = packageManager
        val names = apps.mapNotNull { pkg ->
            try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
            catch (_: Exception) { pkg }
        }
        tvSelectedApps.text = names.joinToString(" • ")
    }

    // ── Festivi ───────────────────────────────────────────────────────────────

    private fun setupHolidays() {
        holidaysContainer.removeAllViews()
        val holidays = ScheduleManager.ITALIAN_HOLIDAYS.toMutableList().also {
            it.add(BlockPreferences.HolidayInfo("pasqua", "Pasqua", "variabile", 0, 0))
            it.add(BlockPreferences.HolidayInfo("pasquetta", "Pasquetta", "variabile", 0, 0))
        }
        for (holiday in holidays) {
            val cb = MaterialCheckBox(this).apply {
                text = "${holiday.name}${if (holiday.dateText.isNotEmpty() && holiday.dateText != "variabile") " (${holiday.dateText})" else ""}"
                textSize = 14f
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedHolidays.add(holiday.id)
                    else selectedHolidays.remove(holiday.id)
                    manager.setEnabledHolidays(selectedHolidays)
                }
            }
            holidaysContainer.addView(cb)
        }
    }

    private fun loadHolidayPrefs() {
        switchHolidays.isChecked = manager.isHolidaysEnabled()
        holidaysContainer.visibility = if (manager.isHolidaysEnabled()) View.VISIBLE else View.GONE
        selectedHolidays.clear()
        selectedHolidays.addAll(manager.getEnabledHolidays())
        val enabled = manager.getEnabledHolidays()
        for (i in 0 until holidaysContainer.childCount) {
            val cb = holidaysContainer.getChildAt(i) as? MaterialCheckBox ?: continue
            val holidays = ScheduleManager.ITALIAN_HOLIDAYS.toMutableList().also {
                it.add(BlockPreferences.HolidayInfo("pasqua", "Pasqua", "", 0, 0))
                it.add(BlockPreferences.HolidayInfo("pasquetta", "Pasquetta", "", 0, 0))
            }
            if (i < holidays.size) cb.isChecked = enabled.contains(holidays[i].id)
        }
    }

    // ── Status UI ─────────────────────────────────────────────────────────────

    private fun updateStatusUI() {
        val sdf = SimpleDateFormat("HH:mm", Locale.ITALY)
        val vpnInfo = if (BlockVpnService.isRunning) " 🌐🚫" else ""

        when {
            // 1. Sblocco temporaneo attivo
            manager.isTempUnblock() && System.currentTimeMillis() < manager.getTempUnblockUntil() -> {
                val untilStr = sdf.format(Date(manager.getTempUnblockUntil()))
                tvStatus.text = "🟡 Sbloccata fino alle $untilStr"
                tvStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.holiday_gold_light))
                btnBlockNow.text = "🔒 BLOCCA ADESSO"
                btnBlockNow.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.blocked_red)
                btnUnblockTemp.text = "⏱ SBLOCCATA FINO ALLE $untilStr"
            }
            // 2. Blocco manuale attivo
            manager.isBlockNow() -> {
                val until = manager.getBlockNowUntil()
                val text = if (until > 0L) {
                    "🔴 Bloccata manualmente fino alle ${sdf.format(Date(until))}$vpnInfo"
                } else {
                    "🔴 Bloccata manualmente$vpnInfo"
                }
                tvStatus.text = text
                tvStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.blocked_red_light))
                btnBlockNow.text = "🔓 SBLOCCA ORA"
                btnBlockNow.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.active_green)
                btnUnblockTemp.text = "⏱ SBLOCCA TEMPORANEAMENTE"
            }
            // 3. Blocco da programmazione (orario lavoro, ferie, festivi, custom)
            manager.shouldBlock() -> {
                val reason = manager.getActiveBlockDescription()
                tvStatus.text = "🔴 Bloccata ($reason)$vpnInfo"
                tvStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.blocked_red_light))
                btnBlockNow.text = "🔒 BLOCCO IN CORSO"
                btnBlockNow.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.blocked_red)
                btnUnblockTemp.text = "⏱ SBLOCCA TEMPORANEAMENTE"
            }
            // 4. App sbloccate (orario di lavoro normale)
            else -> {
                tvStatus.text = "🟢 App sbloccate (Orario di lavoro)"
                tvStatus.setBackgroundResource(R.drawable.status_badge_bg)
                val nextBlock = manager.getNextScheduledBlockMillis()
                btnBlockNow.text = if (nextBlock > 0L) {
                    "🔒 BLOCCA ADESSO (fino alle ${sdf.format(Date(nextBlock))})"
                } else {
                    "🔒 BLOCCA ADESSO"
                }
                btnBlockNow.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.blocked_red)
                btnUnblockTemp.text = "⏱ SBLOCCA TEMPORANEAMENTE"
            }
        }
    }

    // ── Permessi ──────────────────────────────────────────────────────────────

    private fun updatePermissionsCard() {
        val accOk = isAccessibilityServiceEnabled()
        val notOk = isNotificationListenerEnabled()
        val vpnOk = isVpnPermissionGranted()

        val btnAcc = findViewById<MaterialButton>(R.id.btnEnableAccessibility)
        val btnNot = findViewById<MaterialButton>(R.id.btnEnableNotification)
        val btnVpn = findViewById<MaterialButton>(R.id.btnEnableVpn)

        btnAcc.text = if (accOk) "✅ Accessibilità attiva" else "Attiva Servizio di Accessibilità"
        btnAcc.isEnabled = !accOk
        btnNot.text = if (notOk) "✅ Blocco Notifiche attivo" else "Attiva Blocco Notifiche"
        btnNot.isEnabled = !notOk
        btnVpn.text = if (vpnOk) "✅ Blocco Connessione attivo" else "Attiva Blocco Connessione (VPN)"
        btnVpn.isEnabled = !vpnOk

        cardPermissions.visibility = if (accOk && notOk && vpnOk) View.GONE else View.VISIBLE
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val comp = ComponentName(this, AppBlockerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val cn = ComponentName.unflattenFromString(splitter.next())
            if (cn == comp) return true
        }
        return false
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val listeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return listeners.contains(packageName)
    }

    // ── Ferie ─────────────────────────────────────────────────────────────────

    private fun showDatePicker(isStart: Boolean) {
        val builder = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
        builder.setTitleText(if (isStart) "Seleziona Data Inizio" else "Seleziona Data Fine")

        val currentSelection = if (isStart) manager.getVacationStart() else manager.getVacationEnd()
        if (currentSelection > 0L) {
            builder.setSelection(currentSelection)
        } else {
            builder.setSelection(com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
        }

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            if (isStart) {
                manager.setVacationStart(selection)
            } else {
                manager.setVacationEnd(selection)
            }
            updateVacationUI()
            updateStatusUI()
            checkAndStartVpn()
        }
        picker.show(supportFragmentManager, picker.toString())
    }

    private fun updateVacationUI() {
        val start = manager.getVacationStart()
        val end = manager.getVacationEnd()

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.ITALY)

        if (start > 0L) {
            tvVacationStart.text = sdf.format(Date(start))
        } else {
            tvVacationStart.text = "Non impostata"
        }

        if (end > 0L) {
            tvVacationEnd.text = sdf.format(Date(end))
        } else {
            tvVacationEnd.text = "Non impostata"
        }

        btnClearVacation.visibility = if (start > 0L || end > 0L) View.VISIBLE else View.GONE
    }
}
