package com.barcodescanner.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents the state of a product in the system
 */
enum class ProductState {
    /** Product information is being fetched in background */
    PENDING,
    
    /** Product information is available and ready to display */
    READY,
    
    /** Product information could not be found */
    NOT_FOUND;
    
    companion object {
        /**
         * Maps API status string to ProductState enum
         */
        fun fromApiStatus(status: String?): ProductState {
            return when (status?.lowercase()) {
                "ready" -> READY
                "pending" -> PENDING
                "not_found", "notfound" -> NOT_FOUND
                else -> NOT_FOUND // Default to NOT_FOUND for unknown statuses
            }
        }
    }
}

/**
 * Price information from a specific store
 */
data class PriceInfo(
    val storeName: String,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val storeId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
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
    val prices: List<PriceInfo> = emptyList(),
    val myTodayPrice: MyTodayPrice? = null,
    val myPrices: List<MyTodayPrice> = emptyList()
)

/**
 * Today's price info for the user's location
 */
data class MyTodayPrice(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("value")
    val value: Double,
    
    @SerializedName("created_at")
    val createdAt: String,
    
    @SerializedName("store_name")
    val storeName: String?
)

/**
 * Request DTO for adding a price
 */
data class AddPriceRequest(
    @SerializedName("place_id")
    val placeId: String,
    
    @SerializedName("place_name")
    val placeName: String,
    
    @SerializedName("value")
    val value: Double
)

/**
 * Sealed class representing API response states
 */
sealed class ApiResponse<out T> {
    data class Success<T>(val data: T) : ApiResponse<T>()
    data class Error(val message: String, val exception: Exception? = null) : ApiResponse<Nothing>()
}

/**
 * API response DTO matching the live API structure
 * Endpoint: GET /products/{gtin_code}
 */
data class ProductApiResponse(
    @SerializedName("gtin_code")
    val gtinCode: String,
    
    @SerializedName("name")
    val name: String?,
    
    @SerializedName("brand")
    val brand: String?,
    
    @SerializedName("category")
    val category: String?,
    
    @SerializedName("image_url")
    val imageUrl: String?,
    
    @SerializedName("status")
    val status: String?,
    
    @SerializedName("my_today_price")
    val myTodayPrice: MyTodayPrice?,
    
    @SerializedName("my_prices")
    val myPrices: List<MyTodayPrice>?
) {
    /**
     * Converts API response to domain Product model
     */
    fun toDomainModel(prices: List<PriceInfo> = emptyList()): Product {
        return Product(
            id = "product_${gtinCode}_${System.currentTimeMillis()}",
            gtin = gtinCode,
            state = ProductState.fromApiStatus(status),
            name = name,
            brand = brand,
            category = category,
            imageUrl = imageUrl,
            prices = prices,
            myTodayPrice = myTodayPrice,
            myPrices = myPrices ?: emptyList()
        )
    }
}
