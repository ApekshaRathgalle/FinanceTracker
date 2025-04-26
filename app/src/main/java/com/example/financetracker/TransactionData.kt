package com.example.financetracker

data class TransactionData(
    val name: String,
    val amount: Double,
    val isIncome: Boolean,
    val category: String,
    val date: String,
    val timestamp: Long = 0
) 