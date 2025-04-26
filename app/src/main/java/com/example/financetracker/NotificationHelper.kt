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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        const val NOTIFICATION_ID_DAILY_SUMMARY = 1007
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

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

    // Daily Expense Summary notification (11 PM)
    fun showDailyExpenseSummary() {
        val (totalExpenses, totalIncome, expensesByCategory) = getDailyFinancialSummary()
        
        val title = "Today's Expense Summary"
        val message = if (totalExpenses > 0) {
            "Today you spent ${currencyFormat.format(totalExpenses)}"
        } else {
            "No expenses recorded today. Great job!"
        }
        
        // Create a big text style to show more details in the expanded notification
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle(title)
        
        val summaryText = buildSummaryText(totalExpenses, totalIncome, expensesByCategory)
        bigTextStyle.bigText(summaryText)
        
        val intent = Intent(context, Transactions::class.java)
        
        // Create a back stack to ensure proper navigation
        val backIntent = Intent(context, MainActivity::class.java)
        backIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        val stackBuilder = android.app.TaskStackBuilder.create(context)
        stackBuilder.addNextIntentWithParentStack(intent)
        
        val pendingIntent = stackBuilder.getPendingIntent(
            NOTIFICATION_ID_DAILY_SUMMARY,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_reminder)
            .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
            .setContentIntent(pendingIntent)
            .setStyle(bigTextStyle)
            .setAutoCancel(true)
            // Make notification more prominent for push notifications
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Set sound, vibration, and lights
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            // Show on lock screen
            .setFullScreenIntent(pendingIntent, true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_DAILY_SUMMARY, notification)
        saveNotification(title, message)
    }
    
    private fun buildSummaryText(
        totalExpenses: Double, 
        totalIncome: Double, 
        expensesByCategory: Map<String, Double>
    ): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("Today's summary:\n")
        
        // Add total expenses
        stringBuilder.append("Total spent: ${currencyFormat.format(totalExpenses)}\n")
        
        // Add total income
        if (totalIncome > 0) {
            stringBuilder.append("Total income: ${currencyFormat.format(totalIncome)}\n")
        }
        
        // Add expenses by category if there are any
        if (expensesByCategory.isNotEmpty()) {
            stringBuilder.append("\nTop expenses by category:\n")
            expensesByCategory.entries
                .sortedByDescending { it.value }
                .take(3)  // Top 3 categories
                .forEach { (category, amount) ->
                    stringBuilder.append("- $category: ${currencyFormat.format(amount)}\n")
                }
        }
        
        return stringBuilder.toString()
    }
    
    private fun getDailyFinancialSummary(): Triple<Double, Double, Map<String, Double>> {
        val sharedPreferences = context.getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)
        var totalExpenses = 0.0
        var totalIncome = 0.0
        val expensesByCategory = mutableMapOf<String, Double>()
        
        // Get today's date range
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        
        // Get all wallets
        val walletsJson = sharedPreferences.getString("wallets", null)
        if (walletsJson != null) {
            try {
                val jsonArray = JSONArray(walletsJson)
                
                // Process each wallet
                for (i in 0 until jsonArray.length()) {
                    val wallet = jsonArray.getJSONObject(i)
                    val walletName = wallet.getString("name")
                    
                    // Get transactions for this wallet
                    val transactionsKey = "transactions_$walletName"
                    val transactionsJson = sharedPreferences.getString(transactionsKey, null)
                    
                    if (transactionsJson != null) {
                        val transactionsArray = JSONArray(transactionsJson)
                        
                        // Process each transaction
                        for (j in 0 until transactionsArray.length()) {
                            val transaction = transactionsArray.getJSONObject(j)
                            val dateStr = transaction.getString("date")
                            
                            try {
                                val transactionDate = dateFormat.parse(dateStr)
                                val transactionTime = transactionDate?.time ?: 0
                                
                                // Check if transaction is from today
                                if (transactionTime in startOfDay..endOfDay) {
                                    val amount = transaction.getDouble("amount")
                                    val isIncome = transaction.getBoolean("isIncome")
                                    
                                    if (isIncome) {
                                        totalIncome += amount
                                    } else {
                                        totalExpenses += amount
                                        
                                        // Track expenses by category
                                        val category = transaction.getString("category")
                                        expensesByCategory[category] = (expensesByCategory[category] ?: 0.0) + amount
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return Triple(totalExpenses, totalIncome, expensesByCategory)
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
        // Create a back stack to ensure proper navigation
        val backIntent = Intent(context, MainActivity::class.java)
        backIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        val stackBuilder = android.app.TaskStackBuilder.create(context)
        stackBuilder.addNextIntentWithParentStack(intent)
        
        val pendingIntent = stackBuilder.getPendingIntent(
            notificationId,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(icon)
            .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Make notification more prominent for push notifications
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Set sound, vibration, and lights
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            // Show on lock screen
            .setFullScreenIntent(pendingIntent, true)
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