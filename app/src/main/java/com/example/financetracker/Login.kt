package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageView
import android.text.method.PasswordTransformationMethod
import android.text.method.HideReturnsTransformationMethod

class Login : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var continueButton: Button
    private lateinit var dontHaveAccountText: TextView
    private lateinit var passwordVisibilityToggle: ImageView
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // Initialize views
        emailEditText = findViewById(R.id.emailInput)
        passwordEditText = findViewById(R.id.passwordInput)
        continueButton = findViewById(R.id.continueButton)
        dontHaveAccountText = findViewById(R.id.dontHaveAccount)
        passwordVisibilityToggle = findViewById(R.id.passwordVisibility)

        // Setup password visibility toggle
        passwordVisibilityToggle.setOnClickListener {
            togglePasswordVisibility()
        }

        continueButton.setOnClickListener {
            val enteredEmail = emailEditText.text.toString().trim()
            val enteredPassword = passwordEditText.text.toString().trim()

            if (enteredEmail.isEmpty() || enteredPassword.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
            } else {
                loginUser(enteredEmail, enteredPassword)
            }
        }

        dontHaveAccountText.setOnClickListener {
            val intent = Intent(this, Signup::class.java)
            startActivity(intent)
            finish() // Close Login screen
        }
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        
        if (isPasswordVisible) {
            // Show password
            passwordEditText.transformationMethod = HideReturnsTransformationMethod.getInstance()
            passwordVisibilityToggle.setImageResource(R.drawable.ic_visibility_off)
        } else {
            // Hide password
            passwordEditText.transformationMethod = PasswordTransformationMethod.getInstance()
            passwordVisibilityToggle.setImageResource(R.drawable.ic_visibility)
        }
        
        // Maintain cursor position
        passwordEditText.setSelection(passwordEditText.text.length)
    }

    private fun loginUser(email: String, password: String) {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val savedEmail = sharedPref.getString("user_email", null)
        val savedPassword = sharedPref.getString("user_password", null)

        if (email == savedEmail && password == savedPassword) {
            // Set logged in status to true
            sharedPref.edit().putBoolean("is_logged_in", true).apply()
            
            Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, Homepage::class.java)
            startActivity(intent)
            finish() // Close Login screen

        } else {
            Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
        }
    }
}
