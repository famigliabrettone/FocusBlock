package com.example.bloccowhatsapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

/**
 * Schermata per creare o modificare una singola programmazione.
 */
class EditScheduleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"  // -1 = nuova
    }

    private lateinit var manager: ScheduleManager
    private var scheduleId: Int = -1
    private var existingSchedule: Schedule? = null

    private lateinit var etName: TextInputEditText
    private lateinit var switchAllDay: MaterialSwitch
    private lateinit var cardTimeRange: View
    private lateinit var btnFromTime: MaterialButton
    private lateinit var btnToTime: MaterialButton
    private lateinit var switchNotify: MaterialSwitch

    private val selectedDays = mutableSetOf<Int>()
    private val dayButtons = mutableMapOf<Int, MaterialButton>()
    private var fromHour = 8; private var fromMin = 0
    private var toHour = 18; private var toMin = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_schedule)

        manager = ScheduleManager(this)
        scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, -1)

        bindViews()
        setupDayButtons()

        if (scheduleId != -1) {
            // Modifica programmazione esistente
            existingSchedule = manager.getSchedules().find { it.id == scheduleId }
            existingSchedule?.let { loadSchedule(it) }
            supportActionBar?.title = "Modifica Programmazione"
        } else {
            supportActionBar?.title = "Nuova Programmazione"
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun bindViews() {
        etName = findViewById(R.id.etScheduleName)
        switchAllDay = findViewById(R.id.switchAllDay)
        cardTimeRange = findViewById(R.id.cardTimeRange)
        btnFromTime = findViewById(R.id.btnFromTime)
        btnToTime = findViewById(R.id.btnToTime)
        switchNotify = findViewById(R.id.switchNotifyBefore)
    }

    private fun setupDayButtons() {
        dayButtons[Calendar.MONDAY]    = findViewById(R.id.btnDayMon)
        dayButtons[Calendar.TUESDAY]   = findViewById(R.id.btnDayTue)
        dayButtons[Calendar.WEDNESDAY] = findViewById(R.id.btnDayWed)
        dayButtons[Calendar.THURSDAY]  = findViewById(R.id.btnDayThu)
        dayButtons[Calendar.FRIDAY]    = findViewById(R.id.btnDayFri)
        dayButtons[Calendar.SATURDAY]  = findViewById(R.id.btnDaySat)
        dayButtons[Calendar.SUNDAY]    = findViewById(R.id.btnDaySun)

        for ((day, btn) in dayButtons) {
            updateDayBtn(btn, false)
            btn.setOnClickListener {
                val sel = selectedDays.contains(day)
                if (sel) selectedDays.remove(day) else selectedDays.add(day)
                updateDayBtn(btn, !sel)
            }
        }
    }

    private fun loadSchedule(s: Schedule) {
        etName.setText(s.name)
        switchAllDay.isChecked = s.allDay
        switchNotify.isChecked = s.notifyBefore
        fromHour = s.fromHour; fromMin = s.fromMinute
        toHour = s.toHour; toMin = s.toMinute
        selectedDays.addAll(s.days)
        for ((day, btn) in dayButtons) updateDayBtn(btn, selectedDays.contains(day))
        updateTimeButtons()
        updateAllDayVisibility()
    }

    private fun setupListeners() {
        switchAllDay.setOnCheckedChangeListener { _, _ -> updateAllDayVisibility() }

        btnFromTime.setOnClickListener {
            showTimePicker("Dalle ore", fromHour, fromMin) { h, m ->
                fromHour = h; fromMin = m; updateTimeButtons()
            }
        }
        btnToTime.setOnClickListener {
            showTimePicker("Alle ore", toHour, toMin) { h, m ->
                toHour = h; toMin = m; updateTimeButtons()
            }
        }

        findViewById<MaterialButton>(R.id.btnSaveSchedule).setOnClickListener {
            saveSchedule()
        }
    }

    private fun saveSchedule() {
        val name = etName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            Toast.makeText(this, "Inserisci un nome per la programmazione", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedDays.isEmpty()) {
            Toast.makeText(this, "Seleziona almeno un giorno", Toast.LENGTH_SHORT).show()
            return
        }

        val schedules = manager.getSchedules()

        if (existingSchedule != null) {
            // Aggiorna esistente
            val idx = schedules.indexOfFirst { it.id == scheduleId }
            if (idx >= 0) {
                schedules[idx] = schedules[idx].copy(
                    name = name,
                    days = selectedDays.toMutableList(),
                    allDay = switchAllDay.isChecked,
                    fromHour = fromHour, fromMinute = fromMin,
                    toHour = toHour, toMinute = toMin,
                    notifyBefore = switchNotify.isChecked
                )
            }
        } else {
            // Nuova
            schedules.add(
                Schedule(
                    id = manager.nextId(),
                    name = name,
                    days = selectedDays.toMutableList(),
                    allDay = switchAllDay.isChecked,
                    fromHour = fromHour, fromMinute = fromMin,
                    toHour = toHour, toMinute = toMin,
                    enabled = true,
                    notifyBefore = switchNotify.isChecked
                )
            )
        }

        manager.saveSchedules(schedules)
        ScheduleAlarmManager.rescheduleAll(this, schedules)
        Toast.makeText(this, "✅ Programmazione salvata!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateDayBtn(btn: MaterialButton, selected: Boolean) {
        btn.setBackgroundColor(
            ContextCompat.getColor(this, if (selected) R.color.primary else R.color.day_unselected)
        )
        btn.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.white else R.color.text_primary)
        )
    }

    private fun updateTimeButtons() {
        btnFromTime.text = "%02d:%02d".format(fromHour, fromMin)
        btnToTime.text   = "%02d:%02d".format(toHour, toMin)
    }

    private fun updateAllDayVisibility() {
        cardTimeRange.visibility = if (switchAllDay.isChecked) View.GONE else View.VISIBLE
    }

    private fun showTimePicker(title: String, h: Int, m: Int, cb: (Int, Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(h).setMinute(m).setTitleText(title)
            .build()
        picker.addOnPositiveButtonClickListener { cb(picker.hour, picker.minute) }
        picker.show(supportFragmentManager, "tp")
    }
}
