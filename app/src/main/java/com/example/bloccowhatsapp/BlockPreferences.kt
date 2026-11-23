package com.example.bloccowhatsapp

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestisce il salvataggio e il caricamento delle impostazioni di blocco.
 */
class BlockPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("block_prefs", Context.MODE_PRIVATE)

    companion object {
        // Chiavi per le preferenze
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        private const val KEY_BLOCKED_DAYS = "blocked_days"         // es: "1,7" = Domenica, Sabato
        private const val KEY_TIME_FROM_HOUR = "time_from_hour"
        private const val KEY_TIME_FROM_MINUTE = "time_from_minute"
        private const val KEY_TIME_TO_HOUR = "time_to_hour"
        private const val KEY_TIME_TO_MINUTE = "time_to_minute"
        private const val KEY_ALL_DAY = "all_day"
        private const val KEY_HOLIDAYS_ENABLED = "holidays_enabled"
        private const val KEY_ENABLED_HOLIDAYS = "enabled_holidays"  // lista dei festivi attivi
        private const val KEY_BLOCK_NOW = "block_now"
        private const val KEY_BLOCK_NOW_UNTIL = "block_now_until"    // timestamp
        private const val KEY_TEMP_UNBLOCK = "temp_unblock"
        private const val KEY_TEMP_UNBLOCK_UNTIL = "temp_unblock_until"  // timestamp
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"

        // App supportate
        val SUPPORTED_APPS = listOf(
            AppInfo("com.whatsapp", "WhatsApp", "💬"),
            AppInfo("com.whatsapp.w4b", "WhatsApp Business", "💼"),
            AppInfo("org.telegram.messenger", "Telegram", "✈️"),
            AppInfo("com.instagram.android", "Instagram", "📷"),
            AppInfo("com.facebook.katana", "Facebook", "📘"),
            AppInfo("com.facebook.orca", "Messenger", "💬"),
            AppInfo("com.twitter.android", "X (Twitter)", "🐦"),
            AppInfo("com.zhiliaoapp.musically", "TikTok", "🎵"),
            AppInfo("com.snapchat.android", "Snapchat", "👻")
        )

        // Festivi italiani
        val ITALIAN_HOLIDAYS = listOf(
            HolidayInfo("capodanno", "Capodanno", "1 Gennaio", 1, 1),
            HolidayInfo("epifania", "Epifania", "6 Gennaio", 6, 1),
            HolidayInfo("liberazione", "Festa della Liberazione", "25 Aprile", 25, 4),
            HolidayInfo("lavoratori", "Festa dei Lavoratori", "1 Maggio", 1, 5),
            HolidayInfo("repubblica", "Festa della Repubblica", "2 Giugno", 2, 6),
            HolidayInfo("ferragosto", "Ferragosto", "15 Agosto", 15, 8),
            HolidayInfo("ognissanti", "Ognissanti", "1 Novembre", 1, 11),
            HolidayInfo("immacolata", "Immacolata Concezione", "8 Dicembre", 8, 12),
            HolidayInfo("natale", "Natale", "25 Dicembre", 25, 12),
            HolidayInfo("santo_stefano", "Santo Stefano", "26 Dicembre", 26, 12)
            // Pasqua e Pasquetta sono variabili, gestite nel codice
        )
    }

    // --- App bloccate ---
    fun getBlockedApps(): Set<String> {
        return prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()
    }

    fun setBlockedApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCKED_APPS, apps).apply()
    }

    // --- Giorni bloccati (1=Domenica, 2=Lunedì, ..., 7=Sabato nel Calendar) ---
    fun getBlockedDays(): Set<Int> {
        val str = prefs.getString(KEY_BLOCKED_DAYS, "") ?: ""
        if (str.isEmpty()) return emptySet()
        return str.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun setBlockedDays(days: Set<Int>) {
        prefs.edit().putString(KEY_BLOCKED_DAYS, days.joinToString(",")).apply()
    }

    // --- Orario di blocco ---
    fun getTimeFromHour(): Int = prefs.getInt(KEY_TIME_FROM_HOUR, 19)
    fun getTimeFromMinute(): Int = prefs.getInt(KEY_TIME_FROM_MINUTE, 0)
    fun getTimeToHour(): Int = prefs.getInt(KEY_TIME_TO_HOUR, 8)
    fun getTimeToMinute(): Int = prefs.getInt(KEY_TIME_TO_MINUTE, 0)

    fun setTimeRange(fromHour: Int, fromMin: Int, toHour: Int, toMin: Int) {
        prefs.edit()
            .putInt(KEY_TIME_FROM_HOUR, fromHour)
            .putInt(KEY_TIME_FROM_MINUTE, fromMin)
            .putInt(KEY_TIME_TO_HOUR, toHour)
            .putInt(KEY_TIME_TO_MINUTE, toMin)
            .apply()
    }

    // --- Giornata intera ---
    fun isAllDay(): Boolean = prefs.getBoolean(KEY_ALL_DAY, false)
    fun setAllDay(allDay: Boolean) {
        prefs.edit().putBoolean(KEY_ALL_DAY, allDay).apply()
    }

    // --- Festivi ---
    fun isHolidaysEnabled(): Boolean = prefs.getBoolean(KEY_HOLIDAYS_ENABLED, false)
    fun setHolidaysEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HOLIDAYS_ENABLED, enabled).apply()
    }

    fun getEnabledHolidays(): Set<String> {
        return prefs.getStringSet(KEY_ENABLED_HOLIDAYS, ITALIAN_HOLIDAYS.map { it.id }.toSet())
            ?: emptySet()
    }

    fun setEnabledHolidays(holidays: Set<String>) {
        prefs.edit().putStringSet(KEY_ENABLED_HOLIDAYS, holidays).apply()
    }

    // --- Blocca Adesso ---
    fun isBlockNow(): Boolean = prefs.getBoolean(KEY_BLOCK_NOW, false)
    fun getBlockNowUntil(): Long = prefs.getLong(KEY_BLOCK_NOW_UNTIL, 0L)

    fun setBlockNow(enabled: Boolean, untilTimestamp: Long = 0L) {
        prefs.edit()
            .putBoolean(KEY_BLOCK_NOW, enabled)
            .putLong(KEY_BLOCK_NOW_UNTIL, untilTimestamp)
            .apply()
    }

    // --- Sblocco temporaneo ---
    fun isTempUnblock(): Boolean = prefs.getBoolean(KEY_TEMP_UNBLOCK, false)
    fun getTempUnblockUntil(): Long = prefs.getLong(KEY_TEMP_UNBLOCK_UNTIL, 0L)

    fun setTempUnblock(enabled: Boolean, untilTimestamp: Long = 0L) {
        prefs.edit()
            .putBoolean(KEY_TEMP_UNBLOCK, enabled)
            .putLong(KEY_TEMP_UNBLOCK_UNTIL, untilTimestamp)
            .apply()
    }

    // --- Programmazione attiva ---
    fun isScheduleEnabled(): Boolean = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)
    fun setScheduleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCHEDULE_ENABLED, enabled).apply()
    }

    data class AppInfo(val packageName: String, val name: String, val emoji: String)
    data class HolidayInfo(val id: String, val name: String, val dateText: String, val day: Int, val month: Int)
}
