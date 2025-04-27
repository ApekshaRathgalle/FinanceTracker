package com.example.financetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * BroadcastReceiver for handling all scheduled notifications in the Finance Tracker app.
 * This class is registered in the AndroidManifest.xml to ensure it works even when the app
 * is not running in the background.
 */
class NotificationReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Notification broadcast received: ${intent.action}")
        
        // Acquire a wake lock to ensure the notification is processed even if the device is asleep
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FinanceTracker:NotificationWakeLock"
        )
        
        try {
            wakeLock.acquire(WAKE_LOCK_TIMEOUT)
            
            val notificationHelper = NotificationHelper(context)
            
            when (intent.action) {
                NotificationScheduler.ACTION_DAILY_REMINDER -> {
                    Log.d(TAG, "Processing daily reminder notification")
                    notificationHelper.showDailyExpenseReminder()
                    
                    // Reschedule for tomorrow
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        NotificationScheduler().scheduleDailyExpenseReminder(context)
                    }
                }
                NotificationScheduler.ACTION_DAILY_SUMMARY -> {
                    Log.d(TAG, "Processing daily expense summary notification")
                    notificationHelper.showDailyExpenseSummary()
                    
                    // Reschedule for tomorrow
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        NotificationScheduler().scheduleDailyExpenseSummary(context)
                    }
                }
                NotificationScheduler.ACTION_BACKUP_REMINDER -> {
                    Log.d(TAG, "Processing backup reminder notification")
                    notificationHelper.showBackupReminder()
                    
                    // Reschedule for next month
                    NotificationScheduler().scheduleMonthlyBackupReminder(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification: ${e.message}", e)
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
    
    companion object {
        private const val TAG = "NotificationReceiver"
        private const val WAKE_LOCK_TIMEOUT = 10000L // 10 seconds
    }
} 