package com.example.financetracker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import android.widget.LinearLayout

class Profile : AppCompatActivity() {
    private lateinit var profileImage: ImageView
    private lateinit var profilePrefs: SharedPreferences
    private lateinit var userPrefs: SharedPreferences
    private lateinit var userEmail: TextView
    private lateinit var notificationsSwitch: SwitchCompat
    
    // Activity result for image selection
    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            data?.data?.let { uri ->
                try {
                    // Set the selected image to the profile image view
                    profileImage.setImageURI(uri)
                    
                    // Save the image to SharedPreferences
                    saveImageToPreferences(uri)
                    
                    Toast.makeText(this, "Profile image updated", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("Profile", "Error setting image: ${e.message}")
                    Toast.makeText(this, "Failed to update profile image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        // Initialize SharedPreferences
        profilePrefs = getSharedPreferences("profile_prefs", MODE_PRIVATE)
        userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        
        // Initialize views
        profileImage = findViewById(R.id.profile_image)
        userEmail = findViewById(R.id.user_email)
        notificationsSwitch = findViewById(R.id.notifications_switch)
        
        // Set user email from UserPrefs where it's actually stored during login/signup
        val email = userPrefs.getString("user_email", "user@example.com")
        userEmail.text = email
        
        // Also save the email to profile_prefs for consistency
        if (email != null && email != "user@example.com") {
            profilePrefs.edit().putString("user_email", email).apply()
        }
        
        // Load saved profile image if available
        loadProfileImage()
        
        // Load notification settings
        notificationsSwitch.isChecked = profilePrefs.getBoolean("notifications_enabled", true)
        
        // Set up click listener for the edit profile button
        val editProfileBtn = findViewById<FloatingActionButton>(R.id.edit_profile_btn)
        editProfileBtn.setOnClickListener {
            checkPermissionAndPickImage()
        }
        
        // Set up click listeners for backup/restore options
        val backupDataContainer = findViewById<LinearLayout>(R.id.backup_data_container)
        backupDataContainer?.setOnClickListener {
            backupUserData()
        }
        
        val restoreDataContainer = findViewById<LinearLayout>(R.id.restore_data_container)
        restoreDataContainer?.setOnClickListener {
            restoreUserData()
        }
        
        // Set up notification switch listener
        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            profilePrefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            Toast.makeText(
                this,
                if (isChecked) "Notifications enabled" else "Notifications disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // Set up logout
        val logoutContainer = findViewById<LinearLayout>(R.id.logout_container)
        logoutContainer?.setOnClickListener {
            logout()
        }
    }
    
    private fun logout() {
        // Clear login status
        userPrefs.edit().putBoolean("is_logged_in", false).apply()
        
        // Navigate back to login screen
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun backupUserData() {
        try {
            // Create a JSON object to store all user data
            val userData = JSONObject()
            
            // Add user email
            userData.put("user_email", userPrefs.getString("user_email", ""))
            
            // Add profile image if exists
            val profileImage = profilePrefs.getString("profile_image", null)
            if (profileImage != null) {
                userData.put("profile_image", profileImage)
            }
            
            // Add notification settings
            userData.put("notifications_enabled", profilePrefs.getBoolean("notifications_enabled", true))
            
            // Add any other user data you want to backup
            // For example, if you have transaction data in another SharedPreferences file:
            val transactionPrefs = getSharedPreferences("transaction_prefs", MODE_PRIVATE)
            val allTransactions = transactionPrefs.all
            val transactionsJson = JSONObject()
            for (key in allTransactions.keys) {
                transactionsJson.put(key, allTransactions[key].toString())
            }
            userData.put("transactions", transactionsJson.toString())
            
            // Convert JSON to string
            val jsonString = userData.toString()
            
            // Save to internal storage
            val file = File(filesDir, "user_backup.json")
            val outputStream = FileOutputStream(file)
            outputStream.write(jsonString.toByteArray())
            outputStream.close()
            
            Toast.makeText(this, "Data backup successful", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e("Profile", "Error during backup: ${e.message}")
            Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun restoreUserData() {
        try {
            // Read from internal storage
            val file = File(filesDir, "user_backup.json")
            if (!file.exists()) {
                Toast.makeText(this, "No backup found", Toast.LENGTH_SHORT).show()
                return
            }
            
            val inputStream = FileInputStream(file)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            
            val jsonString = String(buffer)
            val userData = JSONObject(jsonString)
            
            // Restore user email
            if (userData.has("user_email")) {
                val email = userData.getString("user_email")
                userPrefs.edit().putString("user_email", email).apply()
                profilePrefs.edit().putString("user_email", email).apply()
                userEmail.text = email
            }
            
            // Restore profile image
            if (userData.has("profile_image")) {
                val profileImageStr = userData.getString("profile_image")
                profilePrefs.edit().putString("profile_image", profileImageStr).apply()
                
                // Update UI
                try {
                    val decodedBytes = Base64.decode(profileImageStr, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    profileImage.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e("Profile", "Error loading restored image: ${e.message}")
                }
            }
            
            // Restore notification settings
            if (userData.has("notifications_enabled")) {
                val notificationsEnabled = userData.getBoolean("notifications_enabled")
                profilePrefs.edit().putBoolean("notifications_enabled", notificationsEnabled).apply()
                notificationsSwitch.isChecked = notificationsEnabled
            }
            
            // Restore other user data as needed
            // For example, restore transaction data:
            if (userData.has("transactions")) {
                val transactionsJson = JSONObject(userData.getString("transactions"))
                val transactionPrefs = getSharedPreferences("transaction_prefs", MODE_PRIVATE)
                val editor = transactionPrefs.edit()
                
                // Clear existing data first
                editor.clear()
                
                // Add all restored transactions
                transactionsJson.keys().forEach { key ->
                    val value = transactionsJson.getString(key)
                    // You'll need to handle different data types appropriately
                    // This is a simplified example
                    editor.putString(key, value)
                }
                
                editor.apply()
            }
            
            Toast.makeText(this, "Data restored successfully", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e("Profile", "Error during restore: ${e.message}")
            Toast.makeText(this, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkPermissionAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+ we need READ_MEDIA_IMAGES permission
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                pickImageFromGallery()
            }
        } else {
            // For Android 12 and below we use READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                pickImageFromGallery()
            }
        }
    }
    
    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        selectImageLauncher.launch(intent)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImageFromGallery()
            } else {
                Toast.makeText(
                    this,
                    "Permission denied. Cannot access gallery.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun saveImageToPreferences(imageUri: Uri) {
        try {
            // Convert the image to a bitmap
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            
            // Compress and convert to Base64 string
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            val encodedImage = Base64.encodeToString(byteArray, Base64.DEFAULT)
            
            // Save to SharedPreferences
            profilePrefs.edit().putString("profile_image", encodedImage).apply()
        } catch (e: Exception) {
            Log.e("Profile", "Error saving image: ${e.message}")
        }
    }
    
    private fun loadProfileImage() {
        try {
            // Get the saved image string from SharedPreferences
            val encodedImage = profilePrefs.getString("profile_image", null)
            
            if (encodedImage != null) {
                // Decode Base64 string to bitmap
                val decodedBytes = Base64.decode(encodedImage, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                
                // Set the bitmap to ImageView
                profileImage.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            Log.e("Profile", "Error loading image: ${e.message}")
        }
    }
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        
        // Helper method to load profile image in other activities
        fun loadProfileImageFromPreferences(context: Context, imageView: ImageView) {
            try {
                val sharedPreferences = context.getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
                val encodedImage = sharedPreferences.getString("profile_image", null)
                
                if (encodedImage != null) {
                    val decodedBytes = Base64.decode(encodedImage, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                Log.e("Profile", "Error loading image: ${e.message}")
            }
        }
        
        // Helper method to get user email from preferences
        fun getUserEmailFromPreferences(context: Context): String {
            val userPrefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            return userPrefs.getString("user_email", "user@example.com") ?: "user@example.com"
        }
        
        // Helper to check if user is logged in
        fun isUserLoggedIn(context: Context): Boolean {
            val userPrefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            return userPrefs.getBoolean("is_logged_in", false)
        }
    }
}