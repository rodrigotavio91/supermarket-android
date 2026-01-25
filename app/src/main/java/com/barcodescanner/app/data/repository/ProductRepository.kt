package com.barcodescanner.app.data.repository

import android.util.Log
import com.barcodescanner.app.data.api.ProductApiService
import com.barcodescanner.app.data.model.AddPriceRequest
import com.barcodescanner.app.data.model.ApiResponse
import com.barcodescanner.app.data.model.PriceInfo
import com.barcodescanner.app.data.model.Product

/**
 * Repository for product data operations
 * Provides a clean API for the ViewModel layer to fetch product information
 */
class ProductRepository(
    private val apiService: ProductApiService
) {
    
    /**
     * Fetches product information by GTIN code from the live API
     * 
     * @param gtin The GTIN code scanned from the barcode
     * @return ApiResponse containing the product or an error
     */
    suspend fun getProductByGtin(gtin: String): ApiResponse<Product> {
        return try {
            val apiResponse = apiService.fetchProduct(gtin)
            val product = apiResponse.toDomainModel()
            
            ApiResponse.Success(product)
        } catch (e: Exception) {
            ApiResponse.Error(
                message = "Failed to fetch product information",
                exception = e
            )
        }
    }
    
    /**
     * Adds a price for a product at a specific location
     * 
     * @param gtin The GTIN code of the product
     * @param placeId The Google Places ID of the store
     * @param placeName The name of the store
     * @param value The price value
     * @return ApiResponse containing the updated product or an error
     */
    suspend fun addPrice(
        gtin: String,
        placeId: String,
        placeName: String,
        value: Double
    ): ApiResponse<Product> {
        return try {
            val request = AddPriceRequest(
                placeId = placeId,
                placeName = placeName,
                value = value
            )
            Log.d(TAG, "Submitting price: gtin=$gtin, placeId=$placeId, placeName=$placeName, value=$value")
            
            // Submit the price (no response body)
            apiService.addPrice(gtin, request)
            Log.d(TAG, "Price submitted successfully")
            
            // Fetch the updated product to get my_today_price
            val apiResponse = apiService.fetchProduct(gtin)
            Log.d(TAG, "Fetched product after price submission: myTodayPrice=${apiResponse.myTodayPrice}")
            
            val product = apiResponse.toDomainModel()
            
            ApiResponse.Success(product)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add price", e)
            ApiResponse.Error(
                message = "Failed to add price",
                exception = e
            )
        }
    }
    
    companion object {
        private const val TAG = "ProductRepository"
    }
}
