package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class WalletTransactionsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var walletDropdown: AutoCompleteTextView
    private lateinit var tvTotalIncome: TextView
    private lateinit var tvTotalExpenses: TextView
    private lateinit var tvTransactionCount: TextView
    private lateinit var exportCsvButton: MaterialButton
    private lateinit var exportPdfButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var adapter: TransactionReportAdapter

    private var walletsList = mutableListOf<String>()
    private var currentWallet = "Default Wallet"
    private var transactions = mutableListOf<TransactionData>()
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val csvDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    private val createCsvDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { exportDataToCsv(it) }
    }
    
    private val createPdfDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { exportDataToPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_transactions)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)

        // Initialize views
        toolbar = findViewById(R.id.toolbar)
        walletDropdown = findViewById(R.id.wallet_dropdown)
        tvTotalIncome = findViewById(R.id.tv_total_income)
        tvTotalExpenses = findViewById(R.id.tv_total_expenses)
        tvTransactionCount = findViewById(R.id.tv_transaction_count)
        exportCsvButton = findViewById(R.id.btn_export_csv)
        exportPdfButton = findViewById(R.id.btn_export_pdf)
        recyclerView = findViewById(R.id.transactions_recycler_view)
        progressIndicator = findViewById(R.id.progress_indicator)

        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Setup RecyclerView with delete callback
        adapter = TransactionReportAdapter(transactions) { transactionToDelete ->
            deleteTransaction(transactionToDelete)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Load wallets from SharedPreferences
        loadWallets()

        // Set up wallet selector
        setupWalletSelector()

        // Setup CSV export button
        exportCsvButton.setOnClickListener {
            if (transactions.isEmpty()) {
                Toast.makeText(this, "No transactions to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Create a file name based on the wallet name and current date
            val fileName = "${currentWallet.replace(" ", "_")}_transactions.csv"
            createCsvDocumentLauncher.launch(fileName)
        }
        
        // Setup PDF export button
        exportPdfButton.setOnClickListener {
            if (transactions.isEmpty()) {
                Toast.makeText(this, "No transactions to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Create a file name based on the wallet name and current date
            val fileName = "${currentWallet.replace(" ", "_")}_transactions.pdf"
            createPdfDocumentLauncher.launch(fileName)
        }
    }

    private fun loadWallets() {
        walletsList.clear()
        
        // Get wallets from SharedPreferences
        val walletsJson = sharedPreferences.getString("wallets", null)
        
        if (walletsJson != null) {
            try {
                val jsonArray = JSONArray(walletsJson)
                
                // Extract wallet names
                for (i in 0 until jsonArray.length()) {
                    val wallet = jsonArray.getJSONObject(i)
                    walletsList.add(wallet.getString("name"))
                }
            } catch (e: Exception) {
                // If error loading wallets, add a default one
                walletsList.add("Default Wallet")
            }
        }
        
        // If no wallets found, add a default one
        if (walletsList.isEmpty()) {
            walletsList.add("Default Wallet")
        }
        
        // Use the first wallet as current by default
        currentWallet = walletsList[0]
    }

    private fun setupWalletSelector() {
        // Create adapter for the dropdown
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, walletsList)
        walletDropdown.setAdapter(adapter)
        walletDropdown.setText(currentWallet, false)

        // Set listener for selection
        walletDropdown.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            currentWallet = parent.getItemAtPosition(position).toString()
            // Load transactions for this wallet
            loadTransactions()
        }
        
        // Load transactions for the initial wallet
        loadTransactions()
    }

    private fun loadTransactions() {
        progressIndicator.visibility = View.VISIBLE
        transactions.clear()
        
        thread {
            // Get transactions for the selected wallet
            val transactionsKey = "transactions_$currentWallet"
            val transactionsJson = sharedPreferences.getString(transactionsKey, null)
            
            var totalIncome = 0.0
            var totalExpenses = 0.0
            
            if (transactionsJson != null) {
                try {
                    val jsonArray = JSONArray(transactionsJson)
                    
                    // Parse each transaction
                    for (i in 0 until jsonArray.length()) {
                        val transaction = jsonArray.getJSONObject(i)
                        
                        val name = transaction.getString("name")
                        val amount = transaction.getDouble("amount")
                        val isIncome = transaction.getBoolean("isIncome")
                        val category = transaction.getString("category")
                        val date = transaction.getString("date")
                        val timestamp = if (transaction.has("timestamp")) {
                            transaction.getLong("timestamp")
                        } else {
                            0L
                        }
                        
                        // Add to the transactions list
                        transactions.add(TransactionData(name, amount, isIncome, category, date, timestamp))
                        
                        // Update totals
                        if (isIncome) {
                            totalIncome += amount
                        } else {
                            totalExpenses += amount
                        }
                    }
                } catch (e: Exception) {
                    // Handle errors parsing transactions
                    runOnUiThread {
                        Toast.makeText(this, "Error loading transactions", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            // Sort transactions by date (newest first)
            transactions.sortByDescending { it.timestamp }
            
            runOnUiThread {
                // Update UI with transaction data
                adapter.updateTransactions(transactions)
                tvTotalIncome.text = currencyFormat.format(totalIncome)
                tvTotalExpenses.text = currencyFormat.format(totalExpenses)
                tvTransactionCount.text = transactions.size.toString()
                progressIndicator.visibility = View.GONE
            }
        }
    }

    private fun exportDataToCsv(uri: Uri) {
        progressIndicator.visibility = View.VISIBLE
        
        thread {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val writer = outputStream.writer()
                    
                    // Write CSV header
                    writer.write("Transaction Name,Amount,Type,Category,Date,Time\n")
                    
                    // Write transaction data
                    for (transaction in transactions) {
                        val type = if (transaction.isIncome) "Income" else "Expense"
                        
                        // Parse date to get separate date and time
                        var dateStr = ""
                        var timeStr = ""
                        try {
                            val parsedDate = dateFormat.parse(transaction.date)
                            if (parsedDate != null) {
                                dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(parsedDate)
                                timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(parsedDate)
                            } else {
                                dateStr = transaction.date
                            }
                        } catch (e: Exception) {
                            dateStr = transaction.date
                        }
                        
                        // Escape any commas in the transaction name to avoid CSV issues
                        val escapedName = transaction.name.replace("\"", "\"\"")
                        
                        // Write the transaction row
                        writer.write("\"$escapedName\",${transaction.amount},$type,${transaction.category},$dateStr,$timeStr\n")
                    }
                    
                    writer.flush()
                    writer.close()
                    
                    runOnUiThread {
                        progressIndicator.visibility = View.GONE
                        Toast.makeText(this, "CSV file exported successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressIndicator.visibility = View.GONE
                    Toast.makeText(this, "Error exporting data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun exportDataToPdf(uri: Uri) {
        progressIndicator.visibility = View.VISIBLE
        
        thread {
            try {
                // Create a new PDF document
                val document = PdfDocument()
                
                // Create page info for the first page
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                
                // Start a new page
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                
                // Create paint objects for styling text
                val titlePaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 24f
                    isFakeBoldText = true
                }
                
                val headerPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 16f
                    isFakeBoldText = true
                }
                
                val textPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 12f
                }
                
                val incomePaint = Paint().apply {
                    color = Color.rgb(0, 0, 200) // Blue
                    textSize = 12f
                }
                
                val expensePaint = Paint().apply {
                    color = Color.rgb(200, 0, 0) // Red
                    textSize = 12f
                }
                
                // Add title and date
                canvas.drawText("Wallet Transactions Report", 50f, 50f, titlePaint)
                canvas.drawText("Wallet: $currentWallet", 50f, 80f, headerPaint)
                canvas.drawText("Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}", 50f, 100f, headerPaint)
                
                // Add summary
                val totalIncome = transactions.filter { it.isIncome }.sumOf { it.amount }
                val totalExpenses = transactions.filter { !it.isIncome }.sumOf { it.amount }
                
                canvas.drawText("Summary", 50f, 130f, headerPaint)
                canvas.drawText("Total Income: ${currencyFormat.format(totalIncome)}", 50f, 150f, incomePaint)
                canvas.drawText("Total Expenses: ${currencyFormat.format(totalExpenses)}", 50f, 170f, expensePaint)
                canvas.drawText("Total Transactions: ${transactions.size}", 50f, 190f, textPaint)
                
                // Add table header
                canvas.drawText("Transaction Details", 50f, 220f, headerPaint)
                canvas.drawLine(50f, 230f, 545f, 230f, headerPaint)
                
                canvas.drawText("Name", 50f, 245f, headerPaint)
                canvas.drawText("Amount", 220f, 245f, headerPaint)
                canvas.drawText("Category", 300f, 245f, headerPaint)
                canvas.drawText("Date", 400f, 245f, headerPaint)
                
                canvas.drawLine(50f, 255f, 545f, 255f, headerPaint)
                
                // Add transaction data
                var y = 275f
                val rowHeight = 20f
                var currentPage = 1
                
                for ((index, transaction) in transactions.withIndex()) {
                    // Check if we need to create a new page
                    if (y > 800f) {
                        // Finish the current page
                        document.finishPage(page)
                        
                        // Start a new page
                        currentPage++
                        val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, currentPage).create()
                        val newPage = document.startPage(newPageInfo)
                        canvas.save()
                        canvas.translate(0f, -y + 80f) // Reset y position
                        y = 80f
                        
                        // Add header to new page
                        newPage.canvas.drawText("Wallet Transactions Report - Page $currentPage", 50f, 50f, headerPaint)
                        newPage.canvas.drawLine(50f, 60f, 545f, 60f, headerPaint)
                    }
                    
                    // Format date
                    val formattedDate = try {
                        val parsedDate = dateFormat.parse(transaction.date)
                        if (parsedDate != null) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(parsedDate)
                        } else {
                            transaction.date
                        }
                    } catch (e: Exception) {
                        transaction.date
                    }
                    
                    // Draw the transaction row
                    val paint = if (transaction.isIncome) incomePaint else expensePaint
                    val amountText = if (transaction.isIncome) 
                        "+${currencyFormat.format(transaction.amount)}" 
                    else 
                        "-${currencyFormat.format(transaction.amount)}"
                    
                    canvas.drawText(
                        transaction.name.take(20) + if (transaction.name.length > 20) "..." else "", 
                        50f, y, textPaint
                    )
                    canvas.drawText(amountText, 220f, y, paint)
                    canvas.drawText(transaction.category, 300f, y, textPaint)
                    canvas.drawText(formattedDate, 400f, y, textPaint)
                    
                    y += rowHeight
                    
                    // Add a separator line if not the last item
                    if (index < transactions.size - 1) {
                        canvas.drawLine(50f, y - 5f, 545f, y - 5f, textPaint)
                    }
                }
                
                // Finish the page
                document.finishPage(page)
                
                // Write the PDF document to the provided URI
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    document.writeTo(outputStream)
                }
                
                // Close the document
                document.close()
                
                runOnUiThread {
                    progressIndicator.visibility = View.GONE
                    Toast.makeText(this, "PDF file exported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressIndicator.visibility = View.GONE
                    Toast.makeText(this, "Error exporting PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteTransaction(transaction: TransactionData) {
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
                if (!isRemoved && 
                    currentTransaction.getString("name") == transaction.name &&
                    currentTransaction.getDouble("amount") == transaction.amount &&
                    currentTransaction.getString("date") == transaction.date &&
                    currentTransaction.getString("category") == transaction.category) {
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
                restoreWalletBalance(transaction.amount, transaction.isIncome)

                // Reload transactions to update the UI
                loadTransactions()

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle back button in action bar
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, WalletTransactionsActivity::class.java)
            context.startActivity(intent)
        }
    }
} 