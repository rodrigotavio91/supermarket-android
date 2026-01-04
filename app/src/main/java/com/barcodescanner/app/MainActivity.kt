package com.barcodescanner.app

import android.content.Intent
import android.os.Bundle
import android.content.res.ColorStateList
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.barcodescanner.app.databinding.ActivityMainBinding
import com.barcodescanner.app.data.location.LocationRepository
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationRepository: LocationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize location repository (singleton)
        locationRepository = LocationRepository.getInstance(this)
        
        // Check if we should show location screen on first launch
        if (savedInstanceState == null && locationRepository.isFirstLaunch()) {
            // Launch LocationActivity for first-time setup
            val intent = Intent(this, LocationActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        // Disable fragment transition animations
        window.setWindowAnimations(0)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Refresh location cache when app is resumed (if expired)
        // Using repeatOnLifecycle ensures proper lifecycle handling - the block runs
        // each time the lifecycle reaches RESUMED state and cancels when it falls below
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                locationRepository.getCurrentStore().collect { state ->
                    // Location state updated in the background
                    // Individual fragments will read the updated cache when needed
                }
            }
        }

        // Get the NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Setup bottom navigation with nav controller
        binding.navView.setupWithNavController(navController)
        
        // Handle bottom navigation item selection to always reset to camera when scan is selected
        binding.navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_scan -> {
                    // If we're already in the scan navigation graph, pop to the start destination
                    if (navController.currentDestination?.parent?.id == R.id.navigation_scan) {
                        navController.popBackStack(R.id.scanFragment, false)
                    } else {
                        navController.navigate(R.id.navigation_scan)
                    }
                    true
                }
                else -> {
                    // For other tabs, use default navigation
                    navController.navigate(item.itemId)
                    true
                }
            }
        }
        
        // Ensure FAB has correct colors
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        binding.fabScan.backgroundTintList = ColorStateList.valueOf(whiteColor)
        binding.fabScan.imageTintList = ColorStateList.valueOf(primaryColor)
        
        // Setup FAB to navigate to scan fragment
        // Let BottomNavigationView handle navigation with proper state preservation
        // When navigating to scan, always pop to the start destination (camera)
        binding.fabScan.setOnClickListener {
            // If we're already in the scan navigation graph, pop to the start destination
            if (navController.currentDestination?.parent?.id == R.id.navigation_scan) {
                navController.popBackStack(R.id.scanFragment, false)
            } else {
                binding.navView.selectedItemId = R.id.navigation_scan
            }
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
