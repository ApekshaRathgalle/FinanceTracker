package com.example.financetracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class NotificationScheduler {

    companion object {
        const val ACTION_DAILY_REMINDER = "com.example.financetracker.DAILY_REMINDER"
        const val ACTION_BACKUP_REMINDER = "com.example.financetracker.BACKUP_REMINDER"
        const val ACTION_DAILY_SUMMARY = "com.example.financetracker.DAILY_SUMMARY"

        const val REQUEST_CODE_DAILY = 2001
        const val REQUEST_CODE_BACKUP = 2002
        const val REQUEST_CODE_SUMMARY = 2003
    }

    // Schedule daily expense reminder (9 PM every day)
    fun scheduleDailyExpenseReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set time to 9 PM
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)

            // If it's already past 9 PM, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Schedule repeating alarm using more reliable method for newer Android versions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }
    }

    // Schedule daily expense summary notification (11 PM every day)
    fun scheduleDailyExpenseSummary(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_DAILY_SUMMARY
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SUMMARY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set time to 11 PM
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)

            // If it's already past 11 PM, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Schedule repeating alarm using more reliable method for newer Android versions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }
    }

    // Schedule monthly backup reminder (1st day of each month)
    fun scheduleMonthlyBackupReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_BACKUP_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BACKUP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set time to 1st day of next month at noon
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        // Schedule using more reliable method for newer Android versions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    // Cancel scheduled notifications
    fun cancelAllScheduledNotifications(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel daily reminder
        val dailyIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        val dailyPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY,
            dailyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(dailyPendingIntent)

        // Cancel daily summary
        val summaryIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_DAILY_SUMMARY
        }
        val summaryPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SUMMARY,
            summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(summaryPendingIntent)

        // Cancel backup reminder
        val backupIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_BACKUP_REMINDER
        }
        val backupPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BACKUP,
            backupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(backupPendingIntent)
    }
}