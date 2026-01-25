package com.barcodescanner.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.barcodescanner.app.databinding.ActivityLocationBinding

/**
 * Activity for requesting location permission.
 * 
 * This activity is shown whenever the app is opened without location permission granted.
 * The user cannot proceed to the main app without granting location access.
 * Pressing back will close the app entirely.
 */
class LocationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLocationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Handle back button to close the app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Close the app when back is pressed
                finishAffinity()
            }
        })
    }
    
    /**
     * Navigate to main activity after permission is granted
     */
    fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
