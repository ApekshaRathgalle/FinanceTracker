package com.example.financetracker

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class launchScreen3 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_launch_screen3)


        // Find the next button by its ID
        val nextButton = findViewById<ImageButton>(R.id.next_button3)

        // Set click listener on the next button
        nextButton.setOnClickListener {
            // Create intent to navigate to the second launch screen
            val intent = Intent(this, welcome::class.java)
            startActivity(intent)


        }
    }
}