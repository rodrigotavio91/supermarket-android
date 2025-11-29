package com.barcodescanner.app.data.location

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Headers

/**
 * Data classes for Places API (New) requests and responses
 */
data class SearchNearbyRequest(
    val includedTypes: List<String>,
    val maxResultCount: Int,
    val locationRestriction: LocationRestriction
)

data class LocationRestriction(
    val circle: Circle
)

data class Circle(
    val center: LatLng,
    val radius: Double
)

data class LatLng(
    val latitude: Double,
    val longitude: Double
)

data class SearchNearbyResponse(
    val places: List<PlaceResult>?
)

data class PlaceResult(
    val displayName: LocalizedText?,
    val location: LatLng?
)

data class LocalizedText(
    val text: String?
)

/**
 * Retrofit interface for Google Places API (New)
 */
interface PlacesApiService {
    @Headers("Content-Type: application/json")
    @POST("v1/places:searchNearby")
    fun searchNearby(
        @Body request: SearchNearbyRequest
    ): Call<SearchNearbyResponse>
}

/**
 * Manages Places API integration for store detection
 * Uses the new Places API with nearby search, radius filtering, and place type filtering
 */
class PlacesManager(context: Context) {
    
    private val placesClient: PlacesClient
    private val placesApiService: PlacesApiService
    private val apiKey: String
    
    init {
        // Initialize Places SDK
        apiKey = getApiKey(context)
        if (!Places.isInitialized()) {
            Places.initialize(context.applicationContext, apiKey)
        }
        placesClient = Places.createClient(context)
        
        // Initialize Retrofit for Places API (New)
        placesApiService = createPlacesApiService()
    }
    
    /**
     * Find nearby stores based on latitude and longitude
     * Uses Places API (New) with nearby search, 500m radius, and supermarket filtering
     * 
     * @param latitude User's current latitude
     * @param longitude User's current longitude
     * @param callback Returns the closest store name or null if none found
     */
    fun findNearbyStores(latitude: Double, longitude: Double, callback: (String?) -> Unit) {
        try {
            // Create request for nearby search with 500m radius and supermarket filter
            val request = SearchNearbyRequest(
                includedTypes = listOf("supermarket", "grocery_store"),
                maxResultCount = 10,
                locationRestriction = LocationRestriction(
                    circle = Circle(
                        center = LatLng(latitude, longitude),
                        radius = SEARCH_RADIUS_METERS
                    )
                )
            )
            
            // Make API call
            placesApiService.searchNearby(request).enqueue(object : Callback<SearchNearbyResponse> {
                override fun onResponse(
                    call: Call<SearchNearbyResponse>,
                    response: Response<SearchNearbyResponse>
                ) {
                    if (response.isSuccessful) {
                        val places = response.body()?.places
                        
                        if (!places.isNullOrEmpty()) {
                            // Calculate distances and find closest store
                            val userLocation = Location("user").apply {
                                this.latitude = latitude
                                this.longitude = longitude
                            }
                            
                            val closestStore = places
                                .mapNotNull { place ->
                                    val placeLocation = place.location
                                    val placeName = place.displayName?.text
                                    
                                    if (placeLocation != null && placeName != null) {
                                        val storeLocation = Location("store").apply {
                                            this.latitude = placeLocation.latitude
                                            this.longitude = placeLocation.longitude
                                        }
                                        val distance = userLocation.distanceTo(storeLocation)
                                        Pair(placeName, distance)
                                    } else {
                                        null
                                    }
                                }
                                .minByOrNull { it.second }
                            
                            if (closestStore != null) {
                                Log.d(TAG, "Found nearby store: ${closestStore.first} (${closestStore.second}m away)")
                                callback(closestStore.first)
                            } else {
                                Log.d(TAG, "No valid stores found in response")
                                callback(null)
                            }
                        } else {
                            Log.d(TAG, "No stores found within ${SEARCH_RADIUS_METERS}m")
                            callback(null)
                        }
                    } else {
                        Log.e(TAG, "API error: ${response.code()} - ${response.message()}")
                        callback(null)
                    }
                }
                
                override fun onFailure(call: Call<SearchNearbyResponse>, t: Throwable) {
                    Log.e(TAG, "Failed to search nearby places", t)
                    callback(null)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error searching nearby stores", e)
            callback(null)
        }
    }
    
    /**
     * Create Retrofit service for Places API (New)
     */
    private fun createPlacesApiService(): PlacesApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        val apiKeyInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .addHeader("X-Goog-Api-Key", apiKey)
                .addHeader("X-Goog-FieldMask", "places.displayName,places.location")
                .build()
            chain.proceed(newRequest)
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(PlacesApiService::class.java)
    }
    
    /**
     * Get API key from AndroidManifest.xml
     */
    private fun getApiKey(context: Context): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get API key from manifest", e)
            ""
        }
    }
    
    companion object {
        private const val TAG = "PlacesManager"
        private const val SEARCH_RADIUS_METERS = 500.0
    }
}
