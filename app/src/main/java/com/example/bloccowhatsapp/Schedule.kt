package com.example.bloccowhatsapp

/**
 * Rappresenta una singola programmazione di blocco.
 *
 * @param id           ID univoco (usato anche come request code per AlarmManager)
 * @param name         Nome personalizzato, es. "Lun-Ven lavoro"
 * @param days         Set di giorni (Calendar.MONDAY=2 … Calendar.SUNDAY=1)
 * @param allDay       Se true ignora fromHour/fromMin/toHour/toMin
 * @param fromHour     Ora di inizio blocco (0-23)
 * @param fromMinute   Minuto di inizio blocco (0-59)
 * @param toHour       Ora di fine blocco (0-23)
 * @param toMinute     Minuto di fine blocco (0-59)
 * @param enabled      Se la programmazione è attiva
 * @param notifyBefore Se true manda una notifica 10 min prima dell'inizio
 */
data class Schedule(
    val id: Int,
    var name: String,
    var days: MutableList<Int>,
    var allDay: Boolean,
    var fromHour: Int,
    var fromMinute: Int,
    var toHour: Int,
    var toMinute: Int,
    var enabled: Boolean,
    var notifyBefore: Boolean
) {
    /** Descrizione testuale dell'orario, es. "08:00 → 18:00" o "Giornata intera" */
    fun timeDescription(): String {
        return if (allDay) "Giornata intera"
        else "%02d:%02d → %02d:%02d".format(fromHour, fromMinute, toHour, toMinute)
    }

    /** Descrizione testuale dei giorni, es. "LUN MAR MER GIO VEN" */
    fun daysDescription(): String {
        val names = mapOf(
            2 to "LUN", 3 to "MAR", 4 to "MER",
            5 to "GIO", 6 to "VEN", 7 to "SAB", 1 to "DOM"
        )
        // Ordine fisso Lun→Dom
        val ordered = listOf(2, 3, 4, 5, 6, 7, 1)
        return ordered.filter { days.contains(it) }.joinToString(" ") { names[it] ?: "" }
    }
}
