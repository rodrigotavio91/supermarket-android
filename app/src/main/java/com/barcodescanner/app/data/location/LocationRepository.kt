package com.barcodescanner.app.data.location

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Repository that coordinates LocationManager and PlacesManager.
 * 
 * This is a singleton to ensure all callers share the same in-flight location request,
 * preventing duplicate location fetches when multiple components request location simultaneously.
 */
class LocationRepository private constructor(context: Context) {
    
    private val locationManager = LocationManager(context.applicationContext)
    private val placesManager = PlacesManager(context.applicationContext)
    
    // Mutex to ensure only one location fetch runs at a time
    private val fetchMutex = Mutex()
    
    // Shared state for the current/last location result
    // Null means no fetch has been done yet
    private val currentState = MutableStateFlow<LocationState?>(null)
    
    // Track if a fetch is currently in progress
    private var isFetching = false
    
    /**
     * Get current store with caching strategy.
     * 
     * If a fetch is already in progress, this will wait for that fetch to complete
     * rather than starting a new one. This prevents duplicate location requests
     * when multiple components call getCurrentStore() simultaneously.
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
            Log.d(TAG, "[LOC] Cache still valid. Using cached store: $cachedStore")
            if (cachedStore != null) {
                emit(LocationState.Success(cachedStore))
            } else {
                emit(LocationState.NoStoreFound)
            }
            return@flow
        }
        
        // Cache expired, need to fetch - use mutex to deduplicate concurrent requests
        val result = fetchMutex.withLock {
            // Double-check: another coroutine might have just finished fetching
            if (!locationManager.shouldRequestLocation()) {
                val cachedStore = locationManager.getLastCachedStore()
                Log.d(TAG, "[LOC] Cache refreshed by another request. Using cached store: $cachedStore")
                if (cachedStore != null) {
                    LocationState.Success(cachedStore)
                } else {
                    LocationState.NoStoreFound
                }
            } else {
                // We're the one who needs to fetch
                Log.d(TAG, "[LOC] Cache expired. Requesting new location...")
                isFetching = true
                currentState.value = LocationState.Loading
                
                val fetchResult = performLocationFetch()
                
                isFetching = false
                currentState.value = fetchResult
                fetchResult
            }
        }
        
        emit(result)
    }.flowOn(Dispatchers.IO)
    
    /**
     * Perform the actual location fetch and store lookup.
     * This should only be called while holding the fetchMutex.
     */
    private suspend fun performLocationFetch(): LocationState {
        return try {
            val location = suspendCancellableCoroutine { continuation ->
                locationManager.getLastLocation { location ->
                    continuation.resume(location)
                }
            }
            
            if (location == null) {
                // No location available, but not an error - user might not be in a store
                LocationState.NoStoreFound
            } else {
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
                    Log.d(TAG, "[LOC] Saving store to cache: ${storeInfo.placeName}")
                    locationManager.saveStoreToCache(storeInfo.placeName, storeInfo.placeId)
                    LocationState.Success(storeInfo.placeName)
                } else {
                    // No store found, save null - user is not in a store
                    Log.d(TAG, "[LOC] No store found, saving null to cache")
                    locationManager.saveStoreToCache(null, null)
                    LocationState.NoStoreFound
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current store", e)
            // Even on error, treat it as no store found rather than an error state
            LocationState.NoStoreFound
        }
    }
    
    /**
     * Force a refresh of the location, bypassing the cache.
     * Useful for manual refresh scenarios.
     */
    fun forceRefresh(): Flow<LocationState> = flow {
        emit(LocationState.Loading)
        
        // Check permission first
        if (!locationManager.hasLocationPermission()) {
            emit(LocationState.PermissionDenied)
            return@flow
        }
        
        // Clear cache to force a refresh
        locationManager.clearCache()
        
        val result = fetchMutex.withLock {
            Log.d(TAG, "Force refresh: Requesting new location...")
            isFetching = true
            currentState.value = LocationState.Loading
            
            val fetchResult = performLocationFetch()
            
            isFetching = false
            currentState.value = fetchResult
            fetchResult
        }
        
        emit(result)
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
        
        @Volatile
        private var instance: LocationRepository? = null
        
        /**
         * Get the singleton instance of LocationRepository.
         * Uses double-checked locking for thread safety.
         */
        fun getInstance(context: Context): LocationRepository {
            return instance ?: synchronized(this) {
                instance ?: LocationRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
