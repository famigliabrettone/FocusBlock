package com.example.bloccowhatsapp

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

/**
 * Salva e carica la lista di programmazioni usando JSON + SharedPreferences.
 * Gestisce:
 * 1. Orario di lavoro settimanale (WhatsApp sbloccato Lun-Ven 8:00-18:10, Sab 8:00-13:10, e bloccato tutto il resto del tempo)
 * 2. Sblocco temporaneo ad altissima priorità (prevale su tutto: orari, ferie, festivi, blocco manuale)
 * 3. Blocco manuale ("Blocca Adesso") fino alla prossima programmazione
 * 4. Ferie, Festivi e Programmazioni personalizzate
 */
class ScheduleManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("block_prefs_v2", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_SCHEDULES = "schedules_json"
        private const val KEY_BLOCKED_APPS = "blocked_apps_v2"
        private const val KEY_HOLIDAYS_ENABLED = "holidays_enabled"
        private const val KEY_ENABLED_HOLIDAYS = "enabled_holidays"
        private const val KEY_BLOCK_NOW = "block_now"
        private const val KEY_BLOCK_NOW_UNTIL = "block_now_until"
        private const val KEY_TEMP_UNBLOCK = "temp_unblock"
        private const val KEY_TEMP_UNBLOCK_UNTIL = "temp_unblock_until"
        private const val KEY_NEXT_ID = "next_schedule_id"

        private const val KEY_VACATION_START = "vacation_start"
        private const val KEY_VACATION_END = "vacation_end"

        // Orario di lavoro settimanale (app sbloccata)
        private const val KEY_WORK_SCHEDULE_ENABLED = "work_schedule_enabled"
        private const val KEY_WORK_MON_FRI_FROM_H = "work_mon_fri_from_h"
        private const val KEY_WORK_MON_FRI_FROM_M = "work_mon_fri_from_m"
        private const val KEY_WORK_MON_FRI_TO_H = "work_mon_fri_to_h"
        private const val KEY_WORK_MON_FRI_TO_M = "work_mon_fri_to_m"

        private const val KEY_WORK_SAT_ENABLED = "work_sat_enabled"
        private const val KEY_WORK_SAT_FROM_H = "work_sat_from_h"
        private const val KEY_WORK_SAT_FROM_M = "work_sat_from_m"
        private const val KEY_WORK_SAT_TO_H = "work_sat_to_h"
        private const val KEY_WORK_SAT_TO_M = "work_sat_to_m"

        // Festivi italiani
        val ITALIAN_HOLIDAYS = listOf(
            BlockPreferences.HolidayInfo("capodanno",   "Capodanno",               "1 Gennaio",   1,  1),
            BlockPreferences.HolidayInfo("epifania",    "Epifania",                "6 Gennaio",   6,  1),
            BlockPreferences.HolidayInfo("liberazione", "Festa della Liberazione", "25 Aprile",  25,  4),
            BlockPreferences.HolidayInfo("lavoratori",  "Festa dei Lavoratori",    "1 Maggio",    1,  5),
            BlockPreferences.HolidayInfo("repubblica",  "Festa della Repubblica",  "2 Giugno",    2,  6),
            BlockPreferences.HolidayInfo("ferragosto",  "Ferragosto",              "15 Agosto",  15,  8),
            BlockPreferences.HolidayInfo("ognissanti",  "Ognissanti",              "1 Novembre",  1, 11),
            BlockPreferences.HolidayInfo("immacolata",  "Immacolata Concezione",   "8 Dicembre",  8, 12),
            BlockPreferences.HolidayInfo("natale",      "Natale",                  "25 Dicembre",25, 12),
            BlockPreferences.HolidayInfo("santo_stefano","Santo Stefano",          "26 Dicembre",26, 12)
        )
    }

    // ── Orario di Lavoro Settimanale (App Sbloccata) ──────────────────────────

    fun isWorkScheduleEnabled(): Boolean = prefs.getBoolean(KEY_WORK_SCHEDULE_ENABLED, true)
    fun setWorkScheduleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WORK_SCHEDULE_ENABLED, enabled).apply()
    }

    // Lunedì - Venerdì
    fun getWorkMonFriFromHour(): Int = prefs.getInt(KEY_WORK_MON_FRI_FROM_H, 8)
    fun getWorkMonFriFromMinute(): Int = prefs.getInt(KEY_WORK_MON_FRI_FROM_M, 0)
    fun getWorkMonFriToHour(): Int = prefs.getInt(KEY_WORK_MON_FRI_TO_H, 18)
    fun getWorkMonFriToMinute(): Int = prefs.getInt(KEY_WORK_MON_FRI_TO_M, 10)

    fun setWorkMonFriTime(fromH: Int, fromM: Int, toH: Int, toM: Int) {
        prefs.edit()
            .putInt(KEY_WORK_MON_FRI_FROM_H, fromH)
            .putInt(KEY_WORK_MON_FRI_FROM_M, fromM)
            .putInt(KEY_WORK_MON_FRI_TO_H, toH)
            .putInt(KEY_WORK_MON_FRI_TO_M, toM)
            .apply()
    }

    // Sabato
    fun isWorkSatEnabled(): Boolean = prefs.getBoolean(KEY_WORK_SAT_ENABLED, true)
    fun setWorkSatEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WORK_SAT_ENABLED, enabled).apply()
    }

    fun getWorkSatFromHour(): Int = prefs.getInt(KEY_WORK_SAT_FROM_H, 8)
    fun getWorkSatFromMinute(): Int = prefs.getInt(KEY_WORK_SAT_FROM_M, 0)
    fun getWorkSatToHour(): Int = prefs.getInt(KEY_WORK_SAT_TO_H, 13)
    fun getWorkSatToMinute(): Int = prefs.getInt(KEY_WORK_SAT_TO_M, 10)

    fun setWorkSatTime(fromH: Int, fromM: Int, toH: Int, toM: Int) {
        prefs.edit()
            .putInt(KEY_WORK_SAT_FROM_H, fromH)
            .putInt(KEY_WORK_SAT_FROM_M, fromM)
            .putInt(KEY_WORK_SAT_TO_H, toH)
            .putInt(KEY_WORK_SAT_TO_M, toM)
            .apply()
    }

    fun resetToDefaultWorkSchedule() {
        setWorkScheduleEnabled(true)
        setWorkMonFriTime(8, 0, 18, 10)
        setWorkSatEnabled(true)
        setWorkSatTime(8, 0, 13, 10)
    }

    /**
     * Ritorna true se il momento attuale rientra nell'orario di lavoro (app sbloccata).
     */
    fun isWorkTime(now: Calendar = Calendar.getInstance()): Boolean {
        if (!isWorkScheduleEnabled()) return false
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val curMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return when (dayOfWeek) {
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY -> {
                val fromMinutes = getWorkMonFriFromHour() * 60 + getWorkMonFriFromMinute()
                val toMinutes = getWorkMonFriToHour() * 60 + getWorkMonFriToMinute()
                curMinutes in fromMinutes until toMinutes
            }
            Calendar.SATURDAY -> {
                if (!isWorkSatEnabled()) return false
                val fromMinutes = getWorkSatFromHour() * 60 + getWorkSatFromMinute()
                val toMinutes = getWorkSatToHour() * 60 + getWorkSatToMinute()
                curMinutes in fromMinutes until toMinutes
            }
            else -> false // Domenica: sempre fuori orario di lavoro (bloccata)
        }
    }

    // ── Programmazioni Personalizzate ─────────────────────────────────────────

    fun getSchedules(): MutableList<Schedule> {
        val json = prefs.getString(KEY_SCHEDULES, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Schedule>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { mutableListOf() }
    }

    fun saveSchedules(schedules: List<Schedule>) {
        prefs.edit().putString(KEY_SCHEDULES, gson.toJson(schedules)).apply()
    }

    fun nextId(): Int {
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    // ── App bloccate ────────────────────────────────────────────────────────────

    fun getBlockedApps(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()

    fun setBlockedApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCKED_APPS, apps).apply()
    }

    // ── Festivi ─────────────────────────────────────────────────────────────────

    fun isHolidaysEnabled(): Boolean = prefs.getBoolean(KEY_HOLIDAYS_ENABLED, false)
    fun setHolidaysEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_HOLIDAYS_ENABLED, v).apply() }

    fun getEnabledHolidays(): Set<String> =
        prefs.getStringSet(KEY_ENABLED_HOLIDAYS, ITALIAN_HOLIDAYS.map { it.id }.toSet()) ?: emptySet()

    fun setEnabledHolidays(h: Set<String>) { prefs.edit().putStringSet(KEY_ENABLED_HOLIDAYS, h).apply() }

    // ── Blocca Adesso ──────────────────────────────────────────────────────────

    fun isBlockNow(): Boolean = prefs.getBoolean(KEY_BLOCK_NOW, false)
    fun getBlockNowUntil(): Long = prefs.getLong(KEY_BLOCK_NOW_UNTIL, 0L)
    fun setBlockNow(enabled: Boolean, until: Long = 0L) {
        prefs.edit().putBoolean(KEY_BLOCK_NOW, enabled).putLong(KEY_BLOCK_NOW_UNTIL, until).apply()
    }

    // ── Sblocco temporaneo ────────────────────────────────────────────────────

    fun isTempUnblock(): Boolean = prefs.getBoolean(KEY_TEMP_UNBLOCK, false)
    fun getTempUnblockUntil(): Long = prefs.getLong(KEY_TEMP_UNBLOCK_UNTIL, 0L)
    fun setTempUnblock(enabled: Boolean, until: Long = 0L) {
        prefs.edit().putBoolean(KEY_TEMP_UNBLOCK, enabled).putLong(KEY_TEMP_UNBLOCK_UNTIL, until).apply()
    }

    // ── Ferie ─────────────────────────────────────────────────────────────────

    fun getVacationStart(): Long = prefs.getLong(KEY_VACATION_START, 0L)
    fun setVacationStart(timestamp: Long) {
        prefs.edit().putLong(KEY_VACATION_START, timestamp).apply()
    }

    fun getVacationEnd(): Long = prefs.getLong(KEY_VACATION_END, 0L)
    fun setVacationEnd(timestamp: Long) {
        prefs.edit().putLong(KEY_VACATION_END, timestamp).apply()
    }

    fun clearVacation() {
        prefs.edit().remove(KEY_VACATION_START).remove(KEY_VACATION_END).apply()
    }

    // ── Valutazione Blocco ───────────────────────────────────────────────────

    /**
     * Ritorna true se c'è un blocco pianificato (orario settimanale, ferie, festivi o custom)
     * attivo in questo momento, INDIPENDENTEMENTE da sblocchi temporanei o blocchi manuali.
     */
    fun isScheduledBlockActive(now: Calendar = Calendar.getInstance()): Boolean {
        // Ferie
        if (isTodayInVacation(now)) return true

        // Festivi
        if (isHolidaysEnabled() && isTodayHoliday(now)) return true

        // Orario di lavoro settimanale: se abilitato, fuori dall'orario di lavoro è bloccato!
        if (isWorkScheduleEnabled()) {
            if (!isWorkTime(now)) return true
        }

        // Programmazioni custom
        return activeCustomSchedule(now) != null
    }

    /**
     * Ritorna la programmazione custom attiva, se presente.
     */
    fun activeCustomSchedule(now: Calendar = Calendar.getInstance()): Schedule? {
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        val prevDay = if (currentDay == Calendar.SUNDAY) Calendar.SATURDAY else currentDay - 1
        val cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return getSchedules().firstOrNull { s ->
            if (!s.enabled) return@firstOrNull false
            if (s.allDay) return@firstOrNull s.days.contains(currentDay)

            val from = s.fromHour * 60 + s.fromMinute
            val to = s.toHour * 60 + s.toMinute

            if (from <= to) {
                s.days.contains(currentDay) && cur in from until to
            } else {
                (s.days.contains(currentDay) && cur >= from) || (s.days.contains(prevDay) && cur < to)
            }
        }
    }

    /**
     * Ritorna true se le app devono essere bloccate ORA.
     * Ordine di priorità:
     * 1. Sblocco Temporaneo (PREVALE SU TUTTO: ferie, festivi, orari e blocco manuale)
     * 2. Blocco Manuale "Blocca Adesso"
     * 3. Programmazioni automatiche (Ferie, Festivi, Orario di Lavoro, Custom)
     */
    fun shouldBlock(): Boolean {
        // 1) SBLOCCO TEMPORANEO: PREVALE SU TUTTO!
        if (isTempUnblock()) {
            val until = getTempUnblockUntil()
            if (System.currentTimeMillis() < until) {
                return false
            } else {
                setTempUnblock(false, 0L)
            }
        }

        // 2) BLOCCO MANUALE ("Blocca Adesso")
        if (isBlockNow()) {
            val until = getBlockNowUntil()
            if (until == 0L || System.currentTimeMillis() < until) {
                return true
            } else {
                // Blocco manuale scaduto (ha raggiunto l'inizio della programmazione)
                setBlockNow(false, 0L)
            }
        }

        // 3) PROGRAMMAZIONI AUTOMATICHE
        val now = Calendar.getInstance()
        return isScheduledBlockActive(now)
    }

    /**
     * Calcola il timestamp in millisecondi in cui inizierà la PROSSIMA programmazione di blocco.
     * Usato per "Blocca Adesso" quando nessuna programmazione è attualmente attiva.
     */
    fun getNextScheduledBlockMillis(now: Calendar = Calendar.getInstance()): Long {
        if (isScheduledBlockActive(now)) return now.timeInMillis

        var earliestMillis = Long.MAX_VALUE

        // 1. Orario di lavoro: il blocco inizia alla fine dell'orario sbloccato di oggi
        if (isWorkScheduleEnabled()) {
            val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
            val curMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            if (dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY) {
                val toHour = getWorkMonFriToHour()
                val toMinute = getWorkMonFriToMinute()
                val toMinutes = toHour * 60 + toMinute
                if (curMinutes < toMinutes) {
                    val cal = (now.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, toHour)
                        set(Calendar.MINUTE, toMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    earliestMillis = Math.min(earliestMillis, cal.timeInMillis)
                }
            } else if (dayOfWeek == Calendar.SATURDAY && isWorkSatEnabled()) {
                val toHour = getWorkSatToHour()
                val toMinute = getWorkSatToMinute()
                val toMinutes = toHour * 60 + toMinute
                if (curMinutes < toMinutes) {
                    val cal = (now.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, toHour)
                        set(Calendar.MINUTE, toMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    earliestMillis = Math.min(earliestMillis, cal.timeInMillis)
                }
            }
        }

        // 2. Eventuali blocchi custom che iniziano più tardi oggi
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        val cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        for (s in getSchedules()) {
            if (!s.enabled || s.allDay) continue
            if (s.days.contains(currentDay)) {
                val fromMinutes = s.fromHour * 60 + s.fromMinute
                if (fromMinutes > cur) {
                    val cal = (now.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, s.fromHour)
                        set(Calendar.MINUTE, s.fromMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    earliestMillis = Math.min(earliestMillis, cal.timeInMillis)
                }
            }
        }

        return if (earliestMillis != Long.MAX_VALUE) earliestMillis else 0L
    }

    /**
     * Ritorna una descrizione dettagliata del motivo per cui l'app è bloccata.
     */
    fun getActiveBlockDescription(now: Calendar = Calendar.getInstance()): String {
        if (isTempUnblock()) {
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALY)
            return "Sbloccata temporaneamente fino alle ${sdf.format(java.util.Date(getTempUnblockUntil()))}"
        }
        if (isBlockNow()) {
            val until = getBlockNowUntil()
            return if (until > 0L) {
                val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALY)
                "Bloccata manualmente fino alle ${sdf.format(java.util.Date(until))}"
            } else {
                "Bloccata manualmente"
            }
        }
        if (isTodayInVacation(now)) return "Periodo di Ferie (24h)"
        if (isHolidaysEnabled() && isTodayHoliday(now)) return "Giorno Festivo"

        val custom = activeCustomSchedule(now)
        if (custom != null) return "Programmazione: ${custom.name}"

        if (isWorkScheduleEnabled() && !isWorkTime(now)) {
            val day = now.get(Calendar.DAY_OF_WEEK)
            val curMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            return when (day) {
                Calendar.SUNDAY -> "Weekend (Domenica tutto il giorno)"
                Calendar.SATURDAY -> {
                    val satToMinutes = getWorkSatToHour() * 60 + getWorkSatToMinute()
                    if (curMinutes >= satToMinutes) "Weekend (Sabato pomeriggio)" else "Weekend (Sabato mattina)"
                }
                Calendar.MONDAY -> {
                    val monFromMinutes = getWorkMonFriFromHour() * 60 + getWorkMonFriFromMinute()
                    if (curMinutes < monFromMinutes) "Fine Weekend (Lunedì prima delle 08:00)" else "Fuori orario (Serale)"
                }
                else -> {
                    val fromMinutes = getWorkMonFriFromHour() * 60 + getWorkMonFriFromMinute()
                    if (curMinutes < fromMinutes) "Fuori orario (Notte/Mattina)" else "Fuori orario (Serale)"
                }
            }
        }

        return "Bloccata da programmazione"
    }

    /**
     * Ritorna la programmazione attiva in questo momento (per retrocompatibilità).
     */
    fun activeSchedule(): Schedule? {
        val now = Calendar.getInstance()
        if (isTodayInVacation(now)) {
            return Schedule(-1, "Ferie", mutableListOf(), true, 0, 0, 0, 0, true, false)
        }
        if (isHolidaysEnabled() && isTodayHoliday(now)) {
            return Schedule(-2, "Festivo", mutableListOf(), true, 0, 0, 0, 0, true, false)
        }
        val custom = activeCustomSchedule(now)
        if (custom != null) return custom

        if (isWorkScheduleEnabled() && !isWorkTime(now)) {
            return Schedule(-3, getActiveBlockDescription(now), mutableListOf(), false, 0, 0, 0, 0, true, false)
        }
        return null
    }

    private fun isTodayInVacation(now: Calendar): Boolean {
        val start = getVacationStart()
        val end = getVacationEnd()
        if (start == 0L || end == 0L) return false

        val today = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startDay = Calendar.getInstance().apply {
            timeInMillis = start
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endDay = Calendar.getInstance().apply {
            timeInMillis = end
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        return today in startDay..endDay
    }

    private fun isTodayHoliday(now: Calendar): Boolean {
        val d = now.get(Calendar.DAY_OF_MONTH)
        val m = now.get(Calendar.MONTH) + 1
        val y = now.get(Calendar.YEAR)
        val enabled = getEnabledHolidays()
        for (h in ITALIAN_HOLIDAYS) {
            if (enabled.contains(h.id) && h.day == d && h.month == m) return true
        }
        // Pasqua
        if (enabled.contains("pasqua") || enabled.contains("pasquetta")) {
            val easter = calcEaster(y)
            if (enabled.contains("pasqua") &&
                easter.get(Calendar.DAY_OF_MONTH) == d &&
                easter.get(Calendar.MONTH) + 1 == m) return true
            if (enabled.contains("pasquetta")) {
                easter.add(Calendar.DAY_OF_MONTH, 1)
                if (easter.get(Calendar.DAY_OF_MONTH) == d &&
                    easter.get(Calendar.MONTH) + 1 == m) return true
            }
        }
        return false
    }

    private fun calcEaster(year: Int): Calendar {
        val a = year % 19; val b = year / 100; val c = year % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25
        val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m2 = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m2 + 114) / 31
        val day = ((h + l - 7 * m2 + 114) % 31) + 1
        return Calendar.getInstance().also { it.set(year, month - 1, day) }
    }
}
