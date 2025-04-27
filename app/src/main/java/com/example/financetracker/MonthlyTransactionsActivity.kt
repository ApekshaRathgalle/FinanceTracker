package com.example.financetracker

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.*

class MonthlyTransactionsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var monthlyTransactionsContainer: LinearLayout
    private lateinit var emptyMonthlyTransactionsText: TextView

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
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
        setContentView(R.layout.activity_monthly_transactions)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)

        // Initialize views
        monthlyTransactionsContainer = findViewById(R.id.monthly_transactions_container)
        emptyMonthlyTransactionsText = findViewById(R.id.empty_monthly_transactions_text)

        // Set up add monthly transaction button
        val addMonthlyTransactionButton = findViewById<Button>(R.id.add_monthly_transaction_button)
        addMonthlyTransactionButton.setOnClickListener {
            showAddMonthlyTransactionDialog()
        }

        // Set up bottom navigation
        setupBottomNavigation()

        // Load monthly transactions
        loadMonthlyTransactions()
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Set the transactions item as selected by default
        bottomNavigation.selectedItemId = R.id.nav_add_transaction

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
                    startActivity(Intent(this, Budget::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun loadMonthlyTransactions() {
        // Clear existing transactions
        monthlyTransactionsContainer.removeAllViews()

        // Get monthly transactions from SharedPreferences
        val monthlyTransactionsJson = sharedPreferences.getString("monthly_transactions", null)

        if (monthlyTransactionsJson != null) {
            try {
                val monthlyTransactionsArray = JSONArray(monthlyTransactionsJson)

                // If there are no monthly transactions, show the empty text
                if (monthlyTransactionsArray.length() == 0) {
                    emptyMonthlyTransactionsText.visibility = View.VISIBLE
                } else {
                    emptyMonthlyTransactionsText.visibility = View.GONE

                    // Loop through monthly transactions and add them to the UI
                    for (i in 0 until monthlyTransactionsArray.length()) {
                        val monthlyTransaction = monthlyTransactionsArray.getJSONObject(i)
                        addMonthlyTransactionToUI(monthlyTransaction, i)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading monthly transactions", Toast.LENGTH_SHORT).show()
            }
        } else {
            // No monthly transactions yet
            emptyMonthlyTransactionsText.visibility = View.VISIBLE
        }
    }

    private fun addMonthlyTransactionToUI(monthlyTransaction: JSONObject, index: Int) {
        // Inflate monthly transaction item layout
        val monthlyTransactionView = LayoutInflater.from(this)
            .inflate(R.layout.monthly_transaction_item, monthlyTransactionsContainer, false)

        // Get data from monthly transaction
        val name = monthlyTransaction.getString("name")
        val amount = monthlyTransaction.getDouble("amount")
        val isIncome = monthlyTransaction.getBoolean("isIncome")
        val category = monthlyTransaction.getString("category")
        val wallet = monthlyTransaction.getString("wallet")
        val dayOfMonth = monthlyTransaction.getInt("dayOfMonth")
        val isActive = monthlyTransaction.getBoolean("isActive")

        // Set data to views
        val nameTextView = monthlyTransactionView.findViewById<TextView>(R.id.monthly_transaction_name)
        val categoryTextView = monthlyTransactionView.findViewById<TextView>(R.id.monthly_transaction_category)
        val walletTextView = monthlyTransactionView.findViewById<TextView>(R.id.monthly_transaction_wallet)
        val dayTextView = monthlyTransactionView.findViewById<TextView>(R.id.monthly_transaction_day)
        val amountTextView = monthlyTransactionView.findViewById<TextView>(R.id.monthly_transaction_amount)
        val categoryIcon = monthlyTransactionView.findViewById<ImageView>(R.id.monthly_transaction_category_icon)
        val editButton = monthlyTransactionView.findViewById<ImageButton>(R.id.btn_edit_monthly_transaction)
        val toggleButton = monthlyTransactionView.findViewById<ImageButton>(R.id.btn_toggle_monthly_transaction)

        nameTextView.text = name
        categoryTextView.text = category
        walletTextView.text = wallet
        dayTextView.text = "Day: $dayOfMonth"

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

        // Set proper icon for toggle button based on active status
        if (isActive) {
            toggleButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            toggleButton.contentDescription = "Disable monthly transaction"
            monthlyTransactionView.alpha = 1.0f
        } else {
            toggleButton.setImageResource(android.R.drawable.ic_menu_add)
            toggleButton.contentDescription = "Enable monthly transaction"
            monthlyTransactionView.alpha = 0.5f
        }

        // Set up edit button click listener
        editButton.setOnClickListener {
            showEditMonthlyTransactionDialog(monthlyTransaction, index)
        }

        // Set up toggle button click listener
        toggleButton.setOnClickListener {
            toggleMonthlyTransaction(index)
        }

        // Add monthly transaction view to container
        monthlyTransactionsContainer.addView(monthlyTransactionView)
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

        // If no wallets, show message and return
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

        // Set up wallet spinner
        val walletAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, walletsList)
        walletSpinner.adapter = walletAdapter

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
            .show()
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

        // Reload monthly transactions
        loadMonthlyTransactions()

        Toast.makeText(this, "Monthly transaction added successfully", Toast.LENGTH_SHORT).show()
    }

    private fun showEditMonthlyTransactionDialog(monthlyTransaction: JSONObject, index: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_monthly_transaction, null)

        val transactionNameEditText = dialogView.findViewById<EditText>(R.id.edit_monthly_transaction_name)
        val amountEditText = dialogView.findViewById<EditText>(R.id.edit_monthly_amount)
        val isIncomeCheckBox = dialogView.findViewById<CheckBox>(R.id.checkbox_monthly_is_income)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.spinner_monthly_category)
        val walletSpinner = dialogView.findViewById<Spinner>(R.id.spinner_monthly_wallet)
        val dayEditText = dialogView.findViewById<EditText>(R.id.edit_monthly_day)

        // Fill with current monthly transaction data
        transactionNameEditText.setText(monthlyTransaction.getString("name"))
        amountEditText.setText(monthlyTransaction.getDouble("amount").toString())
        isIncomeCheckBox.isChecked = monthlyTransaction.getBoolean("isIncome")
        dayEditText.setText(monthlyTransaction.getInt("dayOfMonth").toString())

        // Set up category spinner
        val categories = listOf("Essentials", "Savings", "Pets", "Health", "Donations", "Entertainment", "Food")
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = categoryAdapter
        
        // Set selected category
        val categoryPosition = categories.indexOf(monthlyTransaction.getString("category"))
        if (categoryPosition >= 0) {
            categorySpinner.setSelection(categoryPosition)
        }

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

        // Set up wallet spinner
        val walletAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, walletsList)
        walletSpinner.adapter = walletAdapter
        
        // Set selected wallet
        val walletPosition = walletsList.indexOf(monthlyTransaction.getString("wallet"))
        if (walletPosition >= 0) {
            walletSpinner.setSelection(walletPosition)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Monthly Transaction")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
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
                            updateMonthlyTransaction(index, transactionName, amount, isIncome, 
                                                    category, walletName, day, 
                                                    monthlyTransaction.getBoolean("isActive"))
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
            .show()
    }

    private fun updateMonthlyTransaction(index: Int, name: String, amount: Double, isIncome: Boolean,
                                         category: String, walletName: String, dayOfMonth: Int, isActive: Boolean) {
        // Get current monthly transactions
        val monthlyTransactionsJson = sharedPreferences.getString("monthly_transactions", null) ?: return
        
        try {
            val monthlyTransactionsArray = JSONArray(monthlyTransactionsJson)
            
            // Create updated monthly transaction object
            val updatedMonthlyTransaction = JSONObject().apply {
                put("name", name)
                put("amount", amount)
                put("isIncome", isIncome)
                put("category", category)
                put("wallet", walletName)
                put("dayOfMonth", dayOfMonth)
                put("isActive", isActive)
                // Preserve the original creation timestamp
                put("createdAt", monthlyTransactionsArray.getJSONObject(index).optLong("createdAt", System.currentTimeMillis()))
            }
            
            // Replace the old monthly transaction with the updated one
            monthlyTransactionsArray.put(index, updatedMonthlyTransaction)
            
            // Save updated monthly transactions
            sharedPreferences.edit()
                .putString("monthly_transactions", monthlyTransactionsArray.toString())
                .apply()
            
            // Reload monthly transactions
            loadMonthlyTransactions()
            
            Toast.makeText(this, "Monthly transaction updated successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error updating monthly transaction", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleMonthlyTransaction(index: Int) {
        // Get current monthly transactions
        val monthlyTransactionsJson = sharedPreferences.getString("monthly_transactions", null) ?: return
        
        try {
            val monthlyTransactionsArray = JSONArray(monthlyTransactionsJson)
            val monthlyTransaction = monthlyTransactionsArray.getJSONObject(index)
            
            // Toggle isActive status
            val isCurrentlyActive = monthlyTransaction.getBoolean("isActive")
            monthlyTransaction.put("isActive", !isCurrentlyActive)
            
            // Update the monthly transaction
            monthlyTransactionsArray.put(index, monthlyTransaction)
            
            // Save updated monthly transactions
            sharedPreferences.edit()
                .putString("monthly_transactions", monthlyTransactionsArray.toString())
                .apply()
            
            // Reload monthly transactions
            loadMonthlyTransactions()
            
            val status = if (!isCurrentlyActive) "enabled" else "disabled"
            Toast.makeText(this, "Monthly transaction $status", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error toggling monthly transaction", Toast.LENGTH_SHORT).show()
        }
    }
} 