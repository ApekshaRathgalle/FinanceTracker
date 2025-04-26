package com.example.financetracker

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionReportAdapter(
    private var transactions: List<TransactionData>,
    private val onDeleteClicked: ((TransactionData) -> Unit)? = null
) : 
    RecyclerView.Adapter<TransactionReportAdapter.TransactionViewHolder>() {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
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

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.transaction_name)
        val categoryTextView: TextView = itemView.findViewById(R.id.transaction_category)
        val amountTextView: TextView = itemView.findViewById(R.id.transaction_amount)
        val categoryIcon: ImageView = itemView.findViewById(R.id.transaction_category_icon)
        val dateTextView: TextView = itemView.findViewById(R.id.transaction_date)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete_transaction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.transaction_item, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        
        holder.nameTextView.text = transaction.name
        holder.categoryTextView.text = transaction.category

        // Format amount and set color based on transaction type
        val amountFormatted = currencyFormat.format(transaction.amount)
        holder.amountTextView.text = if (transaction.isIncome) "+$amountFormatted" else "-$amountFormatted"
        
        val amountColor = if (transaction.isIncome) 
            R.color.blue
        else 
            R.color.red
        
        holder.amountTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, amountColor))

        // Set category icon if available
        categoryIcons[transaction.category]?.let { iconResId ->
            holder.categoryIcon.setImageResource(iconResId)
        }

        // Parse and format date
        try {
            val parsedDateTime = dateFormat.parse(transaction.date)
            if (parsedDateTime != null) {
                val formattedDate = displayDateFormat.format(parsedDateTime)
                val formattedTime = displayTimeFormat.format(parsedDateTime)
                holder.dateTextView.text = "$formattedDate • $formattedTime"
            } else {
                holder.dateTextView.text = transaction.date
            }
        } catch (e: Exception) {
            holder.dateTextView.text = transaction.date
        }
        
        // Set up delete button click listener
        if (onDeleteClicked != null) {
            holder.deleteButton.visibility = View.VISIBLE
            holder.deleteButton.setOnClickListener {
                // Show confirmation dialog before deleting
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Delete Transaction")
                    .setMessage("Are you sure you want to delete this transaction?")
                    .setPositiveButton("Delete") { _, _ ->
                        onDeleteClicked.invoke(transaction)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            holder.deleteButton.visibility = View.GONE
        }
    }

    override fun getItemCount() = transactions.size

    fun updateTransactions(newTransactions: List<TransactionData>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
} 