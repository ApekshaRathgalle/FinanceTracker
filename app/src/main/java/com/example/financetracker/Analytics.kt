package com.example.financetracker

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class Analytics : AppCompatActivity() {

    private lateinit var doubleLineChart: LineChart
    private lateinit var categoryPieChart: PieChart
    private lateinit var walletDropdown: AutoCompleteTextView
    private lateinit var timePeriodChipGroup: ChipGroup
    private lateinit var viewAllTransactionsButton: MaterialButton
    private lateinit var categoryLegendRecycler: RecyclerView
    private lateinit var topCategoriesRecycler: RecyclerView
    private lateinit var sharedPreferences: SharedPreferences

    private var currentTimeframe = "Monthly"
    private var currentWallet = "Primary Wallet"
    private var customStartDate: Calendar? = null
    private var customEndDate: Calendar? = null
    private var walletsList = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analytics)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)

        // Initialize views
        doubleLineChart = findViewById(R.id.double_line_chart)
        categoryPieChart = findViewById(R.id.category_pie_chart)
        walletDropdown = findViewById(R.id.wallet_dropdown)
        timePeriodChipGroup = findViewById(R.id.time_period_chip_group)
        viewAllTransactionsButton = findViewById(R.id.view_all_transactions)
        categoryLegendRecycler = findViewById(R.id.category_legend_recycler)
        topCategoriesRecycler = findViewById(R.id.top_categories_recycler)

        // Load wallets from SharedPreferences
        loadWallets()

        // Set up wallet selector
        setupWalletSelector()

        // Set up time period chip group
        setupTimePeriodChips()

        // Set up charts
        setupDoubleLineChart()
        setupPieChart()

        // Set up recycler views
        setupCategoryLegend()
        setupTopCategories()

        // Set up view all transactions click
        viewAllTransactionsButton.setOnClickListener {
            // Navigate to the wallet transactions report
            WalletTransactionsActivity.start(this)
        }

        // Set up bottom navigation
        setupBottomNavigation()
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
                walletsList.add("Primary Wallet")
            }
        }
        
        // If no wallets found, add a default one
        if (walletsList.isEmpty()) {
            walletsList.add("Primary Wallet")
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
            // Update charts with new wallet data
            updateCharts()
        }
    }

    private fun setupTimePeriodChips() {
        // Get references to all chips
        val chipWeekly = findViewById<Chip>(R.id.chip_weekly)
        val chipMonthly = findViewById<Chip>(R.id.chip_monthly)
        val chipYearly = findViewById<Chip>(R.id.chip_yearly)
        val chipCustom = findViewById<Chip>(R.id.chip_custom)

        // Set initial selected chip
        chipMonthly.isChecked = true

        // Set listener for chip group
        timePeriodChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds[0]) {
                    R.id.chip_weekly -> {
                        currentTimeframe = "Weekly"
                        customStartDate = null
                        customEndDate = null
                    }
                    R.id.chip_monthly -> {
                        currentTimeframe = "Monthly"
                        customStartDate = null
                        customEndDate = null
                    }
                    R.id.chip_yearly -> {
                        currentTimeframe = "Year"
                        customStartDate = null
                        customEndDate = null
                    }
                    R.id.chip_custom -> {
                        currentTimeframe = "Custom"
                        showDatePickerDialog()
                    }
                }
                // Update charts with new time period
                updateCharts()
            }
        }
    }

    private fun showDatePickerDialog() {
        // Initialize calendar to today
        val calendar = Calendar.getInstance()
        
        // Show start date picker
        val startDatePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                // Set start date
                val startCalendar = Calendar.getInstance()
                startCalendar.set(year, month, dayOfMonth)
                customStartDate = startCalendar
                
                // Show end date picker after start date is selected
                val endDatePickerDialog = DatePickerDialog(
                    this,
                    { _, endYear, endMonth, endDayOfMonth ->
                        // Set end date
                        val endCalendar = Calendar.getInstance()
                        endCalendar.set(endYear, endMonth, endDayOfMonth)
                        customEndDate = endCalendar
                        
                        // Update charts with selected date range
                        updateCharts()
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                
                // Set min date to the start date
                endDatePickerDialog.datePicker.minDate = startCalendar.timeInMillis
                
                endDatePickerDialog.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        
        // Set max date to today
        startDatePickerDialog.datePicker.maxDate = calendar.timeInMillis
        
        startDatePickerDialog.show()
    }

    private fun setupDoubleLineChart() {
        // Remove grid background
        doubleLineChart.setDrawGridBackground(false)

        // Disable description
        doubleLineChart.description.isEnabled = false

        // Enable touch gestures
        doubleLineChart.setTouchEnabled(true)
        doubleLineChart.isDragEnabled = true
        doubleLineChart.setScaleEnabled(true)

        // X-axis setup
        val xAxis = doubleLineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(true)

        // Left Y-axis setup
        val leftAxis = doubleLineChart.axisLeft
        leftAxis.setDrawGridLines(true)

        // Right Y-axis setup
        val rightAxis = doubleLineChart.axisRight
        rightAxis.isEnabled = false

        // Update chart with data
        updateDoubleLineChart()
    }

    private fun setupPieChart() {
        // Disable description
        categoryPieChart.description.isEnabled = false

        // Set hole in the middle
        categoryPieChart.isDrawHoleEnabled = true
        categoryPieChart.holeRadius = 35f
        categoryPieChart.transparentCircleRadius = 40f

        // Format center text
        categoryPieChart.setDrawCenterText(false)

        // Set legend and entry labels
        categoryPieChart.legend.isEnabled = false
        categoryPieChart.setEntryLabelColor(Color.BLACK)
        categoryPieChart.setEntryLabelTextSize(12f)

        // Update chart with data
        updatePieChart()
    }

    private fun updateDoubleLineChart() {
        // Create income entries
        val incomeEntries = ArrayList<Entry>()

        // Create expense entries
        val expenseEntries = ArrayList<Entry>()

        // Get transaction data for the selected wallet
        val transactionData = getWalletTransactions()
        
        // Generate chart data based on timeframe
        when (currentTimeframe) {
            "Monthly" -> {
                // Set labels for months
                val months = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
                doubleLineChart.xAxis.valueFormatter = IndexAxisValueFormatter(months)
                doubleLineChart.xAxis.labelCount = 12
                
                // Calculate monthly totals
                val monthlyIncome = DoubleArray(12) { 0.0 }
                val monthlyExpenses = DoubleArray(12) { 0.0 }
                
                // Group transactions by month
                for (transaction in transactionData) {
                    try {
                        val date = dateFormat.parse(transaction.date)
                        val calendar = Calendar.getInstance()
                        calendar.time = date ?: continue
                        val month = calendar.get(Calendar.MONTH)
                        
                        if (transaction.isIncome) {
                            monthlyIncome[month] += transaction.amount
                        } else {
                            monthlyExpenses[month] += transaction.amount
                        }
                    } catch (e: Exception) {
                        // Skip invalid dates
                        continue
                    }
                }
                
                // Create data entries
                for (i in 0..11) {
                    incomeEntries.add(Entry(i.toFloat(), monthlyIncome[i].toFloat()))
                    expenseEntries.add(Entry(i.toFloat(), monthlyExpenses[i].toFloat()))
                }
            }
            "Weekly" -> {
                // Generate labels for past 7 days
                val dayLabels = Array(7) { i ->
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_MONTH, -6 + i)
                    calendar.get(Calendar.DAY_OF_MONTH).toString()
                }
                doubleLineChart.xAxis.valueFormatter = IndexAxisValueFormatter(dayLabels)
                doubleLineChart.xAxis.labelCount = 7
                
                // Calculate daily totals for past 7 days
                val dailyIncome = DoubleArray(7) { 0.0 }
                val dailyExpenses = DoubleArray(7) { 0.0 }
                
                // Get timestamp for 7 days ago
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_MONTH, -6)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val weekStartTime = calendar.timeInMillis
                
                // Group transactions by day
                for (transaction in transactionData) {
                    try {
                        val date = dateFormat.parse(transaction.date)
                        if (date == null || date.time < weekStartTime) continue
                        
                        val transactionCalendar = Calendar.getInstance()
                        transactionCalendar.time = date
                        
                        // Calculate day index (0-6)
                        val dayDiff = ((date.time - weekStartTime) / (24 * 60 * 60 * 1000)).toInt()
                        if (dayDiff >= 0 && dayDiff < 7) {
                            if (transaction.isIncome) {
                                dailyIncome[dayDiff] += transaction.amount
                            } else {
                                dailyExpenses[dayDiff] += transaction.amount
                            }
                        }
                    } catch (e: Exception) {
                        // Skip invalid dates
                        continue
                    }
                }
                
                // Create data entries
                for (i in 0..6) {
                    incomeEntries.add(Entry(i.toFloat(), dailyIncome[i].toFloat()))
                    expenseEntries.add(Entry(i.toFloat(), dailyExpenses[i].toFloat()))
                }
            }
            "Year" -> {
                // Generate labels for years
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val yearLabels = Array(5) { i -> (currentYear - 4 + i).toString() }
                doubleLineChart.xAxis.valueFormatter = IndexAxisValueFormatter(yearLabels)
                doubleLineChart.xAxis.labelCount = 5
                
                // Calculate yearly totals
                val yearlyIncome = DoubleArray(5) { 0.0 }
                val yearlyExpenses = DoubleArray(5) { 0.0 }
                
                // Group transactions by year
                for (transaction in transactionData) {
                    try {
                        val date = dateFormat.parse(transaction.date)
                        val calendar = Calendar.getInstance()
                        calendar.time = date ?: continue
                        val year = calendar.get(Calendar.YEAR)
                        
                        // Calculate year index (0-4)
                        val yearIndex = year - (currentYear - 4)
                        if (yearIndex >= 0 && yearIndex < 5) {
                            if (transaction.isIncome) {
                                yearlyIncome[yearIndex] += transaction.amount
                            } else {
                                yearlyExpenses[yearIndex] += transaction.amount
                            }
                        }
                    } catch (e: Exception) {
                        // Skip invalid dates
                        continue
                    }
                }
                
                // Create data entries
                for (i in 0..4) {
                    incomeEntries.add(Entry(i.toFloat(), yearlyIncome[i].toFloat()))
                    expenseEntries.add(Entry(i.toFloat(), yearlyExpenses[i].toFloat()))
                }
            }
            "Custom" -> {
                if (customStartDate != null && customEndDate != null) {
                    // Calculate number of days in range
                    val dayDiff = ((customEndDate!!.timeInMillis - customStartDate!!.timeInMillis) / (24 * 60 * 60 * 1000)).toInt() + 1
                    val dayLabels = Array(dayDiff) { i ->
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = customStartDate!!.timeInMillis
                        calendar.add(Calendar.DAY_OF_MONTH, i)
                        calendar.get(Calendar.DAY_OF_MONTH).toString()
                    }
                    doubleLineChart.xAxis.valueFormatter = IndexAxisValueFormatter(dayLabels)
                    doubleLineChart.xAxis.labelCount = minOf(dayDiff, 10) // Limit labels to 10 for readability
                    
                    // Calculate daily totals
                    val dailyIncome = DoubleArray(dayDiff) { 0.0 }
                    val dailyExpenses = DoubleArray(dayDiff) { 0.0 }
                    
                    // Group transactions by day
                    for (transaction in transactionData) {
                        try {
                            val date = dateFormat.parse(transaction.date)
                            if (date == null) continue
                            
                            // Check if transaction is within date range
                            if (date.time >= customStartDate!!.timeInMillis && 
                                date.time <= customEndDate!!.timeInMillis) {
                                
                                // Calculate day index
                                val dayIndex = ((date.time - customStartDate!!.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
                                if (dayIndex >= 0 && dayIndex < dayDiff) {
                                    if (transaction.isIncome) {
                                        dailyIncome[dayIndex] += transaction.amount
                                    } else {
                                        dailyExpenses[dayIndex] += transaction.amount
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Skip invalid dates
                            continue
                        }
                    }
                    
                    // Create data entries
                    for (i in 0 until dayDiff) {
                        incomeEntries.add(Entry(i.toFloat(), dailyIncome[i].toFloat()))
                        expenseEntries.add(Entry(i.toFloat(), dailyExpenses[i].toFloat()))
                    }
                } else {
                    // Fallback to monthly if custom dates not selected
                    val months = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
                    doubleLineChart.xAxis.valueFormatter = IndexAxisValueFormatter(months)
                    doubleLineChart.xAxis.labelCount = 12
                    
                    // Use sample data if no custom dates
                    for (i in 0..11) {
                        incomeEntries.add(Entry(i.toFloat(), 10f + (i * 2f) + (Random.nextFloat() * 5f)))
                        expenseEntries.add(Entry(i.toFloat(), 8f + (i * 1.5f) + (Random.nextFloat() * 6f)))
                    }
                }
            }
        }

        // Get colors from resources
        val incomeColor = ContextCompat.getColor(this, R.color.blue)
        val expenseColor = ContextCompat.getColor(this, R.color.red)

        // Create dataset for income
        val incomeDataSet = LineDataSet(incomeEntries, "Income")
        incomeDataSet.color = incomeColor
        incomeDataSet.setCircleColor(incomeColor)
        incomeDataSet.lineWidth = 2f
        incomeDataSet.circleRadius = 3f
        incomeDataSet.setDrawCircleHole(false)
        incomeDataSet.setDrawValues(false)
        incomeDataSet.setDrawFilled(true)
        incomeDataSet.fillColor = incomeColor
        incomeDataSet.fillAlpha = 30

        // Create dataset for expenses
        val expenseDataSet = LineDataSet(expenseEntries, "Expenses")
        expenseDataSet.color = expenseColor
        expenseDataSet.setCircleColor(expenseColor)
        expenseDataSet.lineWidth = 2f
        expenseDataSet.circleRadius = 3f
        expenseDataSet.setDrawCircleHole(false)
        expenseDataSet.setDrawValues(false)
        expenseDataSet.setDrawFilled(true)
        expenseDataSet.fillColor = expenseColor
        expenseDataSet.fillAlpha = 30

        // Create line data with both datasets
        val lineData = LineData(incomeDataSet, expenseDataSet)

        // Set data to chart
        doubleLineChart.data = lineData

        // Refresh chart
        doubleLineChart.invalidate()
    }

    private fun updatePieChart() {
        // Get transaction data for the selected wallet
        val transactionData = getWalletTransactions()
        
        // Group expenses by category
        val categorySpending = mutableMapOf<String, Double>()
        
        // Only consider expenses (not income) for category breakdown
        for (transaction in transactionData) {
            if (!transaction.isIncome) {
                val currentAmount = categorySpending.getOrDefault(transaction.category, 0.0)
                categorySpending[transaction.category] = currentAmount + transaction.amount
            }
        }
        
        // Create pie entries for categories
        val entries = ArrayList<PieEntry>()
        
        // Sort categories by amount (descending)
        val sortedCategories = categorySpending.entries.sortedByDescending { it.value }
        
        // Create pie entries for the top categories
        var totalAmount = 0.0
        categorySpending.values.forEach { totalAmount += it }
        
        if (totalAmount > 0) {
            for ((category, amount) in sortedCategories) {
                val percentage = (amount / totalAmount * 100).toFloat()
                entries.add(PieEntry(percentage, category))
            }
        } else {
            // If no transaction data, add placeholder
            entries.add(PieEntry(100f, "No Data"))
        }

        // Create dataset
        val dataSet = PieDataSet(entries, "Categories")

        // Set colors matching the design
        val colors = ArrayList<Int>()
        colors.add(Color.parseColor("#FFAABB")) // Red
        colors.add(Color.parseColor("#FFDD77")) // Yellow
        colors.add(Color.parseColor("#AA88FF")) // Purple
        colors.add(Color.parseColor("#66DDFF")) // Blue
        colors.add(Color.parseColor("#88FFAA")) // Green
        colors.add(Color.parseColor("#FFAA77")) // Orange
        colors.add(Color.parseColor("#AADDEE")) // Light blue

        dataSet.colors = colors

        // Create pie data
        val pieData = PieData(dataSet)
        pieData.setDrawValues(false)

        // Set data to chart
        categoryPieChart.data = pieData

        // Animate chart
        categoryPieChart.animateXY(1000, 1000)

        // Refresh chart
        categoryPieChart.invalidate()
        
        // Update legend and top categories
        updateCategoryLegend(sortedCategories, totalAmount)
        updateTopCategories(sortedCategories, totalAmount)
    }

    private fun updateCategoryLegend(sortedCategoryEntries: List<Map.Entry<String, Double>>, totalAmount: Double) {
        // Sample category data for the legend
        val categories = mutableListOf<CategoryItem>()
        
        if (totalAmount > 0) {
            val colorMap = mapOf(
                0 to "#FFAABB",
                1 to "#FFDD77",
                2 to "#AA88FF",
                3 to "#66DDFF",
                4 to "#88FFAA",
                5 to "#FFAA77",
                6 to "#AADDEE"
            )
            
            // Add categories to the list with their percentage
            sortedCategoryEntries.forEachIndexed { index, entry ->
                val colorHex = colorMap[index % colorMap.size] ?: "#AAAAAA"
                val percentage = (entry.value / totalAmount * 100).toFloat()
                categories.add(CategoryItem(entry.key, colorHex, percentage))
            }
        } else {
            // Add placeholder if no data
            categories.add(CategoryItem("No Data", "#AAAAAA", 100f))
        }

        // Set up recycler view
        categoryLegendRecycler.layoutManager = LinearLayoutManager(this)
        categoryLegendRecycler.adapter = CategoryLegendAdapter(categories)
    }
    
    private fun updateTopCategories(sortedCategoryEntries: List<Map.Entry<String, Double>>, totalAmount: Double) {
        // Generate top categories data
        val topCategories = mutableListOf<TopCategoryItem>()
        
        if (totalAmount > 0) {
            val colorMap = mapOf(
                0 to "#FFAABB",
                1 to "#FFDD77",
                2 to "#AA88FF",
                3 to "#66DDFF"
            )
            
            // Get top 4 categories
            sortedCategoryEntries.take(4).forEachIndexed { index, entry ->
                val colorHex = colorMap[index] ?: "#AAAAAA"
                val percentage = (entry.value / totalAmount * 100).toInt()
                topCategories.add(TopCategoryItem(entry.key, colorHex, entry.value.toFloat(), percentage))
            }
        } else {
            // Add placeholder if no data
            topCategories.add(TopCategoryItem("No Data", "#AAAAAA", 0f, 0))
        }

        // Set up recycler view
        topCategoriesRecycler.layoutManager = LinearLayoutManager(this)
        topCategoriesRecycler.adapter = TopCategoriesAdapter(topCategories)
    }
    
    // Class to hold transaction data
    private data class TransactionData(
        val name: String,
        val amount: Double,
        val isIncome: Boolean,
        val category: String,
        val date: String
    )

    private fun getWalletTransactions(): List<TransactionData> {
        val transactions = mutableListOf<TransactionData>()
        
        // Get transactions for the selected wallet
        val transactionsKey = "transactions_$currentWallet"
        val transactionsJson = sharedPreferences.getString(transactionsKey, null)
        
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
                    
                    // Apply time period filter
                    if (isTransactionInTimeframe(date)) {
                        transactions.add(TransactionData(name, amount, isIncome, category, date))
                    }
                }
            } catch (e: Exception) {
                // Handle errors parsing transactions
            }
        }
        
        return transactions
    }
    
    private fun isTransactionInTimeframe(dateString: String): Boolean {
        try {
            val date = dateFormat.parse(dateString) ?: return false
            val calendar = Calendar.getInstance()
            calendar.time = date
            
            when (currentTimeframe) {
                "Weekly" -> {
                    // Check if date is within last 7 days
                    val weekAgo = Calendar.getInstance()
                    weekAgo.add(Calendar.DAY_OF_MONTH, -7)
                    return date.time >= weekAgo.timeInMillis
                }
                "Monthly" -> {
                    // Check if date is within current month
                    val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                    return calendar.get(Calendar.MONTH) == currentMonth && 
                           calendar.get(Calendar.YEAR) == currentYear
                }
                "Year" -> {
                    // Check if date is within last 5 years
                    val fiveYearsAgo = Calendar.getInstance()
                    fiveYearsAgo.add(Calendar.YEAR, -5)
                    return date.time >= fiveYearsAgo.timeInMillis
                }
                "Custom" -> {
                    // Check if date is within custom date range
                    return if (customStartDate != null && customEndDate != null) {
                        date.time >= customStartDate!!.timeInMillis && 
                        date.time <= customEndDate!!.timeInMillis
                    } else {
                        true // Include all if no custom range
                    }
                }
                else -> return true
            }
        } catch (e: Exception) {
            return false
        }
    }

    private fun setupCategoryLegend() {
        // Initial setup will be updated by updateCategoryLegend
        categoryLegendRecycler.layoutManager = LinearLayoutManager(this)
        categoryLegendRecycler.adapter = CategoryLegendAdapter(emptyList())
    }

    private fun setupTopCategories() {
        // Initial setup will be updated by updateTopCategories
        topCategoriesRecycler.layoutManager = LinearLayoutManager(this)
        topCategoriesRecycler.adapter = TopCategoriesAdapter(emptyList())
    }

    private fun updateCharts() {
        // Update both charts with new data based on selected wallet and timeframe
        updateDoubleLineChart()
        updatePieChart()
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Set the analytics item as selected
        bottomNavigation.selectedItemId = R.id.nav_transaction_history

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, Homepage::class.java))
                    finish()
                    true
                }
                R.id.nav_add_transaction -> {
                    startActivity(Intent(this, Transactions::class.java))
                    finish()
                    true
                }
                R.id.nav_transaction_history -> {
                    // If already on Analytics page, just scroll to top
                    val scrollView = findViewById<NestedScrollView>(R.id.analytics_scroll_view)
                    scrollView.smoothScrollTo(0, 0)
                    true
                }
                R.id.nav_budget_setup -> {
                    startActivity(Intent(this, Budget::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    // Data classes for RecyclerView adapters
    data class CategoryItem(val name: String, val colorHex: String, val percentage: Float)
    data class TopCategoryItem(val name: String, val colorHex: String, val amount: Float, val percentage: Int)

    // Adapter class for category legend
    inner class CategoryLegendAdapter(private val categories: List<CategoryItem>) :
        RecyclerView.Adapter<CategoryLegendAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.transaction_name)
            val categoryText: TextView = view.findViewById(R.id.transaction_category)
            val amountText: TextView = view.findViewById(R.id.transaction_amount)
            val iconContainer: View = view.findViewById(R.id.icon_container)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // Use the existing transaction item layout
            return ViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.transaction_item, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            
            // Set category name
            holder.nameText.text = category.name
            
            // Set percentage as category tag
            holder.categoryText.text = String.format("%.1f%%", category.percentage)
            
            // Display color as background of icon container
            try {
                val color = Color.parseColor(category.colorHex)
                holder.iconContainer.setBackgroundColor(color)
            } catch (e: Exception) {
                // Use default color if parsing fails
                holder.iconContainer.setBackgroundColor(Color.LTGRAY)
            }
            
            // Hide amount text
            holder.amountText.visibility = View.GONE
        }

        override fun getItemCount() = categories.size
    }

    // Adapter class for top categories
    inner class TopCategoriesAdapter(private val categories: List<TopCategoryItem>) :
        RecyclerView.Adapter<TopCategoriesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.transaction_name)
            val categoryText: TextView = view.findViewById(R.id.transaction_category)
            val amountText: TextView = view.findViewById(R.id.transaction_amount)
            val dateText: TextView = view.findViewById(R.id.transaction_date)
            val iconContainer: View = view.findViewById(R.id.icon_container)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // Use the existing transaction item layout
            return ViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.transaction_item, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            
            // Set category name
            holder.nameText.text = category.name
            
            // Set percentage in the category tag
            holder.categoryText.text = String.format("%d%%", category.percentage)
            
            // Set amount
            holder.amountText.text = String.format("$%.2f", category.amount)
            holder.amountText.setTextColor(Color.BLACK) // Use neutral color
            
            // Use date field to show rank
            holder.dateText.text = String.format("Rank #%d", position + 1)
            
            // Display color as background of icon container
            try {
                val color = Color.parseColor(category.colorHex)
                holder.iconContainer.setBackgroundColor(color)
            } catch (e: Exception) {
                // Use default color if parsing fails
                holder.iconContainer.setBackgroundColor(Color.LTGRAY)
            }
        }

        override fun getItemCount() = categories.size
    }
}