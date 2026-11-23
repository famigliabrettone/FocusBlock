package com.example.bloccowhatsapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Gestisce la pianificazione degli allarmi per inviare
 * una notifica 10 minuti prima che inizi un blocco.
 */
class ScheduleAlarmManager {

    companion object {
        const val CHANNEL_ID = "blocco_app_channel"
        const val CHANNEL_NAME = "Blocco App"
        const val EXTRA_SCHEDULE_NAME = "schedule_name"
        const val EXTRA_SCHEDULE_TIME = "schedule_time"

        /**
         * Crea il canale di notifica (chiamare all'avvio dell'app).
         */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avvisi prima dell'attivazione del blocco app"
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }

        /**
         * Pianifica le notifiche "10 minuti prima" per l'orario settimanale e per le programmazioni custom.
         * Cancella prima tutti gli allarmi precedenti.
         */
        @SuppressLint("ScheduleExactAlarm")
        fun rescheduleAll(context: Context, schedules: List<Schedule>) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Cancella allarmi precedenti (ID 1000..1099)
            for (i in 1000..1099) {
                val pi = pendingIntentForId(context, i, "", "", false)
                am.cancel(pi)
            }

            var alarmIndex = 1000
            val now = Calendar.getInstance()
            val manager = ScheduleManager(context)

            // 1. Allarmi per Orario Settimanale di Lavoro
            if (manager.isWorkScheduleEnabled()) {
                val monFriToH = manager.getWorkMonFriToHour()
                val monFriToM = manager.getWorkMonFriToMinute()
                val timeTextMonFri = "%02d:%02d".format(monFriToH, monFriToM)

                val workDays = listOf(
                    Calendar.MONDAY,
                    Calendar.TUESDAY,
                    Calendar.WEDNESDAY,
                    Calendar.THURSDAY,
                    Calendar.FRIDAY
                )
                for (day in workDays) {
                    if (alarmIndex > 1099) break
                    val triggerCal = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_WEEK, day)
                        set(Calendar.HOUR_OF_DAY, monFriToH)
                        set(Calendar.MINUTE, monFriToM - 10)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
                    }
                    val pi = pendingIntentForId(
                        context, alarmIndex, "Blocco serale", timeTextMonFri, true
                    )
                    scheduleAlarm(am, triggerCal.timeInMillis, pi)
                    alarmIndex++
                }

                if (manager.isWorkSatEnabled()) {
                    val satToH = manager.getWorkSatToHour()
                    val satToM = manager.getWorkSatToMinute()
                    val timeTextSat = "%02d:%02d".format(satToH, satToM)
                    val triggerCal = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
                        set(Calendar.HOUR_OF_DAY, satToH)
                        set(Calendar.MINUTE, satToM - 10)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
                    }
                    val pi = pendingIntentForId(
                        context, alarmIndex, "Blocco weekend", timeTextSat, true
                    )
                    scheduleAlarm(am, triggerCal.timeInMillis, pi)
                    alarmIndex++
                }
            }

            // 2. Allarmi per programmazioni custom
            for (schedule in schedules) {
                if (!schedule.enabled || !schedule.notifyBefore || schedule.allDay) continue

                for (day in schedule.days) {
                    if (alarmIndex > 1099) break

                    val triggerCal = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_WEEK, day)
                        set(Calendar.HOUR_OF_DAY, schedule.fromHour)
                        set(Calendar.MINUTE, schedule.fromMinute - 10)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
                    }

                    val timeText = "%02d:%02d".format(schedule.fromHour, schedule.fromMinute)
                    val pi = pendingIntentForId(
                        context, alarmIndex, schedule.name, timeText, true
                    )
                    scheduleAlarm(am, triggerCal.timeInMillis, pi)
                    alarmIndex++
                }
            }
        }

        private fun scheduleAlarm(am: AlarmManager, timeInMillis: Long, pi: PendingIntent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pi)
                    }
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pi)
                }
            } catch (_: SecurityException) {
                // Permesso non garantito, skip
            }
        }

        private fun pendingIntentForId(
            context: Context,
            id: Int,
            scheduleName: String,
            scheduleTime: String,
            create: Boolean
        ): PendingIntent {
            val intent = Intent(context, ScheduleNotificationReceiver::class.java).apply {
                putExtra(EXTRA_SCHEDULE_NAME, scheduleName)
                putExtra(EXTRA_SCHEDULE_TIME, scheduleTime)
            }
            val flags = if (create)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, id, intent, flags)
                ?: PendingIntent.getBroadcast(
                    context, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        }
    }
}

/**
 * Ricevitore dell'allarme: mostra la notifica "10 minuti prima".
 */
class ScheduleNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(ScheduleAlarmManager.EXTRA_SCHEDULE_NAME) ?: "Blocco"
        val time = intent.getStringExtra(ScheduleAlarmManager.EXTRA_SCHEDULE_TIME) ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, ScheduleAlarmManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("🔒 Blocco tra 10 minuti")
            .setContentText("\"$name\" si attiva alle $time")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Il blocco \"$name\" si attiverà alle $time.\nPrepara ciò che ti serve prima del blocco.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
