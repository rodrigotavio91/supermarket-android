package com.barcodescanner.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.barcodescanner.app.databinding.ActivityLocationBinding

/**
 * Standalone activity for location detection on first launch
 */
class LocationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLocationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
    
    /**
     * Navigate to main activity
     */
    fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
