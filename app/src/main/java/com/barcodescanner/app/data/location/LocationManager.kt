package com.barcodescanner.app.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.barcodescanner.app.BuildConfig
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
        
        return timeSinceLastUpdate > BuildConfig.CACHE_TIMEOUT_MS
    }
    
    /**
     * Get the last cached location information
     */
    fun getLastCachedStore(): String? {
        return preferences.getString(KEY_LAST_STORE_NAME, null)
    }
    
    /**
     * Get the last cached place ID
     */
    fun getLastCachedPlaceId(): String? {
        return preferences.getString(KEY_LAST_PLACE_ID, null)
    }
    
    /**
     * Save store information to cache
     */
    fun saveStoreToCache(storeName: String?, placeId: String? = null) {
        preferences.edit().apply {
            putString(KEY_LAST_STORE_NAME, storeName)
            putString(KEY_LAST_PLACE_ID, placeId)
            putLong(KEY_LAST_LOCATION_TIME, System.currentTimeMillis())
            apply()
        }
    }

    fun saveStoreToCache(storeName: String?, placeId: String?, storeLatitude: Double?, storeLongitude: Double?) {
        preferences.edit().apply {
            putString(KEY_LAST_STORE_NAME, storeName)
            putString(KEY_LAST_PLACE_ID, placeId)
            storeLatitude?.let { putFloat(KEY_LAST_STORE_LATITUDE, it.toFloat()) }
            storeLongitude?.let { putFloat(KEY_LAST_STORE_LONGITUDE, it.toFloat()) }
            putLong(KEY_LAST_LOCATION_TIME, System.currentTimeMillis())
            apply()
        }
    }

    fun getLastCachedStoreLatitude(): Double? {
        val stored = preferences.getFloat(KEY_LAST_STORE_LATITUDE, Float.NaN)
        return if (stored.isNaN()) null else stored.toDouble()
    }

    fun getLastCachedStoreLongitude(): Double? {
        val stored = preferences.getFloat(KEY_LAST_STORE_LONGITUDE, Float.NaN)
        return if (stored.isNaN()) null else stored.toDouble()
    }

    fun getLastCachedLatitude(): Double? {
        val stored = preferences.getFloat(KEY_LAST_LATITUDE, Float.NaN)
        return if (stored.isNaN()) null else stored.toDouble()
    }

    fun getLastCachedLongitude(): Double? {
        val stored = preferences.getFloat(KEY_LAST_LONGITUDE, Float.NaN)
        return if (stored.isNaN()) null else stored.toDouble()
    }

    fun getLastCachedAccuracy(): Float? {
        val stored = preferences.getFloat(KEY_LAST_ACCURACY, -1f)
        return if (stored < 0) null else stored
    }

    fun saveUserLocation(latitude: Double, longitude: Double, accuracy: Float) {
        preferences.edit().apply {
            putFloat(KEY_LAST_LATITUDE, latitude.toFloat())
            putFloat(KEY_LAST_LONGITUDE, longitude.toFloat())
            putFloat(KEY_LAST_ACCURACY, accuracy)
            apply()
        }
    }
    
    /**
     * Check if precise location permission is granted.
     * This app requires fine location to accurately detect which store the user is in.
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
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
            requestCurrentLocation(callback)
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
            Log.d(TAG, "[LOC] Requesting current location...")
            val cancellationTokenSource = CancellationTokenSource()
            
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            )
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        Log.d(TAG, "[LOC] Current location: ${location.latitude}, ${location.longitude}")
                        saveUserLocation(location.latitude, location.longitude, location.accuracy)
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
            remove(KEY_LAST_PLACE_ID)
            remove(KEY_LAST_LOCATION_TIME)
            remove(KEY_LAST_LATITUDE)
            remove(KEY_LAST_LONGITUDE)
            remove(KEY_LAST_ACCURACY)
            remove(KEY_LAST_STORE_LATITUDE)
            remove(KEY_LAST_STORE_LONGITUDE)
            apply()
        }
    }
    
    companion object {
        private const val TAG = "LocationManager"
        private const val PREFS_NAME = "location_prefs"
        private const val KEY_LAST_STORE_NAME = "last_store_name"
        private const val KEY_LAST_PLACE_ID = "last_place_id"
        private const val KEY_LAST_LOCATION_TIME = "last_location_time"
        private const val KEY_LAST_LATITUDE = "last_latitude"
        private const val KEY_LAST_LONGITUDE = "last_longitude"
        private const val KEY_LAST_ACCURACY = "last_accuracy"
        private const val KEY_LAST_STORE_LATITUDE = "last_store_latitude"
        private const val KEY_LAST_STORE_LONGITUDE = "last_store_longitude"
    }
}
