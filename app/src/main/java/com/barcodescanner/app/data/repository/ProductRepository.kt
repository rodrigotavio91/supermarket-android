package com.barcodescanner.app.data.repository

import com.barcodescanner.app.data.api.MockProductApiService
import com.barcodescanner.app.data.api.ProductApiService
import com.barcodescanner.app.data.model.ApiResponse
import com.barcodescanner.app.data.model.Product

/**
 * Repository for product data operations
 * Provides a clean API for the ViewModel layer to fetch product information
 */
class ProductRepository(
    private val apiService: ProductApiService = MockProductApiService()
) {
    
    /**
     * Fetches product information by GTIN code
     * 
     * @param gtin The GTIN code scanned from the barcode
     * @return ApiResponse containing the product or an error
     */
    suspend fun getProductByGtin(gtin: String): ApiResponse<Product> {
        return try {
            val product = apiService.fetchProduct(gtin)
            ApiResponse.Success(product)
        } catch (e: Exception) {
            ApiResponse.Error(
                message = "Failed to fetch product information",
                exception = e
            )
        }
    }
}
