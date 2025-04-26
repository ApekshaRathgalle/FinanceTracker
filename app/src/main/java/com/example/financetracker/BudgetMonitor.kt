package com.example.financetracker

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class BudgetMonitor(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)
    private val notificationHelper = NotificationHelper(context)

    // Check all wallets for budget warnings
    fun checkAllWallets() {
        val walletsJson = sharedPreferences.getString("wallets", null) ?: return

        try {
            val walletsArray = JSONArray(walletsJson)

            for (i in 0 until walletsArray.length()) {
                val wallet = walletsArray.getJSONObject(i)
                checkWalletStatus(wallet)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Check individual wallet status and show notifications if needed
    private fun checkWalletStatus(wallet: JSONObject) {
        try {
            val walletName = wallet.getString("name")
            val initialAmount = wallet.getDouble("initialAmount")
            val remaining = wallet.getDouble("remaining")

            if (initialAmount <= 0) return // Skip invalid wallets

            // Calculate percentage of budget used (expenses / initial * 100)
            val expenses = wallet.getDouble("expenses")
            val budgetUsedPercentage = (expenses / initialAmount * 100).toInt()

            // Calculate percentage of budget remaining (remaining / initial * 100)
            val remainingPercentage = (remaining / initialAmount * 100).toInt()

            // Budget warning at 80% used
            if (budgetUsedPercentage >= 80 && budgetUsedPercentage < 100) {
                notificationHelper.showBudgetWarningNotification(walletName, budgetUsedPercentage)
            }

            // Budget exceeded warning (>100% used)
            if (budgetUsedPercentage >= 100) {
                notificationHelper.showBudgetExceededNotification(walletName, budgetUsedPercentage)
            }

            // Low balance warning (<10% remaining)
            if (remainingPercentage < 10) {
                notificationHelper.showLowBalanceWarning(walletName, remainingPercentage)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}