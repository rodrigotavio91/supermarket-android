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
    data class Error(val message: String) : LocationState()
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
        
        // Check cache first
        if (!locationManager.shouldRequestLocation()) {
            val cachedStore = locationManager.getLastCachedStore()
            if (cachedStore != null) {
                Log.d(TAG, "Using cached store: $cachedStore")
                emit(LocationState.Success(cachedStore))
                return@flow
            }
        }
        
        // Request new location
        try {
            val location = suspendCancellableCoroutine { continuation ->
                locationManager.getLastLocation { location ->
                    continuation.resume(location)
                }
            }
            
            if (location == null) {
                emit(LocationState.Error("Não foi possível obter sua localização"))
                return@flow
            }
            
            // Find nearby stores using Places API with lat/lng and radius filtering
            val storeName = suspendCancellableCoroutine { continuation ->
                placesManager.findNearbyStores(
                    latitude = location.latitude,
                    longitude = location.longitude
                ) { name ->
                    continuation.resume(name)
                }
            }
            
            if (storeName != null) {
                // Save to cache
                locationManager.saveStoreToCache(storeName)
                emit(LocationState.Success(storeName))
            } else {
                // No store found, use default
                val defaultStore = "Unknown Store"
                locationManager.saveStoreToCache(defaultStore)
                emit(LocationState.Success(defaultStore))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current store", e)
            emit(LocationState.Error("Erro ao detectar localização: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Get cached store name
     */
    fun getCachedStore(): String? {
        return locationManager.getLastCachedStore()
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
