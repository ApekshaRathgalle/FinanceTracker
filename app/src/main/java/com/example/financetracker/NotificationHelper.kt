package com.example.financetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class NotificationHelper(private val context: Context) {

    companion object {
        // Notification Channel IDs
        const val CHANNEL_ID_BUDGET_ALERTS = "budget_alerts_channel"
        const val CHANNEL_ID_REMINDERS = "reminders_channel"
        const val CHANNEL_ID_SYSTEM = "system_notifications_channel"

        // Notification IDs
        const val NOTIFICATION_ID_BUDGET_WARNING = 1001
        const val NOTIFICATION_ID_BUDGET_EXCEEDED = 1002
        const val NOTIFICATION_ID_DAILY_REMINDER = 1003
        const val NOTIFICATION_ID_LOW_BALANCE = 1004
        const val NOTIFICATION_ID_WALLET_ADDED = 1005
        const val NOTIFICATION_ID_BACKUP_REMINDER = 1006
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Budget Alerts Channel (High importance)
            val budgetAlertsChannel = NotificationChannel(
                CHANNEL_ID_BUDGET_ALERTS,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications related to budget limits and warnings"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            // Reminders Channel (Default importance)
            val remindersChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily and periodic reminders"
                enableLights(true)
                lightColor = Color.BLUE
            }

            // System Notifications Channel (Low importance)
            val systemChannel = NotificationChannel(
                CHANNEL_ID_SYSTEM,
                "System Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "System and confirmation notifications"
            }

            notificationManager.createNotificationChannels(
                listOf(budgetAlertsChannel, remindersChannel, systemChannel)
            )
        }
    }

    // Budget Limit Warning notification (80% of budget)
    fun showBudgetWarningNotification(walletName: String, percentage: Int) {
        val title = "Budget Warning"
        val message = "You've used $percentage% of your '$walletName' budget"

        val intent = Intent(context, Homepage::class.java)
        showNotification(
            NOTIFICATION_ID_BUDGET_WARNING,
            title,
            message,
            CHANNEL_ID_BUDGET_ALERTS,
            intent,
            R.drawable.ic_warning
        )
        saveNotification(title, message)
    }

    // Budget Exceeded Alert notification (>100% of budget)
    fun showBudgetExceededNotification(walletName: String, percentage: Int) {
        val title = "Budget Exceeded!"
        val message = "You have exceeded your '$walletName' budget by ${percentage - 100}%"

        val intent = Intent(context, Budget::class.java)
        showNotification(
            NOTIFICATION_ID_BUDGET_EXCEEDED,
            title,
            message,
            CHANNEL_ID_BUDGET_ALERTS,
            intent,
            R.drawable.ic_alert
        )
        saveNotification(title, message)
    }

    // Daily Expense Reminder notification
    fun showDailyExpenseReminder() {
        val title = "Daily Expense Reminder"
        val message = "Don't forget to log today's expenses!"

        val intent = Intent(context, Transactions::class.java)
        showNotification(
            NOTIFICATION_ID_DAILY_REMINDER,
            title,
            message,
            CHANNEL_ID_REMINDERS,
            intent,
            R.drawable.ic_reminder
        )
        saveNotification(title, message)
    }

    // Low Wallet Balance Warning notification (<10% of initial balance)
    fun showLowBalanceWarning(walletName: String, percentage: Int) {
        val title = "Low Balance Warning"
        val message = "Your '$walletName' wallet is down to $percentage% of its initial balance"

        val intent = Intent(context, Budget::class.java)
        showNotification(
            NOTIFICATION_ID_LOW_BALANCE,
            title,
            message,
            CHANNEL_ID_BUDGET_ALERTS,
            intent,
            R.drawable.ic_low_balance
        )
        saveNotification(title, message)
    }

    // New Wallet Added Success notification
    fun showWalletAddedNotification(walletName: String) {
        val title = "Wallet Added"
        val message = "New wallet '$walletName' added successfully!"

        val intent = Intent(context, Budget::class.java)
        showNotification(
            NOTIFICATION_ID_WALLET_ADDED,
            title,
            message,
            CHANNEL_ID_SYSTEM,
            intent,
            R.drawable.wallet
        )
        saveNotification(title, message)
    }

    // Backup Reminder notification
    fun showBackupReminder() {
        val title = "Backup Reminder"
        val message = "It's time to backup your financial data to avoid loss"

        val intent = Intent(context, Homepage::class.java)
        showNotification(
            NOTIFICATION_ID_BACKUP_REMINDER,
            title,
            message,
            CHANNEL_ID_REMINDERS,
            intent,
            R.drawable.ic_backup
        )
        saveNotification(title, message)
    }

    // Generic method to show notification
    private fun showNotification(
        notificationId: Int,
        title: String,
        message: String,
        channelId: String,
        intent: Intent,
        icon: Int
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(icon)
            .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun saveNotification(title: String, message: String) {
        val sharedPreferences = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val notificationsJson = sharedPreferences.getString("notifications", "[]")
        val notificationsArray = JSONArray(notificationsJson)

        val notification = JSONObject().apply {
            put("id", System.currentTimeMillis().toInt())
            put("title", title)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
            put("isRead", false)
        }

        notificationsArray.put(notification)

        sharedPreferences.edit()
            .putString("notifications", notificationsArray.toString())
            .apply()
    }
}