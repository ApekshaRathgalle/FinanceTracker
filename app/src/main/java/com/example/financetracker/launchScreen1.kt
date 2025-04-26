package com.example.financetracker

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class launchScreen1 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_launch_screen1)

        // Find the next button by its ID
        val nextButton = findViewById<ImageButton>(R.id.next_button2)

        // Set click listener on the next button
        nextButton.setOnClickListener {
            // Create intent to navigate to the second launch screen
            val intent = Intent(this, launchScreen2::class.java)
            startActivity(intent)


        }
    }
}