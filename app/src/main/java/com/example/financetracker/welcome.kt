package com.example.financetracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class welcome : AppCompatActivity() {

    private lateinit var signUpButton: Button
    private lateinit var loginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome_page)

        // Initialize buttons
        signUpButton = findViewById(R.id.signUpButton)
        loginButton = findViewById(R.id.loginButton)

        // Set initial styles
        setupButtonStyles()

        // Set click listener for signup button with visual feedback
        signUpButton.setOnClickListener {
            // Apply pressed state visually
            animateButtonPress(signUpButton)

            // Navigate to SignUpActivity after a short delay (for visual effect)
            signUpButton.postDelayed({
                val intent = Intent(this, Signup::class.java)
                startActivity(intent)
            }, 150)
        }

        // Set click listener for login button with visual feedback
        loginButton.setOnClickListener {
            // Apply pressed state visually
            animateButtonPress(loginButton)

            // Navigate to LoginActivity after a short delay (for visual effect)
            loginButton.postDelayed({
                val intent = Intent(this, Login::class.java)
                startActivity(intent)
            }, 150)
        }

        // Set up edge-to-edge content
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupButtonStyles() {
        // Set signup button style (green background with white text)
        signUpButton.setBackgroundResource(R.drawable.primary_button_background)
        signUpButton.setTextColor(Color.WHITE)

        // Set login button style (white background with black text)
        loginButton.setBackgroundResource(R.drawable.secondary_button_background)
        loginButton.setTextColor(Color.BLACK)

        // Add touch listeners for additional visual feedback
        addTouchFeedback(signUpButton)
        addTouchFeedback(loginButton)
    }

    private fun addTouchFeedback(button: Button) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Scale down slightly when pressed
                    v.scaleX = 0.95f
                    v.scaleY = 0.95f
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Return to normal size when released
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                    false
                }
                else -> false
            }
        }
    }

    private fun animateButtonPress(button: Button) {
        // Scale down
        button.animate().scaleX(0.95f).scaleY(0.95f).setDuration(50).withEndAction {
            // Scale back up
            button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100)
        }
    }
}