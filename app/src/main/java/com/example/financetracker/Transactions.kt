package com.example.financetracker

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class Transactions : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var transactionsContainer: LinearLayout
    private lateinit var currentBalanceTextView: TextView
    private lateinit var incomeValueTextView: TextView
    private lateinit var expensesValueTextView: TextView
    private lateinit var categoriesContainer: LinearLayout

    private var currentWallet: String = "Default Wallet"
    private var currentBalance: Double = 0.0
    private var totalIncome: Double = 0.0
    private var totalExpenses: Double = 0.0

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeOnlyFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val displayTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    // Map category names to drawable resource IDs
    private val categoryIcons = mapOf(
        "Essentials" to R.drawable.essentials,
        "Savings" to R.drawable.savings,
        "Pets" to R.drawable.pets,
        "Health" to R.drawable.health,
        "Donations" to R.drawable.donations,
        "Entertainment" to R.drawable.entertainment,
        "Food" to R.drawable.food
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_transactions)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)

        // Initialize views
        currentBalanceTextView = findViewById(R.id.current_balance)
        incomeValueTextView = findViewById(R.id.income_value)
        expensesValueTextView = findViewById(R.id.expenses_value)
        transactionsContainer = findViewById(R.id.transactions_container)
        categoriesContainer = findViewById(R.id.categories_container)

        // Set up change wallet button
        val changeWalletButton = findViewById<Button>(R.id.change_wallet_button)
        changeWalletButton.setOnClickListener {
            showChangeWalletDialog()
        }

        // Set up add transaction button
        val addTransactionButton = findViewById<Button>(R.id.add_transaction_button)
        addTransactionButton.setOnClickListener {
            showAddTransactionDialog()
        }

        // Set up add monthly transaction button
        val addMonthlyTransactionButton = findViewById<Button>(R.id.add_monthly_transaction_button)
        addMonthlyTransactionButton.setOnClickListener {
            showAddMonthlyTransactionDialog()
        }

        // Set up category card click listeners
        setupCategoryClickListeners()

        // Set up bottom navigation
        setupBottomNavigation()

        // Load last selected wallet or use default
        currentWallet = sharedPreferences.getString("current_wallet", "Default Wallet") ?: "Default Wallet"

        // Load transactions for the current wallet
        loadTransactions()
        
        // Schedule monthly transactions to be processed
        processMonthlyTransactions()
    }

    override fun onResume() {
        super.onResume()
        
        // Check if any transactions were processed by other activities
        val dataChanged = sharedPreferences.getBoolean("transaction_data_changed", false)
        if (dataChanged) {
            // Reload the current wallet data
            loadTransactions()
        }
    }

    private fun setupCategoryClickListeners() {
        val categoryEssentials = findViewById<CardView>(R.id.category_essentials)
        val categorySavings = findViewById<CardView>(R.id.category_savings)
        val categoryPets = findViewById<CardView>(R.id.category_pets)
        val categoryHealth = findViewById<CardView>(R.id.category_health)
        val categoryDonations = findViewById<CardView>(R.id.category_donations)
        val categoryEntertainment = findViewById<CardView>(R.id.category_entertainment)
        val categoryFood = findViewById<CardView>(R.id.category_food)

        // Set click listeners for categories to quickly add transactions
        categoryEssentials.setOnClickListener { showAddTransactionDialog("Essentials") }
        categorySavings.setOnClickListener { showAddTransactionDialog("Savings") }
        categoryPets.setOnClickListener { showAddTransactionDialog("Pets") }
        categoryHealth.setOnClickListener { showAddTransactionDialog("Health") }
        categoryDonations.setOnClickListener { showAddTransactionDialog("Donations") }
        categoryEntertainment.setOnClickListener { showAddTransactionDialog("Entertainment") }
        categoryFood.setOnClickListener { showAddTransactionDialog("Food") }
    }

    private fun showChangeWalletDialog() {
        // Get list of wallets from SharedPreferences
        val walletsJson = sharedPreferences.getString("wallets", null)
        val walletsList = mutableListOf<String>()

        if (walletsJson != null) {
            try {
                val jsonArray = JSONArray(walletsJson)
                for (i in 0 until jsonArray.length()) {
                    val wallet = jsonArray.getJSONObject(i)
                    walletsList.add(wallet.getString("name"))
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading wallets", Toast.LENGTH_SHORT).show()
            }
        }

        // If no wallets, show message and redirect to Budget page
        if (walletsList.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Wallets Found")
                .setMessage("You don't have any wallets set up yet. Would you like to create one now?")
                .setPositiveButton("Yes") { _, _ ->
                    startActivity(Intent(this, Budget::class.java))
                }
                .setNegativeButton("No", null)
                .show()
            return
        }

        // Create array adapter for spinner/list view
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, walletsList)

        // Create dialog with list of wallets
        AlertDialog.Builder(this)
            .setTitle("Select Wallet")
            .setAdapter(adapter) { _, which ->
                // Update current wallet
                currentWallet = walletsList[which]

                // Save selection to SharedPreferences
                sharedPreferences.edit()
                    .putString("current_wallet", currentWallet)
                    .apply()

                // Reload transactions for this wallet
                loadTransactions()

                Toast.makeText(this, "Switched to ${walletsList[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddTransactionDialog(preSelectedCategory: String? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_transaction, null)

        val transactionNameEditText = dialogView.findViewById<EditText>(R.id.edit_transaction_name)
        val amountEditText = dialogView.findViewById<EditText>(R.id.edit_amount)
        val isIncomeCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_is_income)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.spinner_category)
        val dateEditText = dialogView.findViewById<EditText>(R.id.edit_transaction_date)
        val timeEditText = dialogView.findViewById<EditText>(R.id.edit_transaction_time)

        // Set default date to today
        dateEditText.setText(dateOnlyFormat.format(Date()))
        // Set default time to current time
        timeEditText.setText(timeOnlyFormat.format(Date()))

        // Set up category spinner
        val categories = listOf("Essentials", "Savings", "Pets", "Health", "Donations", "Entertainment", "Food")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = categoryAdapter

        // If a category was pre-selected (from category card click), set it
        if (preSelectedCategory != null) {
            val position = categories.indexOf(preSelectedCategory)
            if (position >= 0) {
                categorySpinner.setSelection(position)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Add New Transaction")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val transactionName = transactionNameEditText.text.toString()
                val amountText = amountEditText.text.toString()
                val isIncome = isIncomeCheckBox.isChecked
                val category = categorySpinner.selectedItem.toString()
                val dateText = dateEditText.text.toString()
                val timeText = timeEditText.text.toString()

                if (transactionName.isNotEmpty() && amountText.isNotEmpty() && dateText.isNotEmpty() && timeText.isNotEmpty()) {
                    try {
                        val amount = amountText.toDouble()
                        // Validate date and time format
                        try {
                            val dateTimeText = "$dateText $timeText"
                            dateFormat.parse(dateTimeText)
                            addTransaction(transactionName, amount, isIncome, category)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Please enter a valid date (yyyy-MM-dd) and time (HH:mm)", Toast.LENGTH_SHORT).show()
                        }
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

    private fun showAddMonthlyTransactionDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_monthly_transaction, null)

        val transactionNameEditText = dialogView.findViewById<EditText>(R.id.edit_monthly_transaction_name)
        val amountEditText = dialogView.findViewById<EditText>(R.id.edit_monthly_amount)
        val isIncomeCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_monthly_is_income)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.spinner_monthly_category)
        val walletSpinner = dialogView.findViewById<Spinner>(R.id.spinner_monthly_wallet)
        val dayEditText = dialogView.findViewById<EditText>(R.id.edit_monthly_day)

        // Set up category spinner
        val categories = listOf("Essentials", "Savings", "Pets", "Health", "Donations", "Entertainment", "Food")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = categoryAdapter

        // Get list of wallets from SharedPreferences
        val walletsJson = sharedPreferences.getString("wallets", null)
        val walletsList = mutableListOf<String>()

        if (walletsJson != null) {
            try {
                val jsonArray = JSONArray(walletsJson)
                for (i in 0 until jsonArray.length()) {
                    val wallet = jsonArray.getJSONObject(i)
                    walletsList.add(wallet.getString("name"))
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading wallets", Toast.LENGTH_SHORT).show()
            }
        }

        // If no wallets, use just the current wallet
        if (walletsList.isEmpty()) {
            walletsList.add(currentWallet)
        }

        // Set up wallet spinner
        val walletAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, walletsList)
        walletSpinner.adapter = walletAdapter
        
        // Set current wallet as default
        val currentWalletIndex = walletsList.indexOf(currentWallet)
        if (currentWalletIndex >= 0) {
            walletSpinner.setSelection(currentWalletIndex)
        }

        AlertDialog.Builder(this)
            .setTitle("Add Monthly Transaction")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val transactionName = transactionNameEditText.text.toString()
                val amountText = amountEditText.text.toString()
                val isIncome = isIncomeCheckBox.isChecked
                val category = categorySpinner.selectedItem.toString()
                val walletName = walletSpinner.selectedItem.toString()
                val dayText = dayEditText.text.toString()

                if (transactionName.isNotEmpty() && amountText.isNotEmpty() && dayText.isNotEmpty()) {
                    try {
                        val amount = amountText.toDouble()
                        val day = dayText.toInt()
                        
                        // Validate day of month
                        if (day in 1..31) {
                            addNewMonthlyTransaction(transactionName, amount, isIncome, category, walletName, day)
                        } else {
                            Toast.makeText(this, "Please enter a valid day (1-31)", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: NumberFormatException) {
                        Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Manage Monthly Transactions") { _, _ ->
                // Navigate to MonthlyTransactionsActivity
                startActivity(Intent(this, MonthlyTransactionsActivity::class.java))
            }
            .show()
    }

    /**
     * Add a new transaction to the SharedPreferences
     */
    private fun addTransaction(name: String, amount: Double, isIncome: Boolean, category: String) {
        // Get today's date
        val currentDateTime = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(currentDateTime)
        val timestamp = System.currentTimeMillis()

        // Create JSON object for the transaction
        val transaction = JSONObject().apply {
            put("name", name)
            put("amount", amount)
            put("isIncome", isIncome)
            put("category", category)
            put("date", formattedDate)
            put("timestamp", timestamp) // Add timestamp for sorting and relative time display
        }

        // Get existing transactions for this wallet
        val transactionsKey = "transactions_$currentWallet"
        val transactionsJson = sharedPreferences.getString(transactionsKey, null)
        val transactionsArray = if (transactionsJson != null) {
            JSONArray(transactionsJson)
        } else {
            JSONArray()
        }

        // Add new transaction
        transactionsArray.put(transaction)

        // Save updated transactions
        sharedPreferences.edit()
            .putString(transactionsKey, transactionsArray.toString())
            .apply()

        // Update wallet balance
        updateWalletBalance(amount, isIncome)

        // Check budget status after the transaction
        BudgetMonitor(this).checkAllWallets()

        // Reload transactions to show the new one
        loadTransactions()

        // Set a flag in SharedPreferences to notify Homepage to refresh the graph
        // Ensure we have a unique timestamp so changes are always detected
        val updateTimestamp = System.currentTimeMillis()
        sharedPreferences.edit()
            .putBoolean("transaction_data_changed", true)
            .putLong("last_transaction_update", updateTimestamp)
            .apply()

        // Send a broadcast to inform all components about the transaction update
        try {
            val intent = Intent("com.example.financetracker.TRANSACTION_UPDATED")
            // Add data to intent to prevent empty broadcast issues
            intent.putExtra("timestamp", updateTimestamp)
            intent.putExtra("action", "add_transaction")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            
            // Also send a global broadcast as fallback
            applicationContext.sendBroadcast(intent)
        } catch (e: Exception) {
            // Log the error but continue - the SharedPreferences flag is our backup
            e.printStackTrace()
        }

        // Force a stronger sync approach - commit instead of apply for flags
        sharedPreferences.edit()
            .putBoolean("transaction_data_changed", true)
            .putLong("last_transaction_update", updateTimestamp)
            .commit() // Use commit for immediate effect

        // Don't delay the navigation back - it may be causing issues
        navigateToHomepageWithRefresh()

        Toast.makeText(this, "Transaction added successfully", Toast.LENGTH_SHORT).show()
    }

    private fun updateWalletBalance(amount: Double, isIncome: Boolean) {
        // Get wallets from SharedPreferences
        val walletsJson = sharedPreferences.getString("wallets", null) ?: return

        try {
            val jsonArray = JSONArray(walletsJson)
            var walletIndex = -1

            // Find the current wallet
            for (i in 0 until jsonArray.length()) {
                val wallet = jsonArray.getJSONObject(i)
                if (wallet.getString("name") == currentWallet) {
                    walletIndex = i
                    break
                }
            }

            if (walletIndex != -1) {
                // Update wallet data
                val wallet = jsonArray.getJSONObject(walletIndex)
                val initialAmount = wallet.getDouble("initialAmount")
                var expenses = wallet.getDouble("expenses")
                var remaining = wallet.getDouble("remaining")

                // Update values based on transaction type
                if (isIncome) {
                    remaining += amount
                } else {
                    expenses += amount
                    remaining -= amount
                }

                // Update JSON object
                wallet.put("expenses", expenses)
                wallet.put("remaining", remaining)
                jsonArray.put(walletIndex, wallet)

                // Save to SharedPreferences
                sharedPreferences.edit()
                    .putString("wallets", jsonArray.toString())
                    .apply()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error updating wallet balance", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Navigate back to Homepage with a flag to immediately refresh the graph
     */
    private fun navigateToHomepageWithRefresh() {
        // Check if the user is navigating to transactions from the homepage
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        
        // Set a flag in SharedPreferences to trigger an immediate refresh
        // Use a unique timestamp to ensure the update is detected
        val currentTime = System.currentTimeMillis()
        sharedPreferences.edit()
            .putBoolean("transaction_data_changed", true)
            .putLong("last_transaction_update", currentTime)
            .putBoolean("immediate_refresh_needed", true)  // Additional flag for immediate refresh
            .commit()  // Use commit for immediate disk write
        
        try {
            // Send a strong broadcast to force an update
            val intent = Intent("com.example.financetracker.TRANSACTION_UPDATED")
            intent.putExtra("timestamp", currentTime)
            intent.putExtra("action", "navigate_back")
            intent.putExtra("force_refresh", true)
            // Send both local and global broadcasts
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            applicationContext.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // If the selected item is the transactions tab, navigate back to homepage
        if (bottomNavigation.selectedItemId == R.id.nav_add_transaction) {
            // Create an explicit intent to Homepage with flags to clear task
            val intent = Intent(this, Homepage::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            intent.putExtra("refresh_needed", true)
            startActivity(intent)
            finish()
        }
    }

    private fun loadTransactions() {
        // Clear existing transactions
        transactionsContainer.removeAllViews()

        // Reset totals
        totalIncome = 0.0
        totalExpenses = 0.0
        currentBalance = 0.0

        // Get wallet details for the current wallet
        val walletsJson = sharedPreferences.getString("wallets", null)
        if (walletsJson != null) {
            try {
                val jsonArray = JSONArray(walletsJson)

                // Find the current wallet
                for (i in 0 until jsonArray.length()) {
                    val wallet = jsonArray.getJSONObject(i)
                    if (wallet.getString("name") == currentWallet) {
                        // Update balance display
                        currentBalance = wallet.getDouble("remaining")
                        totalExpenses = wallet.getDouble("expenses")
                        totalIncome = wallet.getDouble("initialAmount") // This is simplified; in a real app, you'd track income separately

                        break
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading wallet data", Toast.LENGTH_SHORT).show()
            }
        }

        // Update UI with current wallet data
        currentBalanceTextView.text = currencyFormat.format(currentBalance)
        incomeValueTextView.text = currencyFormat.format(totalIncome)
        expensesValueTextView.text = currencyFormat.format(totalExpenses)

        // Get transactions for this wallet
        val transactionsKey = "transactions_$currentWallet"
        val transactionsJson = sharedPreferences.getString(transactionsKey, null)

        if (transactionsJson != null) {
            try {
                val transactionsArray = JSONArray(transactionsJson)

                // If there are no transactions, show a placeholder
                if (transactionsArray.length() == 0) {
                    val noTransactionsView = TextView(this).apply {
                        text = "No transactions yet. Add your first transaction!"
                        textSize = 16f
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, 16, 0, 16)
                    }
                    transactionsContainer.addView(noTransactionsView)
                } else {
                    // Loop through transactions and add them to the UI (newest first)
                    for (i in transactionsArray.length() - 1 downTo 0) {
                        val transaction = transactionsArray.getJSONObject(i)
                        addTransactionToUI(transaction)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading transactions", Toast.LENGTH_SHORT).show()
            }
        } else {
            // No transactions yet
            val noTransactionsView = TextView(this).apply {
                text = "No transactions yet. Add your first transaction!"
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 16, 0, 16)
            }
            transactionsContainer.addView(noTransactionsView)
        }
    }

    private fun addTransactionToUI(transaction: JSONObject) {
        // Inflate transaction item layout
        val transactionView = LayoutInflater.from(this)
            .inflate(R.layout.transaction_item, transactionsContainer, false)

        // Get data from transaction
        val name = transaction.getString("name")
        val amount = transaction.getDouble("amount")
        val isIncome = transaction.getBoolean("isIncome")
        val category = transaction.getString("category")
        val date = transaction.getString("date")

        // Set data to views
        val nameTextView = transactionView.findViewById<TextView>(R.id.transaction_name)
        val categoryTextView = transactionView.findViewById<TextView>(R.id.transaction_category)
        val amountTextView = transactionView.findViewById<TextView>(R.id.transaction_amount)
        val categoryIcon = transactionView.findViewById<ImageView>(R.id.transaction_category_icon)
        val dateTextView = transactionView.findViewById<TextView>(R.id.transaction_date)
        val deleteButton = transactionView.findViewById<ImageButton>(R.id.btn_delete_transaction)
        val editButton = transactionView.findViewById<ImageButton>(R.id.btn_edit_transaction)

        nameTextView.text = name
        categoryTextView.text = category

        // Format and display date and time
        try {
            val parsedDateTime = dateFormat.parse(date)
            val formattedDate = displayDateFormat.format(parsedDateTime!!)
            val formattedTime = displayTimeFormat.format(parsedDateTime)
            dateTextView.text = "$formattedDate • $formattedTime"
        } catch (e: Exception) {
            // Handle legacy format (without time)
            try {
                val parsedDate = dateOnlyFormat.parse(date)
                dateTextView.text = displayDateFormat.format(parsedDate!!)
            } catch (e2: Exception) {
                dateTextView.text = date
            }
        }

        // Format amount and set color based on transaction type
        val formattedAmount = if (isIncome) {
            amountTextView.setTextColor(ContextCompat.getColor(this, R.color.green))
            "+ ${currencyFormat.format(amount)}"
        } else {
            amountTextView.setTextColor(ContextCompat.getColor(this, R.color.red))
            "- ${currencyFormat.format(amount)}"
        }
        amountTextView.text = formattedAmount

        // Set category icon
        val iconResId = categoryIcons[category] ?: R.drawable.donations // fallback icon
        categoryIcon.setImageResource(iconResId)

        // Set up delete button click listener
        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog(transaction)
        }
        
        // Set up edit button click listener
        editButton.setOnClickListener {
            showEditTransactionDialog(transaction)
        }

        // Add transaction view to container
        transactionsContainer.addView(transactionView)
    }

    private fun showDeleteConfirmationDialog(transaction: JSONObject) {
        AlertDialog.Builder(this)
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?")
            .setPositiveButton("Delete") { _, _ ->
                deleteTransaction(transaction)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTransaction(transaction: JSONObject) {
        // Get current transactions for this wallet
        val transactionsKey = "transactions_$currentWallet"
        val transactionsJson = sharedPreferences.getString(transactionsKey, null) ?: return

        try {
            val transactionsArray = JSONArray(transactionsJson)
            val updatedTransactionsArray = JSONArray()
            var isRemoved = false

            // Find the transaction to remove and create a new array without it
            for (i in 0 until transactionsArray.length()) {
                val currentTransaction = transactionsArray.getJSONObject(i)
                
                // Check if this is the transaction we want to delete by comparing properties
                // We're using multiple properties for more reliable identification
                if (!isRemoved && 
                    currentTransaction.getString("name") == transaction.getString("name") &&
                    currentTransaction.getDouble("amount") == transaction.getDouble("amount") &&
                    currentTransaction.getString("date") == transaction.getString("date") &&
                    currentTransaction.getString("category") == transaction.getString("category")) {
                    // This is the transaction to remove, don't add it to the new array
                    isRemoved = true
                } else {
                    // Not the transaction to remove, add it to the new array
                    updatedTransactionsArray.put(currentTransaction)
                }
            }

            // Only update if we actually removed a transaction
            if (isRemoved) {
                // Save updated transactions list
                sharedPreferences.edit()
                    .putString(transactionsKey, updatedTransactionsArray.toString())
                    .apply()

                // Restore the wallet balance
                restoreWalletBalance(transaction.getDouble("amount"), transaction.getBoolean("isIncome"))

                // Check budget status after removing the transaction
                BudgetMonitor(this).checkAllWallets()

                // Set a flag in SharedPreferences to notify Homepage to refresh the graph
                sharedPreferences.edit()
                    .putBoolean("transaction_data_changed", true)
                    .putLong("last_transaction_update", System.currentTimeMillis())
                    .putBoolean("immediate_refresh_needed", true)
                    .apply()

                // Send a broadcast to inform all components about the transaction update
                val intent = Intent("com.example.financetracker.TRANSACTION_UPDATED")
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

                // Reload transactions
                loadTransactions()
                
                // Offer to navigate back to homepage with refresh
                navigateToHomepageWithRefresh()

                Toast.makeText(this, "Transaction deleted successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error deleting transaction", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreWalletBalance(amount: Double, wasIncome: Boolean) {
        // Get wallets from SharedPreferences
        val walletsJson = sharedPreferences.getString("wallets", null) ?: return

        try {
            val jsonArray = JSONArray(walletsJson)
            var walletIndex = -1

            // Find the current wallet
            for (i in 0 until jsonArray.length()) {
                val wallet = jsonArray.getJSONObject(i)
                if (wallet.getString("name") == currentWallet) {
                    walletIndex = i
                    break
                }
            }

            if (walletIndex != -1) {
                // Update wallet data
                val wallet = jsonArray.getJSONObject(walletIndex)
                var expenses = wallet.getDouble("expenses")
                var remaining = wallet.getDouble("remaining")

                // Reverse the transaction effect on wallet balance
                if (wasIncome) {
                    // If it was income, remove it from remaining balance
                    remaining -= amount
                } else {
                    // If it was expense, reduce expenses and add back to remaining
                    expenses -= amount
                    remaining += amount
                }

                // Update JSON object
                wallet.put("expenses", expenses)
                wallet.put("remaining", remaining)
                jsonArray.put(walletIndex, wallet)

                // Save to SharedPreferences
                sharedPreferences.edit()
                    .putString("wallets", jsonArray.toString())
                    .apply()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error updating wallet balance", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditTransactionDialog(transaction: JSONObject) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_transaction, null)

        val transactionNameEditText = dialogView.findViewById<EditText>(R.id.edit_transaction_name)
        val amountEditText = dialogView.findViewById<EditText>(R.id.edit_amount)
        val isIncomeCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_is_income)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.spinner_category)
        val dateEditText = dialogView.findViewById<EditText>(R.id.edit_transaction_date)
        val timeEditText = dialogView.findViewById<EditText>(R.id.edit_transaction_time)

        // Fill with current transaction data
        transactionNameEditText.setText(transaction.getString("name"))
        amountEditText.setText(transaction.getDouble("amount").toString())
        isIncomeCheckBox.isChecked = transaction.getBoolean("isIncome")
        
        // Parse date and time
        val dateTimeString = transaction.getString("date")
        try {
            val dateTime = dateFormat.parse(dateTimeString)
            if (dateTime != null) {
                dateEditText.setText(dateOnlyFormat.format(dateTime))
                timeEditText.setText(timeOnlyFormat.format(dateTime))
            } else {
                // Fallback to showing as is
                dateEditText.setText(dateTimeString.split(" ")[0])
                timeEditText.setText(if (dateTimeString.contains(" ")) dateTimeString.split(" ")[1] else "00:00")
            }
        } catch (e: Exception) {
            // Handle older format or errors
            dateEditText.setText(dateTimeString)
            timeEditText.setText("00:00")
        }

        // Set up category spinner
        val categories = listOf("Essentials", "Savings", "Pets", "Health", "Donations", "Entertainment", "Food")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = categoryAdapter
        
        // Set selected category
        val categoryPosition = categories.indexOf(transaction.getString("category"))
        if (categoryPosition >= 0) {
            categorySpinner.setSelection(categoryPosition)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Transaction")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = transactionNameEditText.text.toString()
                val newAmountText = amountEditText.text.toString()
                val newIsIncome = isIncomeCheckBox.isChecked
                val newCategory = categorySpinner.selectedItem.toString()
                val newDateText = dateEditText.text.toString()
                val newTimeText = timeEditText.text.toString()

                if (newName.isNotEmpty() && newAmountText.isNotEmpty() && newDateText.isNotEmpty() && newTimeText.isNotEmpty()) {
                    try {
                        val newAmount = newAmountText.toDouble()
                        // Validate date and time format
                        try {
                            val newDateTime = "$newDateText $newTimeText"
                            dateFormat.parse(newDateTime)
                            updateTransaction(transaction, newName, newAmount, newIsIncome, newCategory, newDateTime)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Please enter a valid date (yyyy-MM-dd) and time (HH:mm)", Toast.LENGTH_SHORT).show()
                        }
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

    private fun updateTransaction(oldTransaction: JSONObject, newName: String, newAmount: Double, 
                                 newIsIncome: Boolean, newCategory: String, newDate: String) {
        // First, restore the old transaction's effect on the wallet balance
        restoreWalletBalance(oldTransaction.getDouble("amount"), oldTransaction.getBoolean("isIncome"))
        
        // Get current transactions for this wallet
        val transactionsKey = "transactions_$currentWallet"
        val transactionsJson = sharedPreferences.getString(transactionsKey, null) ?: return
        
        try {
            val transactionsArray = JSONArray(transactionsJson)
            var found = false
            
            // Find the transaction to update
            for (i in 0 until transactionsArray.length()) {
                val transaction = transactionsArray.getJSONObject(i)
                
                // Check if this is the transaction we want to update
                if (!found && 
                    transaction.getString("name") == oldTransaction.getString("name") &&
                    transaction.getDouble("amount") == oldTransaction.getDouble("amount") &&
                    transaction.getString("date") == oldTransaction.getString("date") &&
                    transaction.getString("category") == oldTransaction.getString("category")) {
                    
                    // Create updated transaction object
                    val updatedTransaction = JSONObject().apply {
                        put("name", newName)
                        put("amount", newAmount)
                        put("isIncome", newIsIncome)
                        put("category", newCategory)
                        put("date", newDate)
                        // Keep the original timestamp
                        put("timestamp", transaction.optLong("timestamp", System.currentTimeMillis()))
                    }
                    
                    // Replace old transaction with updated one
                    transactionsArray.put(i, updatedTransaction)
                    found = true
                }
            }
            
            // Save updated transactions list
            if (found) {
                sharedPreferences.edit()
                    .putString(transactionsKey, transactionsArray.toString())
                    .apply()
                
                // Update wallet balance with the new transaction
                updateWalletBalance(newAmount, newIsIncome)
                
                // Check budget status after updating the transaction
                BudgetMonitor(this).checkAllWallets()
                
                // Set a flag in SharedPreferences to notify Homepage to refresh the graph
                sharedPreferences.edit()
                    .putBoolean("transaction_data_changed", true)
                    .putLong("last_transaction_update", System.currentTimeMillis())
                    .putBoolean("immediate_refresh_needed", true)
                    .apply()
                
                // Send a broadcast to inform all components about the transaction update
                val intent = Intent("com.example.financetracker.TRANSACTION_UPDATED")
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                
                // Reload transactions
                loadTransactions()
                
                // Offer to navigate back to homepage with refresh
                navigateToHomepageWithRefresh()
                
                Toast.makeText(this, "Transaction updated successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Error updating transaction", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error updating transaction: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Set the add transaction item as selected by default
        bottomNavigation.selectedItemId = R.id.nav_add_transaction

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, Homepage::class.java))
                    true
                }
                R.id.nav_add_transaction -> {
                    // If already on Transactions page, no need to navigate, just scroll to top if possible
                    if (this::class.java != Transactions::class.java) {
                        startActivity(Intent(this, Transactions::class.java))
                        finish()
                    } else {
                        // Scroll to top logic here if needed
                        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.transactions_scroll_view)
                        scrollView?.smoothScrollTo(0, 0)
                    }
                    true
                }
                R.id.nav_transaction_history -> {
                    startActivity(Intent(this, Analytics::class.java))
                    true
                }
                R.id.nav_budget_setup -> {
                    startActivity(Intent(this, Budget::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun addNewMonthlyTransaction(name: String, amount: Double, isIncome: Boolean, 
                                        category: String, walletName: String, dayOfMonth: Int) {
        // Create monthly transaction object
        val monthlyTransaction = JSONObject().apply {
            put("name", name)
            put("amount", amount)
            put("isIncome", isIncome)
            put("category", category)
            put("wallet", walletName)
            put("dayOfMonth", dayOfMonth)
            put("isActive", true)
            put("createdAt", System.currentTimeMillis())
        }

        // Get current monthly transactions
        val monthlyTransactionsJson = sharedPreferences.getString("monthly_transactions", null)
        val monthlyTransactionsArray = if (monthlyTransactionsJson != null) {
            JSONArray(monthlyTransactionsJson)
        } else {
            JSONArray()
        }

        // Add new monthly transaction
        monthlyTransactionsArray.put(monthlyTransaction)

        // Save updated monthly transactions
        sharedPreferences.edit()
            .putString("monthly_transactions", monthlyTransactionsArray.toString())
            .apply()

        Toast.makeText(this, "Monthly transaction added successfully", Toast.LENGTH_SHORT).show()
    }

    private fun processMonthlyTransactions() {
        // Get current monthly transactions
        val monthlyTransactionsJson = sharedPreferences.getString("monthly_transactions", null) ?: return
        
        try {
            val monthlyTransactionsArray = JSONArray(monthlyTransactionsJson)
            val calendar = Calendar.getInstance()
            val today = calendar.get(Calendar.DAY_OF_MONTH)
            
            // Get the last processed day from SharedPreferences
            val lastProcessedDay = sharedPreferences.getInt("last_processed_day", -1)
            
            // Only process if we haven't already processed today
            if (lastProcessedDay != today) {
                var transactionsProcessed = false
                
                // Loop through monthly transactions
                for (i in 0 until monthlyTransactionsArray.length()) {
                    val monthlyTransaction = monthlyTransactionsArray.getJSONObject(i)
                    
                    // Check if transaction is active
                    if (monthlyTransaction.getBoolean("isActive")) {
                        val dayOfMonth = monthlyTransaction.getInt("dayOfMonth")
                        
                        // If today is the day to process this transaction
                        if (today == dayOfMonth) {
                            // Get transaction details
                            val name = monthlyTransaction.getString("name")
                            val amount = monthlyTransaction.getDouble("amount")
                            val isIncome = monthlyTransaction.getBoolean("isIncome")
                            val category = monthlyTransaction.getString("category")
                            val walletName = monthlyTransaction.getString("wallet")
                            
                            // Create a transaction for this month
                            val transaction = JSONObject().apply {
                                put("name", name + " (Monthly)")
                                put("amount", amount)
                                put("isIncome", isIncome)
                                put("category", category)
                                put("date", dateFormat.format(Date()))
                                put("timestamp", System.currentTimeMillis())
                            }
                            
                            // Get current transactions for the wallet
                            val transactionsKey = "transactions_$walletName"
                            val transactionsJson = sharedPreferences.getString(transactionsKey, null)
                            val transactionsArray = if (transactionsJson != null) {
                                JSONArray(transactionsJson)
                            } else {
                                JSONArray()
                            }
                            
                            // Add new transaction
                            transactionsArray.put(transaction)
                            
                            // Save updated transactions
                            sharedPreferences.edit()
                                .putString(transactionsKey, transactionsArray.toString())
                                .apply()
                            
                            // Update wallet balance
                            updateWalletBalanceByName(walletName, amount, isIncome)
                            
                            transactionsProcessed = true
                        }
                    }
                }
                
                // Update last processed day
                sharedPreferences.edit()
                    .putInt("last_processed_day", today)
                    .apply()
                
                // Set update flag if transactions were processed
                if (transactionsProcessed) {
                    sharedPreferences.edit()
                        .putBoolean("transaction_data_changed", true)
                        .putLong("last_transaction_update", System.currentTimeMillis())
                        .apply()
                }
                
                // Check budget status after processing monthly transactions
                BudgetMonitor(this).checkAllWallets()
                
                // Reload transactions if we processed any for the current wallet
                loadTransactions()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error processing monthly transactions", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateWalletBalanceByName(walletName: String, amount: Double, isIncome: Boolean) {
        // Get wallets from SharedPreferences
        val walletsJson = sharedPreferences.getString("wallets", null) ?: return

        try {
            val jsonArray = JSONArray(walletsJson)
            var walletIndex = -1

            // Find the wallet by name
            for (i in 0 until jsonArray.length()) {
                val wallet = jsonArray.getJSONObject(i)
                if (wallet.getString("name") == walletName) {
                    walletIndex = i
                    break
                }
            }

            if (walletIndex != -1) {
                // Update wallet data
                val wallet = jsonArray.getJSONObject(walletIndex)
                val initialAmount = wallet.getDouble("initialAmount")
                var expenses = wallet.getDouble("expenses")
                var remaining = wallet.getDouble("remaining")

                // Update values based on transaction type
                if (isIncome) {
                    remaining += amount
                } else {
                    expenses += amount
                    remaining -= amount
                }

                // Update JSON object
                wallet.put("expenses", expenses)
                wallet.put("remaining", remaining)
                jsonArray.put(walletIndex, wallet)

                // Save to SharedPreferences
                sharedPreferences.edit()
                    .putString("wallets", jsonArray.toString())
                    .apply()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error updating wallet balance", Toast.LENGTH_SHORT).show()
        }
    }
}