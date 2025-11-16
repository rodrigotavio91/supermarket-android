package com.barcodescanner.app.data.model

/**
 * Represents the state of a product in the system
 */
enum class ProductState {
    /** Product information is being fetched in background */
    PENDING,
    
    /** Product information is available and ready to display */
    READY,
    
    /** Product information could not be found */
    NOT_FOUND
}

/**
 * Price information from a specific store
 */
data class PriceInfo(
    val storeName: String,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Product data model
 */
data class Product(
    val id: String,
    val gtin: String,
    val state: ProductState,
    val name: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val imageUrl: String? = null,
    val prices: List<PriceInfo> = emptyList()
)

/**
 * Sealed class representing API response states
 */
sealed class ApiResponse<out T> {
    data class Success<T>(val data: T) : ApiResponse<T>()
    data class Error(val message: String, val exception: Exception? = null) : ApiResponse<Nothing>()
}
