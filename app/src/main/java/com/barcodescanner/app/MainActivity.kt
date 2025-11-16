package com.barcodescanner.app

import android.os.Bundle
import android.content.res.ColorStateList
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.barcodescanner.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Disable fragment transition animations
        window.setWindowAnimations(0)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Setup bottom navigation with nav controller
        binding.navView.setupWithNavController(navController)
        
        // Ensure FAB has correct colors
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        binding.fabScan.backgroundTintList = ColorStateList.valueOf(whiteColor)
        binding.fabScan.imageTintList = ColorStateList.valueOf(primaryColor)
        
        // Setup FAB to navigate to scan fragment
        // Let BottomNavigationView handle navigation with proper state preservation
        binding.fabScan.setOnClickListener {
            binding.navView.selectedItemId = R.id.navigation_scan
        }
        
        // Update FAB elevation based on current destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_scan -> {
                    // Increase elevation when selected
                    binding.fabScan.compatElevation = 20f
                }
                else -> {
                    // Normal elevation when not selected
                    binding.fabScan.compatElevation = 16f
                }
            }
        }
    }
}
