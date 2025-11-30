package com.barcodescanner.app.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * Manages location requests and caching
 */
class LocationManager(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if location should be requested based on cache timeout
     */
    fun shouldRequestLocation(): Boolean {
        val lastLocationTime = preferences.getLong(KEY_LAST_LOCATION_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastLocationTime
        
        return timeSinceLastUpdate > CACHE_TIMEOUT_MS
    }
    
    /**
     * Get the last cached location information
     */
    fun getLastCachedStore(): String? {
        return preferences.getString(KEY_LAST_STORE_NAME, null)
    }
    
    /**
     * Save store information to cache
     */
    fun saveStoreToCache(storeName: String?) {
        preferences.edit().apply {
            putString(KEY_LAST_STORE_NAME, storeName)
            putLong(KEY_LAST_LOCATION_TIME, System.currentTimeMillis())
            apply()
        }
    }
    
    /**
     * Check if location permission is granted
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get current location
     */
    fun getLastLocation(callback: (Location?) -> Unit) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            callback(null)
            return
        }
        
        try {
            // Try to get last known location first (faster)
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        Log.d(TAG, "Last known location: ${location.latitude}, ${location.longitude}")
                        callback(location)
                    } else {
                        // If no last location, request current location
                        requestCurrentLocation(callback)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get last location", exception)
                    // Try current location as fallback
                    requestCurrentLocation(callback)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location", e)
            callback(null)
        }
    }
    
    /**
     * Request current location update
     */
    private fun requestCurrentLocation(callback: (Location?) -> Unit) {
        if (!hasLocationPermission()) {
            callback(null)
            return
        }
        
        try {
            val cancellationTokenSource = CancellationTokenSource()
            
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            )
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        Log.d(TAG, "Current location: ${location.latitude}, ${location.longitude}")
                    } else {
                        Log.w(TAG, "Current location is null")
                    }
                    callback(location)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get current location", exception)
                    callback(null)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception requesting current location", e)
            callback(null)
        }
    }
    
    /**
     * Clear cached location data
     */
    fun clearCache() {
        preferences.edit().apply {
            remove(KEY_LAST_STORE_NAME)
            remove(KEY_LAST_LOCATION_TIME)
            apply()
        }
    }
    
    companion object {
        private const val TAG = "LocationManager"
        private const val PREFS_NAME = "location_prefs"
        private const val KEY_LAST_STORE_NAME = "last_store_name"
        private const val KEY_LAST_LOCATION_TIME = "last_location_time"
        private const val CACHE_TIMEOUT_MS = 10 * 1000L // 10 seconds (for testing)
    }
}
