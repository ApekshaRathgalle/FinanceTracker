package com.example.financetracker

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.ProgressBar

class Budget : AppCompatActivity() {

    private lateinit var walletsContainer: LinearLayout
    private val wallets = mutableListOf<WalletData>()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private lateinit var sharedPreferences: SharedPreferences

    // For authentication - in a real app, these would come from a secure source
    // This is just for demonstration
    private val USERNAME = "admin"
    private val PASSWORD = "admin123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_budget)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)

        // Initialize views
        walletsContainer = findViewById(R.id.wallets_container)
        val addWalletButton = findViewById<FloatingActionButton>(R.id.add_wallet_button)

        // Set up add wallet button click listener
        addWalletButton.setOnClickListener {
            showAddWalletDialog()
        }

        setupBottomNavigation()

        // Load saved wallets
        loadWalletsFromPreferences()
    }

    private fun loadWalletsFromPreferences() {
        // Clear the current UI and data
        wallets.clear()
        walletsContainer.removeAllViews()

        // Get wallets JSON string from SharedPreferences
        val walletsJson = sharedPreferences.getString("wallets", null)

        if (walletsJson != null) {
            try {
                // Parse JSON array
                val jsonArray = JSONArray(walletsJson)

                // Loop through wallets and add them to the list and UI
                for (i in 0 until jsonArray.length()) {
                    val walletObject = jsonArray.getJSONObject(i)

                    val wallet = WalletData(
                        name = walletObject.getString("name"),
                        initialAmount = walletObject.getDouble("initialAmount"),
                        expenses = walletObject.getDouble("expenses"),
                        remaining = walletObject.getDouble("remaining")
                    )

                    wallets.add(wallet)
                    addWalletToUI(wallet, wallets.size - 1) // Pass the index for deletion
                }
            } catch (e: Exception) {
                // Handle JSON parsing error
                Toast.makeText(this, "Error loading wallets", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveWalletsToPreferences() {
        try {
            // Convert wallets list to JSON
            val jsonArray = JSONArray()

            for (wallet in wallets) {
                val walletObject = JSONObject().apply {
                    put("name", wallet.name)
                    put("initialAmount", wallet.initialAmount)
                    put("expenses", wallet.expenses)
                    put("remaining", wallet.remaining)
                }

                jsonArray.put(walletObject)
            }

            // Save to SharedPreferences
            sharedPreferences.edit()
                .putString("wallets", jsonArray.toString())
                .apply()

        } catch (e: Exception) {
            // Handle JSON creation error
            Toast.makeText(this, "Error saving wallets", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddWalletDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_wallet, null)
        val walletNameEditText = dialogView.findViewById<EditText>(R.id.edit_wallet_name)
        val initialAmountEditText = dialogView.findViewById<EditText>(R.id.edit_initial_amount)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val walletName = walletNameEditText.text.toString()
                val initialAmountText = initialAmountEditText.text.toString()

                if (walletName.isNotEmpty() && initialAmountText.isNotEmpty()) {
                    try {
                        val initialAmount = initialAmountText.toDouble()
                        addNewWallet(walletName, initialAmount)
                    } catch (e: NumberFormatException) {
                        Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // In Budget.kt, update the addNewWallet method
    private fun addNewWallet(walletName: String, initialAmount: Double) {
        // Create a new wallet data object
        val newWallet = WalletData(
            name = walletName,
            initialAmount = initialAmount,
            expenses = 0.0,  // Initially no expenses
            remaining = initialAmount  // Initially remaining = initial
        )

        // Add to the list
        wallets.add(newWallet)

        // Add to the UI with its index
        addWalletToUI(newWallet, wallets.size - 1)

        // Save to SharedPreferences
        saveWalletsToPreferences()

        // Show notification
        NotificationHelper(this).showWalletAddedNotification(walletName)

        Toast.makeText(this, "Wallet added successfully", Toast.LENGTH_SHORT).show()
    }

    private fun addWalletToUI(wallet: WalletData, index: Int) {
        // Create a new CardView programmatically
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 48) // 16dp bottom margin
            }
            radius = 12f * resources.displayMetrics.density // 12dp corner radius
            cardElevation = 4f * resources.displayMetrics.density // 4dp elevation
            setCardBackgroundColor(resources.getColor(R.color.wallet_card_background, null))
        }

        // Inflate the wallet card layout
        val cardContent = LayoutInflater.from(this)
            .inflate(R.layout.wallet_card, null, false)

        // Set wallet data to the views
        cardContent.findViewById<TextView>(R.id.wallet_name).text = wallet.name
        cardContent.findViewById<TextView>(R.id.wallet_remaining).text = currencyFormat.format(wallet.remaining)
        cardContent.findViewById<TextView>(R.id.wallet_initial).text = currencyFormat.format(wallet.initialAmount)
        cardContent.findViewById<TextView>(R.id.wallet_expenses).text = currencyFormat.format(wallet.expenses)

        // Calculate progress percentage
        val progress = if (wallet.initialAmount > 0) {
            ((wallet.remaining / wallet.initialAmount) * 100).toInt()
        } else {
            0
        }

        // Set progress bar
        cardContent.findViewById<ProgressBar>(R.id.wallet_progress).progress = progress

        // Set up delete button
        val deleteButton = cardContent.findViewById<ImageButton>(R.id.delete_wallet_button)
        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog(index)
        }

        // Add the inflated layout to the card
        cardView.addView(cardContent)

        // Add the card to the wallets container
        walletsContainer.addView(cardView)
    }

    private fun showDeleteConfirmationDialog(walletIndex: Int) {
        // Create dialog for authentication
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_auth_delete, null)
        val usernameEditText = dialogView.findViewById<EditText>(R.id.edit_username)
        val passwordEditText = dialogView.findViewById<EditText>(R.id.edit_password)

        AlertDialog.Builder(this)
            .setTitle("Authentication Required")
            .setMessage("Please enter your username and password to delete this wallet")
            .setView(dialogView)
            .setPositiveButton("Delete") { _, _ ->
                val username = usernameEditText.text.toString()
                val password = passwordEditText.text.toString()

                // Check credentials
                if (username == USERNAME && password == PASSWORD) {
                    // Credentials match, proceed with deletion
                    deleteWallet(walletIndex)
                } else {
                    // Authentication failed
                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteWallet(index: Int) {
        if (index >= 0 && index < wallets.size) {
            // Remove from the list
            wallets.removeAt(index)

            // Refresh the UI
            walletsContainer.removeAllViews()
            for (i in wallets.indices) {
                addWalletToUI(wallets[i], i)
            }

            // Save changes to SharedPreferences
            saveWalletsToPreferences()

            Toast.makeText(this, "Wallet deleted successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Set the budget item as selected by default
        bottomNavigation.selectedItemId = R.id.nav_budget_setup

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, Homepage::class.java))
                    true
                }
                R.id.nav_add_transaction -> {
                    startActivity(Intent(this, Transactions::class.java))
                    true
                }
                R.id.nav_transaction_history -> {
                    startActivity(Intent(this, Analytics::class.java))
                    true
                }
                R.id.nav_budget_setup -> {
                    // If already on Budget page, no need to navigate, just scroll to top if possible
                    if (this::class.java != Budget::class.java) {
                        startActivity(Intent(this, Budget::class.java))
                        finish()
                    } else {
                        // Scroll to top logic here if needed
                        val scrollView = findViewById<NestedScrollView>(R.id.budget_scroll_view)
                        scrollView?.smoothScrollTo(0, 0)
                    }
                    true
                }
                else -> false
            }
        }
    }

    // Data class to hold wallet information
    data class WalletData(
        val name: String,
        val initialAmount: Double,
        var expenses: Double,
        var remaining: Double
    )
}