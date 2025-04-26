package com.example.financetracker

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject

class NotificationsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var adapter: NotificationAdapter
    private var notifications = mutableListOf<NotificationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Notifications"

        // Initialize views
        recyclerView = findViewById(R.id.notifications_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        tabLayout = findViewById(R.id.tab_layout)

        // Set up RecyclerView
        adapter = NotificationAdapter(notifications) { position ->
            markNotificationAsRead(position)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Set up TabLayout
        setupTabLayout()

        // Load notifications
        loadNotifications()
    }

    private fun setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText("All"))
        tabLayout.addTab(tabLayout.newTab().setText("Unread"))
        tabLayout.addTab(tabLayout.newTab().setText("Read"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                filterNotifications(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun loadNotifications() {
        val sharedPreferences = getSharedPreferences("notification_prefs", MODE_PRIVATE)
        val notificationsJson = sharedPreferences.getString("notifications", "[]")
        
        notifications.clear()
        try {
            val jsonArray = JSONArray(notificationsJson)
            for (i in 0 until jsonArray.length()) {
                val notification = jsonArray.getJSONObject(i)
                notifications.add(
                    NotificationItem(
                        id = notification.getInt("id"),
                        title = notification.getString("title"),
                        message = notification.getString("message"),
                        timestamp = notification.getLong("timestamp"),
                        isRead = notification.getBoolean("isRead")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort notifications by timestamp (newest first)
        notifications.sortByDescending { it.timestamp }
        
        // Show appropriate view
        updateEmptyView()
        adapter.notifyDataSetChanged()
    }

    private fun filterNotifications(tabPosition: Int) {
        val filteredList = when (tabPosition) {
            0 -> notifications // All
            1 -> notifications.filter { !it.isRead } // Unread
            2 -> notifications.filter { it.isRead } // Read
            else -> notifications
        }
        
        adapter.updateNotifications(filteredList)
        updateEmptyView(filteredList.isEmpty())
    }

    private fun markNotificationAsRead(position: Int) {
        if (position < notifications.size) {
            val notification = notifications[position]
            if (!notification.isRead) {
                notification.isRead = true
                saveNotifications()
                adapter.notifyItemChanged(position)
            }
        }
    }

    private fun saveNotifications() {
        val sharedPreferences = getSharedPreferences("notification_prefs", MODE_PRIVATE)
        val jsonArray = JSONArray()
        
        notifications.forEach { notification ->
            val jsonObject = JSONObject().apply {
                put("id", notification.id)
                put("title", notification.title)
                put("message", notification.message)
                put("timestamp", notification.timestamp)
                put("isRead", notification.isRead)
            }
            jsonArray.put(jsonObject)
        }
        
        sharedPreferences.edit()
            .putString("notifications", jsonArray.toString())
            .apply()
    }

    private fun updateEmptyView(isEmpty: Boolean = notifications.isEmpty()) {
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(RESULT_OK)
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        setResult(RESULT_OK)
        super.onBackPressed()
    }
}

data class NotificationItem(
    val id: Int,
    val title: String,
    val message: String,
    val timestamp: Long,
    var isRead: Boolean
) 