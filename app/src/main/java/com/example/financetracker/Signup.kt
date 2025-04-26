package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class Signup : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var termsCheckBox: CheckBox
    private lateinit var signupButton: Button
    private lateinit var alreadyHaveAccountText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_signup)

        // Initialize views
        emailEditText = findViewById(R.id.emailInput)
        passwordEditText = findViewById(R.id.passwordInput)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordInput)
        termsCheckBox = findViewById(R.id.termsCheckbox)
        signupButton = findViewById(R.id.signUpButton)
        alreadyHaveAccountText = findViewById(R.id.alreadyHaveAccount)

        // Button click for Signup
        signupButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()
            val isTermsChecked = termsCheckBox.isChecked

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            } else if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            } else if (!isTermsChecked) {
                Toast.makeText(this, "Please agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
            } else {
                saveUserCredentials(email, password)
                Toast.makeText(this, "Signup successful!", Toast.LENGTH_SHORT).show()

                // After signup, navigate directly to Homepage
                val intent = Intent(this, Homepage::class.java)
                startActivity(intent)
                finish()
            }
        }


        alreadyHaveAccountText.setOnClickListener {
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun saveUserCredentials(email: String, password: String) {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString("user_email", email)
        editor.putString("user_password", password)
        editor.putBoolean("is_logged_in", true) // Set login status to true
        editor.apply() // save asynchronously
    }
}
