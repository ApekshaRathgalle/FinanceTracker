package com.example.financetracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONArray
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import android.graphics.Color
import android.os.Build
import java.util.Calendar
import androidx.core.content.ContextCompat
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.formatter.ValueFormatter
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import androidx.core.widget.NestedScrollView
import de.hdodenhof.circleimageview.CircleImageView
import android.view.LayoutInflater
import org.json.JSONObject
import java.util.Date
import java.util.Comparator
import android.app.AlarmManager

class Homepage : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private lateinit var expenseChart: LineChart
    private lateinit var recentTransactionsContainer: LinearLayout
    private var refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var graphRefreshRunnable: Runnable? = null
    private var lastGraphCheckTimestamp: Long = 0

    // Date formatters
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
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

    // Broadcast receiver for transaction updates
    private val transactionUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.example.financetracker.TRANSACTION_UPDATED" -> {
                    // Log receipt of broadcast for debugging
                    val timestamp = intent.getLongExtra("timestamp", 0)
                    val action = intent.getStringExtra("action") ?: "unknown"
                    android.util.Log.d("Homepage", "Received transaction broadcast: $action at $timestamp")
                    
                    // Force UI thread update
                    runOnUiThread {
                        // Refresh all data immediately when receiving a broadcast
                        refreshAllData()
                        
                        // Reset the flag since we've handled the update
                        sharedPreferences.edit()
                            .putBoolean("transaction_data_changed", false)
                            .apply()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_homepage)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("wallet_prefs", MODE_PRIVATE)
        lastGraphCheckTimestamp = System.currentTimeMillis()

        // Initialize recentTransactionsContainer
        recentTransactionsContainer = findViewById(R.id.recent_transactions_container)

        // Check if this activity was started with refresh_needed flag
        if (intent.getBooleanExtra("refresh_needed", false)) {
            // Force an immediate data refresh
            android.util.Log.d("Homepage", "Refresh needed flag detected - forcing refresh")
            refreshAllData()
            
            // Clear the flag in SharedPreferences
            sharedPreferences.edit()
                .putBoolean("transaction_data_changed", false)
                .putBoolean("immediate_refresh_needed", false)
                .apply()
        }

        // Register for transaction update broadcasts - register for both local and global broadcasts
        try {
            val intentFilter = IntentFilter("com.example.financetracker.TRANSACTION_UPDATED")
            LocalBroadcastManager.getInstance(this).registerReceiver(transactionUpdateReceiver, intentFilter)
            registerReceiver(transactionUpdateReceiver, intentFilter)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize notification system
        initializeNotifications()
        requestNotificationPermission()

        // Set up bottom navigation
        setupBottomNavigation()

        // Load and display wallet data
        loadWalletsData()

        // Set up expense graph
        setupExpenseGraph()

        // Load and display recent transactions
        loadRecentTransactions()

        // Set up "See All Transactions" button
        val seeAllTransactions = findViewById<TextView>(R.id.see_all_transactions)
        seeAllTransactions.setOnClickListener {
            startActivity(Intent(this, Transactions::class.java))
        }

        // Update notification badge
        updateNotificationBadge()

        val profile = findViewById<CircleImageView>(R.id.profile_image)
        // Load the profile image from SharedPreferences
        Profile.loadProfileImageFromPreferences(this, profile)
        // Set content description with user's email for accessibility
        profile.contentDescription = "Profile picture of ${Profile.getUserEmailFromPreferences(this)}"
        profile.setOnClickListener {
            val intent = Intent(this, Profile::class.java)
            startActivity(intent)
        }

        // Add notification icon click listener
        val notificationIcon = findViewById<ImageView>(R.id.notification_icon)
        notificationIcon.setOnClickListener {
            val intent = Intent(this, NotificationsActivity::class.java)
            startActivityForResult(intent, 100)
        }
        
        // Start periodic refresh of transactions
        startTransactionsRefresh()
        
        // Start periodic check for transaction data changes
        startGraphRefresh()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()

        // Always force a full refresh when resuming - don't rely on flags
        refreshAllData()

        // Register receiver when activity resumes - both local and global
        try {
            val intentFilter = IntentFilter("com.example.financetracker.TRANSACTION_UPDATED")
            LocalBroadcastManager.getInstance(this).registerReceiver(transactionUpdateReceiver, intentFilter)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(transactionUpdateReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(transactionUpdateReceiver, intentFilter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Check if an immediate refresh is needed (coming back from a transaction)
        val immediateRefreshNeeded = sharedPreferences.getBoolean("immediate_refresh_needed", false)
        
        if (immediateRefreshNeeded) {
            // Force refresh all data immediately
            refreshAllData()
            
            // Clear the immediate refresh flag
            sharedPreferences.edit()
                .putBoolean("immediate_refresh_needed", false)
                .putBoolean("transaction_data_changed", false)
                .apply()
        }
        
        // Always update notification badge
        updateNotificationBadge()
    }

    override fun onPause() {
        super.onPause()
        // Unregister receiver when activity pauses to prevent leaks - try both
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(transactionUpdateReceiver)
            unregisterReceiver(transactionUpdateReceiver)
        } catch (e: Exception) {
            // Ignore if receivers weren't registered
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    private fun loadWalletsData() {
        val walletsJson = sharedPreferences.getString("wallets", null)

        if (walletsJson != null) {
            try {
                val jsonArray = JSONArray(walletsJson)

                // Update wallet cards
                updateWalletCards(jsonArray)

                // Update total balance display
                updateTotalBalanceDisplay()

            } catch (e: Exception) {
                e.printStackTrace()
                // Handle error loading wallet data
            }
        } else {
            // No wallets found, show 0 balance
            val balanceAmountTextView = findViewById<TextView>(R.id.balance_amount)
            balanceAmountTextView.text = currencyFormat.format(0)
        }
    }

    private fun updateTotalBalanceDisplay() {
        // Calculate and display the total balance from all wallets
        val balanceAmountTextView = findViewById<TextView>(R.id.balance_amount)
        val totalBalance = calculateTotalBalance()
        balanceAmountTextView.text = currencyFormat.format(totalBalance)
    }

    private fun calculateTotalBalance(): Double {
        var totalBalance = 0.0

        // Get wallets from SharedPreferences
        val walletsJson = sharedPreferences.getString("wallets", null)

        if (walletsJson != null) {
            try {
                val jsonArray = JSONArray(walletsJson)

                // Calculate total remaining amount across all wallets
                for (i in 0 until jsonArray.length()) {
                    val wallet = jsonArray.getJSONObject(i)
                    val remaining = wallet.getDouble("remaining")
                    totalBalance += remaining
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return totalBalance
    }

    private fun updateWalletCards(walletsArray: JSONArray) {
        // Get the container for wallet cards
        val walletsContainer = findViewById<LinearLayout>(R.id.homepage_wallets_container)

        // Clear existing wallet views
        walletsContainer.removeAllViews()

        // Set up 'See All' button
        val seeAllWallets = findViewById<TextView>(R.id.see_all_wallets)
        seeAllWallets.setOnClickListener {
            // Navigate to Budget screen when "See All" is clicked
            startActivity(Intent(this, Budget::class.java))
        }

        // If no wallets found, show a message
        if (walletsArray.length() == 0) {
            val noWalletsView = TextView(this)
            noWalletsView.text = "No wallets found. Create one in Budget section."
            noWalletsView.textSize = 16f
            noWalletsView.setPadding(16, 16, 16, 16)
            walletsContainer.addView(noWalletsView)
            return
        }

        // Loop through all wallets and add them to the container
        for (i in 0 until walletsArray.length()) {
            val wallet = walletsArray.getJSONObject(i)
            val walletName = wallet.getString("name")
            val remaining = wallet.getDouble("remaining")
            val initialAmount = wallet.getDouble("initialAmount")

            // Calculate progress percentage
            val progress = if (initialAmount > 0) {
                ((remaining / initialAmount) * 100).toInt()
            } else {
                0
            }

            // Inflate the wallet item layout
            val walletView = layoutInflater.inflate(
                R.layout.wallet_item_homepage,
                walletsContainer,
                false
            )

            // Set wallet data
            val nameTextView = walletView.findViewById<TextView>(R.id.homepage_wallet_name)
            val amountTextView = walletView.findViewById<TextView>(R.id.homepage_wallet_amount)
            val progressBar = walletView.findViewById<ProgressBar>(R.id.homepage_wallet_progress)

            nameTextView.text = walletName
            amountTextView.text = currencyFormat.format(remaining)
            progressBar.progress = progress

            // Add click listener to the wallet view to navigate to transactions
            walletView.setOnClickListener {
                val intent = Intent(this, Transactions::class.java)
                // Save the selected wallet to preferences so Transactions activity can load it
                sharedPreferences.edit()
                    .putString("current_wallet", walletName)
                    .apply()
                startActivity(intent)
            }

            // Add the wallet view to the container
            walletsContainer.addView(walletView)
        }
    }

    private fun initializeNotifications() {
        // Create notification channels
        val notificationHelper = NotificationHelper(this)

        // Check for alarm permissions on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isExactAlarmPermissionGranted()) {
                requestExactAlarmPermission()
            }
        }

        // Schedule periodic notifications
        val scheduler = NotificationScheduler()
        scheduler.scheduleDailyExpenseReminder(this)
        scheduler.scheduleMonthlyBackupReminder(this)
        scheduler.scheduleDailyExpenseSummary(this)  // Schedule daily expense summary at 11 PM

        // Initial budget check
        BudgetMonitor(this).checkAllWallets()
    }
    
    private fun isExactAlarmPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            return alarmManager.canScheduleExactAlarms()
        }
        return true
    }
    
    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        }
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Set the home item as selected by default
        bottomNavigation.selectedItemId = R.id.nav_home

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // If already on Homepage, no need to navigate, just scroll to top if possible
                    if (this::class.java != Homepage::class.java) {
                        startActivity(Intent(this, Homepage::class.java))
                        finish()
                    } else {
                        // Scroll to top logic here if needed
                        val scrollView = findViewById<NestedScrollView>(R.id.homepage_scroll_view)
                        scrollView?.smoothScrollTo(0, 0)
                    }
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

    private fun updateNotificationBadge() {
        val badge = findViewById<TextView>(R.id.notification_badge)
        val notificationPrefs = getSharedPreferences("notification_prefs", MODE_PRIVATE)
        val notificationsJson = notificationPrefs.getString("notifications", "[]")

        try {
            val jsonArray = JSONArray(notificationsJson)
            var unreadCount = 0

            for (i in 0 until jsonArray.length()) {
                val notification = jsonArray.getJSONObject(i)
                if (!notification.getBoolean("isRead")) {
                    unreadCount++
                }
            }

            if (unreadCount > 0) {
                badge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                badge.visibility = View.VISIBLE
            } else {
                badge.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            badge.visibility = View.GONE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            updateNotificationBadge()
        }
    }

    private fun setupExpenseGraph() {
        expenseChart = findViewById(R.id.expense_chart)
        val tooltip = findViewById<CardView>(R.id.expense_tooltip)
        val tooltipAmount = findViewById<TextView>(R.id.tooltip_amount)
        val tooltipDate = findViewById<TextView>(R.id.tooltip_date)

        // Update the expense section title to reflect all wallets
        val expenseSectionTitle = findViewById<TextView>(R.id.expense_section_title)
        expenseSectionTitle.text = "All Wallets Expenses"

        // Hide tooltip initially
        tooltip.visibility = View.GONE

        // Set up the chart appearance
        with(expenseChart) {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            maxHighlightDistance = 300f

            // Set up X axis
            val xAxis = xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.textColor = Color.parseColor("#7A7A7A")
            xAxis.textSize = 12f

            // Set up Y axis
            val leftAxis = axisLeft
            leftAxis.setDrawGridLines(true)
            leftAxis.gridColor = Color.parseColor("#EEEEEE")
            leftAxis.textColor = Color.parseColor("#7A7A7A")
            leftAxis.textSize = 12f

            // FIX: Invert Y axis values to display expenses in positive direction
            leftAxis.isInverted = true

            leftAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "$${Math.abs(value.toInt())}"
                }
            }

            // Disable right Y axis
            axisRight.isEnabled = false

            // Set up legend
            legend.isEnabled = false

            // Add extra margin
            setExtraOffsets(10f, 10f, 10f, 10f)

            // Set up the data
            setWeeklyExpenseData()

            // Set up the highlight listener
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e != null) {
                        // Get the selected day index
                        val dayIndex = h?.x?.toInt() ?: 0

                        // Day of week labels
                        val dayLabels = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

                        // Set tooltip text
                        tooltipAmount.text = "$${String.format("%.2f", Math.abs(e.y))}"

                        // Get current date
                        val calendar = Calendar.getInstance()
                        // Go to Sunday of this week
                        calendar.firstDayOfWeek = Calendar.SUNDAY
                        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                        // Move to the highlighted day
                        calendar.add(Calendar.DAY_OF_WEEK, dayIndex)

                        // Format date for tooltip
                        val dateFormat = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault())
                        tooltipDate.text = dateFormat.format(calendar.time)

                        // Show tooltip
                        tooltip.visibility = View.VISIBLE
                    } else {
                        tooltip.visibility = View.GONE
                    }
                }

                override fun onNothingSelected() {
                    tooltip.visibility = View.GONE
                }
            })
        }
    }

    private fun setWeeklyExpenseData() {
        val entries = ArrayList<Entry>()

        // Array to hold expense totals for each day of the week
        val weeklyData = FloatArray(7) { 0f }
        // Array to hold income totals for each day of the week
        val weeklyIncome = FloatArray(7) { 0f }
        // Track expenses by wallet
        val walletExpenses = mutableMapOf<String, Float>()

        // Get the current week's date range
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.SUNDAY

        // Set to start of current week (Sunday)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        val startDate = calendar.time

        // Move to end of week (Saturday)
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endDate = calendar.time

        // Date format used in transactions
        val transactionDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // Track overall totals
        var totalExpense = 0f
        var totalIncome = 0f
        var walletCount = 0

        // Process all wallets to get transactions
        val walletsJson = sharedPreferences.getString("wallets", null)

        if (walletsJson != null) {
            try {
                val jsonArray = JSONArray(walletsJson)
                walletCount = jsonArray.length()

                // Go through each wallet
                for (i in 0 until jsonArray.length()) {
                    val wallet = jsonArray.getJSONObject(i)
                    val walletName = wallet.getString("name")
                    var walletTotalExpense = 0f

                    // Get transactions for this wallet
                    val transactionsKey = "transactions_$walletName"
                    val transactionsJson = sharedPreferences.getString(transactionsKey, null)

                    if (transactionsJson != null) {
                        val transactionsArray = JSONArray(transactionsJson)

                        // Process each transaction
                        for (j in 0 until transactionsArray.length()) {
                            val transaction = transactionsArray.getJSONObject(j)
                            val dateStr = transaction.getString("date")
                            val isIncome = transaction.getBoolean("isIncome")
                            val amount = transaction.getDouble("amount").toFloat()

                            try {
                                val transactionDate = transactionDateFormat.parse(dateStr)

                                // Check if transaction is within current week
                                if (transactionDate != null &&
                                    !transactionDate.before(startDate) &&
                                    !transactionDate.after(endDate)) {

                                    // Get day of week (0 = Sunday, 1 = Monday, etc.)
                                    calendar.time = transactionDate
                                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // Convert to 0-based index

                                    if (isIncome) {
                                        // Add income amount to the appropriate day
                                        weeklyIncome[dayOfWeek] += amount
                                        totalIncome += amount
                                    } else {
                                        // Add expense amount to the appropriate day (negative value)
                                        weeklyData[dayOfWeek] -= amount // Subtract to make it negative
                                        totalExpense += amount
                                        walletTotalExpense += amount
                                    }
                                }
                            } catch (e: Exception) {
                                // Handle date parsing errors
                                e.printStackTrace()
                            }
                        }
                    }
                    
                    // Store this wallet's total expenses
                    walletExpenses[walletName] = walletTotalExpense
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Set up the expense and income info display
        val expenseTotal = findViewById<TextView>(R.id.expense_total)
        val expenseInfo = findViewById<LinearLayout>(R.id.expense_info)
        expenseTotal.text = "-$${String.format("%.2f", totalExpense)}"
        
        // Update subtitle with wallet count information
        val expenseSectionSubtitle = findViewById<TextView>(R.id.expense_section_subtitle)
        if (walletCount > 0) {
            expenseSectionSubtitle.text = "Weekly expenses from all $walletCount ${if (walletCount == 1) "wallet" else "wallets"}"
        } else {
            expenseSectionSubtitle.text = "No wallets found. Create one in Budget section."
        }

        // Update wallet expense distribution
        updateWalletDistribution(walletExpenses, totalExpense)

        // Update the total balance display
        updateTotalBalanceDisplay()

        // Day of week labels
        val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        // Create entries
        for (i in weeklyData.indices) {
            entries.add(Entry(i.toFloat(), weeklyData[i]))
        }

        // Find the day with highest expense for highlighting
        var maxExpenseDay = 0
        var maxExpenseAmount = 0f
        for (i in weeklyData.indices) {
            if (Math.abs(weeklyData[i]) > Math.abs(maxExpenseAmount)) {
                maxExpenseAmount = weeklyData[i]
                maxExpenseDay = i
            }
        }

        // Create dataset
        val dataSet = LineDataSet(entries, "Daily Expenses")

        // Set dataset appearance
        with(dataSet) {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
            setDrawFilled(true)
            setDrawCircles(true)
            setDrawCircleHole(true)
            lineWidth = 3f
            circleRadius = 5f
            circleHoleRadius = 2.5f

            // Set highlighted point appearance
            highlightLineWidth = 2f
            highLightColor = Color.parseColor("#4CAF50")
            setDrawHorizontalHighlightIndicator(false)

            // Set colors
            color = Color.parseColor("#4CAF50")
            fillColor = Color.parseColor("#804CAF50") // Semi-transparent green
            circleColors = arrayListOf(Color.parseColor("#4CAF50"))
            circleHoleColor = Color.WHITE

            // Set gradient fill
            val startColor = Color.parseColor("#4CAF50") // Green for lower values
            val endColor = Color.parseColor("#E57373")   // Red for higher values
            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP, // Changed direction due to inverted Y axis
                intArrayOf(startColor, endColor)
            )
            dataSet.fillDrawable = gradientDrawable

            // Customize value appearance
            setDrawValues(false)

            // Set highlighted values - highlight the day with highest expense
            if (Math.abs(maxExpenseAmount) > 0) {
                setCircleColor(Color.RED, maxExpenseDay)
            }
        }

        // Set X-axis labels
        expenseChart.xAxis.valueFormatter = IndexAxisValueFormatter(dayLabels)

        // Create and set the data
        val lineData = LineData(dataSet)
        expenseChart.data = lineData

        // Highlight the day with highest expense
        if (Math.abs(maxExpenseAmount) > 0) {
            expenseChart.highlightValue(maxExpenseDay.toFloat(), 0)
        }

        // Refresh the chart
        expenseChart.invalidate()
    }

    private fun updateWalletDistribution(walletExpenses: Map<String, Float>, totalExpense: Float) {
        // Get the container for wallet distribution
        val distributionContainer = findViewById<LinearLayout>(R.id.wallet_distribution_container)
        
        // Clear existing distribution views
        distributionContainer.removeAllViews()
        
        // Show or hide the distribution section based on if there are any expenses
        val distributionTitle = findViewById<TextView>(R.id.wallet_distribution_title)
        
        if (totalExpense <= 0f || walletExpenses.isEmpty()) {
            distributionTitle.visibility = View.GONE
            return
        } else {
            distributionTitle.visibility = View.VISIBLE
        }
        
        // Sort wallets by expense amount (highest first)
        val sortedWallets = walletExpenses.entries
            .filter { it.value > 0f }
            .sortedByDescending { it.value }
        
        // Add distribution items for each wallet
        for (walletEntry in sortedWallets) {
            val walletName = walletEntry.key
            val expenseAmount = walletEntry.value
            
            // Calculate percentage of total expenses
            val percentage = if (totalExpense > 0f) {
                (expenseAmount / totalExpense) * 100f
            } else {
                0f
            }
            
            // Inflate the distribution item layout
            val distributionView = layoutInflater.inflate(
                R.layout.wallet_expense_distribution_item,
                distributionContainer,
                false
            )
            
            // Set wallet data
            val nameTextView = distributionView.findViewById<TextView>(R.id.wallet_name)
            val progressBar = distributionView.findViewById<ProgressBar>(R.id.wallet_progress)
            val percentageTextView = distributionView.findViewById<TextView>(R.id.expense_percentage)
            val amountTextView = distributionView.findViewById<TextView>(R.id.expense_amount)
            
            nameTextView.text = walletName
            progressBar.progress = percentage.toInt()
            percentageTextView.text = "${percentage.toInt()}%"
            amountTextView.text = currencyFormat.format(expenseAmount)
            
            // Set different colors based on percentage
            val color = when {
                percentage >= 70 -> Color.parseColor("#E57373") // Red for high percentage
                percentage >= 40 -> Color.parseColor("#FFB74D") // Orange for medium
                else -> Color.parseColor("#4CAF50") // Green for low
            }
            
            progressBar.progressTintList = ColorStateList.valueOf(color)
            percentageTextView.setTextColor(color)
            
            // Add the distribution view to the container
            distributionContainer.addView(distributionView)
        }
    }

    // Helper function to set a specific circle color at a specific index
    private fun LineDataSet.setCircleColor(color: Int, index: Int) {
        val colors = ArrayList<Int>()
        for (i in 0 until entryCount) {
            colors.add(if (i == index) color else Color.parseColor("#4CAF50"))
        }
        circleColors = colors
    }

    private fun loadRecentTransactions() {
        // Clear existing transactions
        recentTransactionsContainer.removeAllViews()
        
        // Style the recent transactions container
        recentTransactionsContainer.setPadding(8, 8, 8, 8)

        // Get references to new UI elements
        val noTransactionsText = findViewById<TextView>(R.id.no_transactions_text)
        val transactionsLoading = findViewById<ProgressBar>(R.id.transactions_loading)
        val transactionsSubtitle = findViewById<TextView>(R.id.recent_transactions_subtitle)

        // Show loading indicator
        transactionsLoading.visibility = View.VISIBLE
        noTransactionsText.visibility = View.GONE
        recentTransactionsContainer.visibility = View.GONE

        // Create a list to store all transactions from all wallets
        val allTransactions = mutableListOf<Pair<JSONObject, String>>() // Pair of (transaction, walletName)

        // Get wallets from SharedPreferences
        val walletsJson = sharedPreferences.getString("wallets", null)

        if (walletsJson != null) {
            try {
                val jsonArray = JSONArray(walletsJson)
                val walletCount = jsonArray.length()

                // Update subtitle with wallet count
                transactionsSubtitle.text = "From all ${if (walletCount == 1) "wallet" else "$walletCount wallets"}"

                // Go through each wallet
                for (i in 0 until jsonArray.length()) {
                    val wallet = jsonArray.getJSONObject(i)
                    val walletName = wallet.getString("name")

                    // Get transactions for this wallet
                    val transactionsKey = "transactions_$walletName"
                    val transactionsJson = sharedPreferences.getString(transactionsKey, null)

                    if (transactionsJson != null) {
                        val transactionsArray = JSONArray(transactionsJson)

                        // Add each transaction to the list
                        for (j in 0 until transactionsArray.length()) {
                            val transaction = transactionsArray.getJSONObject(j)
                            allTransactions.add(Pair(transaction, walletName))
                        }
                    }
                }

                // Sort transactions by timestamp (newest first)
                allTransactions.sortWith(compareByDescending {
                    it.first.optLong("timestamp", 0L)
                })

                // Hide loading indicator
                transactionsLoading.visibility = View.GONE
                
                // If there are no transactions, show the placeholder
                if (allTransactions.isEmpty()) {
                    noTransactionsText.visibility = View.VISIBLE
                    recentTransactionsContainer.visibility = View.GONE
                } else {
                    noTransactionsText.visibility = View.GONE
                    recentTransactionsContainer.visibility = View.VISIBLE
                    
                    // Show the most recent 5 transactions
                    val transactionsToShow = allTransactions.take(5)

                    // Add transactions to the UI
                    for (transactionPair in transactionsToShow) {
                        addTransactionToUI(transactionPair.first, transactionPair.second)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                
                // Hide loading indicator
                transactionsLoading.visibility = View.GONE

                // Show error message
                noTransactionsText.text = "Could not load transactions"
                noTransactionsText.visibility = View.VISIBLE
                recentTransactionsContainer.visibility = View.GONE
            }
        } else {
            // Hide loading indicator
            transactionsLoading.visibility = View.GONE
            
            // No wallets found, show a message
            noTransactionsText.text = "No wallets found. Create one in Budget section."
            noTransactionsText.visibility = View.VISIBLE
            recentTransactionsContainer.visibility = View.GONE
        }
    }

    private fun addTransactionToUI(transaction: JSONObject, walletName: String) {
        // Inflate transaction item layout using the homepage-specific layout
        val transactionView = LayoutInflater.from(this)
            .inflate(R.layout.homepage_transaction_item, recentTransactionsContainer, false)

        // Get data from transaction
        val name = transaction.getString("name")
        val amount = transaction.getDouble("amount")
        val isIncome = transaction.getBoolean("isIncome")
        val category = transaction.getString("category")
        val date = transaction.getString("date")
        val timestamp = transaction.optLong("timestamp", 0L)
        
        // Store the timestamp as a tag for future updates
        transactionView.tag = timestamp

        // Set data to views
        val nameTextView = transactionView.findViewById<TextView>(R.id.transaction_name)
        val categoryTextView = transactionView.findViewById<TextView>(R.id.transaction_category)
        val walletTextView = transactionView.findViewById<TextView>(R.id.transaction_wallet)
        val amountTextView = transactionView.findViewById<TextView>(R.id.transaction_amount)
        val categoryIcon = transactionView.findViewById<ImageView>(R.id.transaction_category_icon)
        val dateTextView = transactionView.findViewById<TextView>(R.id.transaction_date)

        nameTextView.text = name
        categoryTextView.text = category
        walletTextView.text = walletName

        // Format and display date and time with relative timestamp
        var formattedDate = ""
        var formattedTime = ""
        var relativeTime = ""
        
        try {
            // Parse the date from the transaction
            val parsedDateTime = dateFormat.parse(date)
            formattedDate = displayDateFormat.format(parsedDateTime!!)
            formattedTime = displayTimeFormat.format(parsedDateTime)
            
            // Calculate relative time
            if (timestamp > 0) {
                val now = System.currentTimeMillis()
                val diffMillis = now - timestamp
                
                // Convert to appropriate time format
                relativeTime = when {
                    diffMillis < 60000 -> "Just now" // Less than a minute
                    diffMillis < 3600000 -> "${(diffMillis / 60000).toInt()} min ago" // Less than an hour
                    diffMillis < 86400000 -> "${(diffMillis / 3600000).toInt()} hours ago" // Less than a day
                    diffMillis < 172800000 -> "Yesterday" // Less than 2 days
                    diffMillis < 604800000 -> "${(diffMillis / 86400000).toInt()} days ago" // Less than a week
                    else -> formattedDate // More than a week
                }
                
                // If it's more than a week, just use the normal date display
                if (diffMillis >= 604800000) {
                    dateTextView.text = "$formattedDate • $formattedTime"
                } else {
                    dateTextView.text = "$relativeTime • $formattedTime"
                }
            } else {
                // Fallback if no timestamp
                dateTextView.text = "$formattedDate • $formattedTime"
            }
        } catch (e: Exception) {
            // Handle legacy format (without time)
            try {
                val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)
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
        
        // Set custom background color for category icon based on category
        val iconContainer = transactionView.findViewById<CardView>(R.id.icon_container)
        
        // Assign different background colors based on category
        when (category) {
            "Essentials" -> iconContainer.setCardBackgroundColor(Color.parseColor("#E8F5E9")) // Light green
            "Savings" -> iconContainer.setCardBackgroundColor(Color.parseColor("#E3F2FD")) // Light blue
            "Pets" -> iconContainer.setCardBackgroundColor(Color.parseColor("#FFF3E0")) // Light orange
            "Health" -> iconContainer.setCardBackgroundColor(Color.parseColor("#E1F5FE")) // Light sky blue
            "Donations" -> iconContainer.setCardBackgroundColor(Color.parseColor("#F3E5F5")) // Light purple
            "Entertainment" -> iconContainer.setCardBackgroundColor(Color.parseColor("#FFEBEE")) // Light red
            "Food" -> iconContainer.setCardBackgroundColor(Color.parseColor("#FFF8E1")) // Light amber
            else -> iconContainer.setCardBackgroundColor(Color.parseColor("#E8F5E9")) // Default light green
        }

        // Make entire transaction card clickable to view transaction details
        transactionView.setOnClickListener {
            val intent = Intent(this, Transactions::class.java)
            startActivity(intent)
        }

        // Add transaction view to container
        recentTransactionsContainer.addView(transactionView)
    }

    private fun startTransactionsRefresh() {
        // Define the refresh runnable
        refreshRunnable = object : Runnable {
            override fun run() {
                // Update relative timestamps without full reload
                updateTransactionTimestamps()
                
                // Schedule the next refresh
                refreshHandler.postDelayed(this, 60000) // Refresh every minute
            }
        }
        
        // Start the periodic refresh
        refreshHandler.post(refreshRunnable!!)
    }
    
    private fun stopTransactionsRefresh() {
        refreshRunnable?.let {
            refreshHandler.removeCallbacks(it)
        }
    }
    
    private fun updateTransactionTimestamps() {
        // Only update if we're showing transactions
        if (recentTransactionsContainer.visibility != View.VISIBLE || recentTransactionsContainer.childCount == 0) {
            return
        }
        
        // Iterate through the transaction views and update timestamps
        for (i in 0 until recentTransactionsContainer.childCount) {
            val transactionView = recentTransactionsContainer.getChildAt(i)
            val dateTextView = transactionView.findViewById<TextView>(R.id.transaction_date)
            
            // Try to extract timestamp from the tag
            val timestamp = transactionView.tag as? Long ?: continue
            
            // Calculate relative time
            val now = System.currentTimeMillis()
            val diffMillis = now - timestamp
            
            // Get the formatted time part
            val displayText = dateTextView.text.toString()
            val timePart = if (displayText.contains("•")) {
                "• ${displayText.split("•")[1].trim()}"
            } else {
                ""
            }
            
            // Update the relative time part
            val relativeTime = when {
                diffMillis < 60000 -> "Just now" // Less than a minute
                diffMillis < 3600000 -> "${(diffMillis / 60000).toInt()} min ago" // Less than an hour
                diffMillis < 86400000 -> "${(diffMillis / 3600000).toInt()} hours ago" // Less than a day
                diffMillis < 172800000 -> "Yesterday" // Less than 2 days
                diffMillis < 604800000 -> "${(diffMillis / 86400000).toInt()} days ago" // Less than a week
                else -> {
                    // If more than a week, we need the full date which requires a reload
                    continue
                }
            }
            
            // Update the text view
            dateTextView.text = "$relativeTime $timePart"
        }
    }
    
    /**
     * Check if transaction data has changed by checking the flag in SharedPreferences
     * If changed, update the expense graph and recent transactions
     */
    private fun checkTransactionDataChanges() {
        // Get the transaction data changed flag and last update timestamp
        val dataChanged = sharedPreferences.getBoolean("transaction_data_changed", false)
        val lastUpdateTimestamp = sharedPreferences.getLong("last_transaction_update", 0)
        
        // Only update if data has changed and the update is newer than our last check
        if (dataChanged && lastUpdateTimestamp > lastGraphCheckTimestamp) {
            android.util.Log.d("Homepage", "Transaction data changed detected - refreshing UI")
            
            // Update our last check timestamp first to prevent missing updates
            lastGraphCheckTimestamp = System.currentTimeMillis()
            
            // Use the comprehensive refresh method
            refreshAllData()
            
            // Reset the flag AFTER all UI updates have been processed
            // But do NOT use a post-delayed as it can cause race conditions
            sharedPreferences.edit()
                .putBoolean("transaction_data_changed", false)
                .apply()
        }
    }
    
    /**
     * Start a periodic check for transaction data changes to update the graph
     * in real-time when transactions occur in other activities
     */
    private fun startGraphRefresh() {
        // Define the refresh runnable for graph updates
        graphRefreshRunnable = object : Runnable {
            override fun run() {
                // Check if transaction data has changed
                checkTransactionDataChanges()
                
                // Schedule the next check - use an even shorter interval for more responsive updates
                refreshHandler.postDelayed(this, 100) // Check every 100ms for truly real-time updates
            }
        }
        
        // Start the periodic check
        refreshHandler.post(graphRefreshRunnable!!)
    }
    
    private fun stopGraphRefresh() {
        graphRefreshRunnable?.let {
            refreshHandler.removeCallbacks(it)
        }
    }
    
    /**
     * Refresh all data on the homepage including the expense graph, wallet cards, 
     * transactions and total balance display. This is a comprehensive refresh method.
     */
    private fun refreshAllData() {
        android.util.Log.d("Homepage", "Refreshing all homepage data")
        
        // Force refresh the graph immediately
        setupExpenseGraph()
        
        // Update total balance
        updateTotalBalanceDisplay()
        
        // Refresh wallet cards
        val walletsJson = sharedPreferences.getString("wallets", null)
        if (walletsJson != null) {
            try {
                val jsonArray = JSONArray(walletsJson)
                updateWalletCards(jsonArray)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Refresh recent transactions
        loadRecentTransactions()
        
        // Force a redraw of the entire view
        val mainLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.main)
        mainLayout.invalidate()
        
        // Update our last check timestamp
        lastGraphCheckTimestamp = System.currentTimeMillis()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop the refresh handlers when activity is destroyed
        stopTransactionsRefresh()
        stopGraphRefresh()
        
        // Ensure the receiver is unregistered from both local and global
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(transactionUpdateReceiver)
            unregisterReceiver(transactionUpdateReceiver)
        } catch (e: Exception) {
            // Receiver might already be unregistered
        }
    }
}