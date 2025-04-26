package com.example.financetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(
    private var notifications: List<NotificationItem>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.notification_title)
        val message: TextView = view.findViewById(R.id.notification_message)
        val time: TextView = view.findViewById(R.id.notification_time)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        
        holder.title.text = notification.title
        holder.message.text = notification.message
        holder.time.text = formatTimestamp(notification.timestamp)

        // Set background color based on read status
        holder.itemView.setBackgroundColor(
            holder.itemView.context.getColor(
                if (notification.isRead) android.R.color.white
                else R.color.unread_notification_background
            )
        )

        // Set up click listener for the entire item
        holder.itemView.setOnClickListener {
            onItemClick(position)
        }

        // Set up delete button click listener
        holder.deleteButton.setOnClickListener {
            deleteNotification(position)
        }
    }

    override fun getItemCount() = notifications.size

    fun updateNotifications(newNotifications: List<NotificationItem>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun deleteNotification(position: Int) {
        if (position < notifications.size) {
            val mutableList = notifications.toMutableList()
            mutableList.removeAt(position)
            notifications = mutableList
            notifyItemRemoved(position)
        }
    }
} 