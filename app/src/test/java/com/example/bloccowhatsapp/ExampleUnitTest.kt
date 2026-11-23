package com.example.bloccowhatsapp

import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

class ExampleUnitTest {

    private fun isWorkTimeHelper(
        now: Calendar,
        enabled: Boolean = true,
        monFriFromH: Int = 8, monFriFromM: Int = 0,
        monFriToH: Int = 18, monFriToM: Int = 10,
        satEnabled: Boolean = true,
        satFromH: Int = 8, satFromM: Int = 0,
        satToH: Int = 13, satToM: Int = 10
    ): Boolean {
        if (!enabled) return false
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val curMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        return when (dayOfWeek) {
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY -> {
                val fromMinutes = monFriFromH * 60 + monFriFromM
                val toMinutes = monFriToH * 60 + monFriToM
                curMinutes in fromMinutes until toMinutes
            }
            Calendar.SATURDAY -> {
                if (!satEnabled) return false
                val fromMinutes = satFromH * 60 + satFromM
                val toMinutes = satToH * 60 + satToM
                curMinutes in fromMinutes until toMinutes
            }
            else -> false // Domenica
        }
    }

    private fun makeCal(dayOfWeek: Int, hour: Int, minute: Int): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    @Test
    fun testWorkSchedule_MondayToFriday() {
        // Lunedì prima delle 08:00 -> Fuori orario (BLOCCATO)
        assertFalse(isWorkTimeHelper(makeCal(Calendar.MONDAY, 7, 59)))

        // Lunedì esattamente alle 08:00 -> SBLOCCATO
        assertTrue(isWorkTimeHelper(makeCal(Calendar.MONDAY, 8, 0)))

        // Martedì pomeriggio alle 14:30 -> SBLOCCATO
        assertTrue(isWorkTimeHelper(makeCal(Calendar.TUESDAY, 14, 30)))

        // Venerdì alle 18:09 -> SBLOCCATO
        assertTrue(isWorkTimeHelper(makeCal(Calendar.FRIDAY, 18, 9)))

        // Venerdì esattamente alle 18:10 -> Fuori orario (BLOCCATO)
        assertFalse(isWorkTimeHelper(makeCal(Calendar.FRIDAY, 18, 10)))

        // Mercoledì sera alle 21:00 -> Fuori orario (BLOCCATO)
        assertFalse(isWorkTimeHelper(makeCal(Calendar.WEDNESDAY, 21, 0)))
    }

    @Test
    fun testWorkSchedule_Saturday() {
        // Sabato mattina prima delle 08:00 -> Fuori orario (BLOCCATO)
        assertFalse(isWorkTimeHelper(makeCal(Calendar.SATURDAY, 7, 59)))

        // Sabato alle 08:00 -> SBLOCCATO
        assertTrue(isWorkTimeHelper(makeCal(Calendar.SATURDAY, 8, 0)))

        // Sabato alle 11:00 -> SBLOCCATO
        assertTrue(isWorkTimeHelper(makeCal(Calendar.SATURDAY, 11, 0)))

        // Sabato alle 13:09 -> SBLOCCATO
        assertTrue(isWorkTimeHelper(makeCal(Calendar.SATURDAY, 13, 9)))

        // Sabato alle 13:10 -> Fuori orario (BLOCCATO)
        assertFalse(isWorkTimeHelper(makeCal(Calendar.SATURDAY, 13, 10)))

        // Sabato pomeriggio alle 15:00 -> Fuori orario (BLOCCATO)
        assertFalse(isWorkTimeHelper(makeCal(Calendar.SATURDAY, 15, 0)))

        // Sabato sera alle 23:00 -> Fuori orario (BLOCCATO)
        assertFalse(isWorkTimeHelper(makeCal(Calendar.SATURDAY, 23, 0)))
    }

    @Test
    fun testWorkSchedule_Sunday() {
        // Domenica notte -> Fuori orario (BLOCCATO)
        assertFalse(isWorkTimeHelper(makeCal(Calendar.SUNDAY, 2, 0)))

        // Domenica mattina -> Fuori orario (BLOCCATO)
        assertFalse(isWorkTimeHelper(makeCal(Calendar.SUNDAY, 10, 0)))

        // Domenica sera -> Fuori orario (BLOCCATO)
        assertFalse(isWorkTimeHelper(makeCal(Calendar.SUNDAY, 20, 0)))
    }
}
