package com.barcodescanner.app.data.repository

import com.barcodescanner.app.data.api.ProductApiService
import com.barcodescanner.app.data.model.ApiResponse
import com.barcodescanner.app.data.model.PriceInfo
import com.barcodescanner.app.data.model.Product

/**
 * Repository for product data operations
 * Provides a clean API for the ViewModel layer to fetch product information
 */
class ProductRepository(
    private val apiService: ProductApiService = ProductApiService.create()
) {
    
    /**
     * Fetches product information by GTIN code from the live API
     * Merges API product data with static pricing information
     * 
     * @param gtin The GTIN code scanned from the barcode
     * @return ApiResponse containing the product or an error
     */
    suspend fun getProductByGtin(gtin: String): ApiResponse<Product> {
        return try {
            val apiResponse = apiService.fetchProduct(gtin)
            
            // Merge API data with static pricing
            val product = apiResponse.toDomainModel(
                prices = generateStaticPrices()
            )
            
            ApiResponse.Success(product)
        } catch (e: Exception) {
            ApiResponse.Error(
                message = "Failed to fetch product information",
                exception = e
            )
        }
    }
    
    /**
     * Generates static pricing data for demonstration purposes
     * This will be replaced with real pricing data in the future
     */
    private fun generateStaticPrices(): List<PriceInfo> {
        val baseTime = System.currentTimeMillis()
        return listOf(
            PriceInfo("Carrefour", 5.99, baseTime - 3600000), // 1 hour ago
            PriceInfo("Pão de Açúcar", 6.49, baseTime - 7200000), // 2 hours ago
            PriceInfo("Extra", 5.79, baseTime - 10800000), // 3 hours ago
            PriceInfo("Walmart", 6.29, baseTime - 14400000), // 4 hours ago
            PriceInfo("Mercado Livre", 7.50, baseTime - 86400000) // 1 day ago
        )
    }
}
