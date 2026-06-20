package com.barcodescanner.app.data.repository

import android.util.Log
import com.barcodescanner.app.data.api.ProductApiService
import com.barcodescanner.app.data.model.ApiResponse
import com.barcodescanner.app.data.model.NearbyPrice
import com.barcodescanner.app.data.model.PriceWarningResponse
import com.barcodescanner.app.data.model.Product
import com.barcodescanner.app.data.model.SubmitPriceRequest
import com.google.gson.Gson

class ProductRepository(
    private val apiService: ProductApiService
) {

    suspend fun getProductByGtin(gtin: String, placeId: String? = null): ApiResponse<Product> {
        return try {
            val apiResponse = apiService.fetchProduct(gtin, placeId)
            val product = apiResponse.toDomainModel()
            ApiResponse.Success(product)
        } catch (e: Exception) {
            ApiResponse.Error(
                message = "Failed to fetch product information",
                exception = e
            )
        }
    }

    suspend fun submitPriceSubmission(request: SubmitPriceRequest): PriceSubmissionResult {
        return try {
            Log.d(TAG, "Submitting price: ${request.value} for product ${request.productId}")
            val response = apiService.submitPrice(request)

            if (response.isSuccessful) {
                Log.d(TAG, "Price submission successful (${response.code()})")
                PriceSubmissionResult.Success
            } else if (response.code() == 409) {
                val errorBody = response.errorBody()?.string()
                Log.d(TAG, "Price warning (409): $errorBody")
                val warning = try {
                    Gson().fromJson(errorBody, PriceWarningResponse::class.java)
                } catch (e: Exception) {
                    null
                }
                if (warning != null) {
                    PriceSubmissionResult.Warning(warning)
                } else {
                    PriceSubmissionResult.Error(errorBody ?: "Price requires confirmation")
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Price submission failed (${response.code()}): $errorBody")
                PriceSubmissionResult.Error(errorBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit price", e)
            PriceSubmissionResult.Error(e.message ?: "Failed to submit price")
        }
    }

    suspend fun getNearbyPrices(
        gtin: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 5000,
        placeId: String? = null
    ): ApiResponse<List<NearbyPrice>> {
        return try {
            Log.d(TAG, "Fetching nearby prices for $gtin at ($latitude, $longitude) within ${radiusMeters}m exclude=$placeId")
            val response = apiService.getNearbyPrices(gtin, latitude, longitude, radiusMeters, placeId)
            val prices = response.prices?.map { it.toDomainModel() } ?: emptyList()
            Log.d(TAG, "Nearby prices: ${prices.size} results")
            ApiResponse.Success(prices)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch nearby prices", e)
            ApiResponse.Error(
                message = "Failed to fetch nearby prices",
                exception = e
            )
        }
    }

    companion object {
        private const val TAG = "ProductRepository"
    }
}

sealed class PriceSubmissionResult {
    data object Success : PriceSubmissionResult()
    data class Warning(val warning: PriceWarningResponse) : PriceSubmissionResult()
    data class Error(val message: String) : PriceSubmissionResult()
}
