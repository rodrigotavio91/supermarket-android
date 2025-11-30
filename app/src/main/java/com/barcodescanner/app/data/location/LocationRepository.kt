package com.barcodescanner.app.data.location

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Sealed class representing location detection states
 */
sealed class LocationState {
    object Loading : LocationState()
    data class Success(val storeName: String) : LocationState()
    object NoStoreFound : LocationState()
    object PermissionDenied : LocationState()
}

/**
 * Repository that coordinates LocationManager and PlacesManager
 */
class LocationRepository(context: Context) {
    
    private val locationManager = LocationManager(context.applicationContext)
    private val placesManager = PlacesManager(context.applicationContext)
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if this is the first launch
     */
    fun isFirstLaunch(): Boolean {
        return preferences.getBoolean(KEY_FIRST_LAUNCH, true)
    }
    
    /**
     * Mark first launch as complete
     */
    fun setFirstLaunchComplete() {
        preferences.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
    
    /**
     * Get current store with caching strategy
     */
    fun getCurrentStore(): Flow<LocationState> = flow {
        emit(LocationState.Loading)
        
        // Check permission first
        if (!locationManager.hasLocationPermission()) {
            emit(LocationState.PermissionDenied)
            return@flow
        }
        
        // Check if cache is still valid
        if (!locationManager.shouldRequestLocation()) {
            val cachedStore = locationManager.getLastCachedStore()
            Log.d(TAG, "Cache still valid. Using cached store: $cachedStore")
            if (cachedStore != null) {
                emit(LocationState.Success(cachedStore))
            } else {
                emit(LocationState.NoStoreFound)
            }
            return@flow
        }
        
        // Cache expired, request new location
        Log.d(TAG, "Cache expired. Requesting new location...")
        try {
            val location = suspendCancellableCoroutine { continuation ->
                locationManager.getLastLocation { location ->
                    continuation.resume(location)
                }
            }
            
            if (location == null) {
                // No location available, but not an error - user might not be in a store
                emit(LocationState.NoStoreFound)
                return@flow
            }
            
            // Find nearby stores using Places API with lat/lng and radius filtering
            val storeInfo = suspendCancellableCoroutine { continuation ->
                placesManager.findNearbyStores(
                    latitude = location.latitude,
                    longitude = location.longitude
                ) { info ->
                    continuation.resume(info)
                }
            }
            
            if (storeInfo != null) {
                // Save to cache
                locationManager.saveStoreToCache(storeInfo.placeName, storeInfo.placeId)
                emit(LocationState.Success(storeInfo.placeName))
            } else {
                // No store found, save null - user is not in a store
                locationManager.saveStoreToCache(null, null)
                emit(LocationState.NoStoreFound)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current store", e)
            // Even on error, treat it as no store found rather than an error state
            emit(LocationState.NoStoreFound)
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Get cached store name
     */
    fun getCachedStore(): String? {
        return locationManager.getLastCachedStore()
    }
    
    /**
     * Get cached place ID
     */
    fun getCachedPlaceId(): String? {
        return locationManager.getLastCachedPlaceId()
    }
    
    /**
     * Clear all cached data
     */
    fun clearCache() {
        locationManager.clearCache()
    }
    
    companion object {
        private const val TAG = "LocationRepository"
        private const val PREFS_NAME = "location_prefs"
        private const val KEY_FIRST_LAUNCH = "is_first_launch"
    }
}
