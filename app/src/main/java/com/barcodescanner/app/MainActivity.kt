package com.barcodescanner.app

import android.os.Bundle
import android.content.res.ColorStateList
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.barcodescanner.app.databinding.ActivityMainBinding
import com.barcodescanner.app.data.location.LocationRepository

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var locationRepository: LocationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Disable fragment transition animations
        window.setWindowAnimations(0)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize location repository
        locationRepository = LocationRepository(this)

        // Get the NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Check if we should show location loading screen
        if (savedInstanceState == null) {
            if (!locationRepository.isFirstLaunch() && locationRepository.getCachedStore() != null) {
                // Skip location loading and go directly to scan
                navController.navigate(R.id.navigation_scan)
            }
            // Otherwise navigation graph will show location loading fragment as start destination
        }
        
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
