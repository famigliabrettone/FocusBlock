package com.example.bloccowhatsapp

import java.util.Calendar

/**
 * Motore di valutazione: controlla se al momento attuale
 * le app selezionate devono essere bloccate o meno.
 */
class BlockEngine(private val prefs: BlockPreferences) {

    /**
     * Ritorna true se le app devono essere bloccate in questo momento.
     */
    fun shouldBlock(): Boolean {
        // 1) Sblocco temporaneo attivo? → non bloccare
        if (prefs.isTempUnblock()) {
            val until = prefs.getTempUnblockUntil()
            if (System.currentTimeMillis() < until) {
                return false
            } else {
                // Sblocco temporaneo scaduto, disattivalo
                prefs.setTempUnblock(false, 0L)
            }
        }

        // 2) Blocco manuale "Blocca Adesso" attivo?
        if (prefs.isBlockNow()) {
            val until = prefs.getBlockNowUntil()
            if (until == 0L || System.currentTimeMillis() < until) {
                return true
            } else {
                // Blocco manuale scaduto
                prefs.setBlockNow(false, 0L)
            }
        }

        // 3) La programmazione è attiva?
        if (!prefs.isScheduleEnabled()) return false

        val now = Calendar.getInstance()

        // 4) Controlla se oggi è un giorno festivo
        if (prefs.isHolidaysEnabled() && isTodayHoliday(now)) {
            return true
        }

        // 5) Controlla se oggi è un giorno bloccato (es. Domenica)
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val blockedDays = prefs.getBlockedDays()

        if (blockedDays.contains(dayOfWeek)) {
            // Se "giornata intera" è attivo, blocca tutto il giorno
            if (prefs.isAllDay()) {
                return true
            }
            // Altrimenti controlla la fascia oraria
            return isInTimeRange(now)
        }

        return false
    }

    /**
     * Controlla se l'orario attuale è nella fascia oraria di blocco.
     */
    private fun isInTimeRange(now: Calendar): Boolean {
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val fromMinutes = prefs.getTimeFromHour() * 60 + prefs.getTimeFromMinute()
        val toMinutes = prefs.getTimeToHour() * 60 + prefs.getTimeToMinute()

        return if (fromMinutes <= toMinutes) {
            // Esempio: dalle 09:00 alle 17:00
            currentMinutes in fromMinutes..toMinutes
        } else {
            // Esempio: dalle 19:00 alle 08:00 (attraversa la mezzanotte)
            currentMinutes >= fromMinutes || currentMinutes <= toMinutes
        }
    }

    /**
     * Controlla se oggi è un giorno festivo italiano.
     */
    private fun isTodayHoliday(now: Calendar): Boolean {
        val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
        val month = now.get(Calendar.MONTH) + 1 // Calendar.MONTH è 0-based
        val year = now.get(Calendar.YEAR)
        val enabledHolidays = prefs.getEnabledHolidays()

        // Controlla i festivi fissi
        for (holiday in BlockPreferences.ITALIAN_HOLIDAYS) {
            if (enabledHolidays.contains(holiday.id) &&
                holiday.day == dayOfMonth &&
                holiday.month == month
            ) {
                return true
            }
        }

        // Controlla Pasqua e Pasquetta (date variabili)
        if (enabledHolidays.contains("pasqua") || enabledHolidays.contains("pasquetta")) {
            val easter = calculateEaster(year)
            if (enabledHolidays.contains("pasqua") &&
                easter.get(Calendar.DAY_OF_MONTH) == dayOfMonth &&
                easter.get(Calendar.MONTH) + 1 == month
            ) {
                return true
            }
            if (enabledHolidays.contains("pasquetta")) {
                easter.add(Calendar.DAY_OF_MONTH, 1) // Pasquetta = Pasqua + 1
                if (easter.get(Calendar.DAY_OF_MONTH) == dayOfMonth &&
                    easter.get(Calendar.MONTH) + 1 == month
                ) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Calcola la data di Pasqua per un dato anno (algoritmo di Gauss).
     */
    private fun calculateEaster(year: Int): Calendar {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1

        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day) // month - 1 perché Calendar è 0-based
        return cal
    }
}
