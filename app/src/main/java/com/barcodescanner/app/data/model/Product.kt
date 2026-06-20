package com.barcodescanner.app.data.model

import com.google.gson.annotations.SerializedName

enum class ProductState {
    PENDING,
    READY,
    NOT_FOUND;

    companion object {
        fun fromApiStatus(status: String?): ProductState {
            return when (status?.lowercase()) {
                "ready" -> READY
                "pending" -> PENDING
                "not_found", "notfound" -> NOT_FOUND
                else -> NOT_FOUND
            }
        }
    }
}

data class PriceInfo(
    val storeName: String,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val storeId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class TodayPrice(
    val id: Int,
    val value: Double,
    val status: String,
    val store: Store,
    val createdAt: String
)

data class Store(
    val id: Int,
    val name: String,
    val googlePlaceId: String
)

data class Product(
    val id: Int,
    val gtin: String,
    val state: ProductState,
    val name: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val imageUrl: String? = null,
    val prices: List<PriceInfo> = emptyList(),
    val todayPrice: TodayPrice? = null,
    val myPrices: List<MyTodayPrice> = emptyList(),
    val requiresPriceSubmission: Boolean = false
)

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

data class SubmitPriceRequest(
    @SerializedName("product_id")
    val productId: Int,

    @SerializedName("place_id")
    val placeId: String,

    @SerializedName("place_name")
    val placeName: String,

    @SerializedName("value")
    val value: String,

    @SerializedName("store_selection_method")
    val storeSelectionMethod: String,

    val latitude: Double? = null,

    val longitude: Double? = null,

    @SerializedName("location_accuracy_meters")
    val locationAccuracyMeters: Double? = null,

    @SerializedName("store_latitude")
    val storeLatitude: Double? = null,

    @SerializedName("store_longitude")
    val storeLongitude: Double? = null,

    @SerializedName("confirm_warning")
    val confirmWarning: Boolean = false
)

data class PriceSubmissionResponse(
    val submission: PriceSubmissionDto
)

data class PriceSubmissionDto(
    val id: Int,

    @SerializedName("product_id")
    val productId: Int,

    @SerializedName("store_id")
    val storeId: Int,

    val value: String,

    val status: String,

    @SerializedName("confidence_score")
    val confidenceScore: Int,

    @SerializedName("store_selection_method")
    val storeSelectionMethod: String,

    val latitude: Double? = null,

    val longitude: Double? = null,

    @SerializedName("location_accuracy_meters")
    val locationAccuracyMeters: String? = null,

    @SerializedName("price_warning_type")
    val priceWarningType: String? = null,

    @SerializedName("price_warning_acknowledged_at")
    val priceWarningAcknowledgedAt: String? = null,

    @SerializedName("created_at")
    val createdAt: String
)

data class PriceWarningResponse(
    val error: String,
    val message: String,

    @SerializedName("warning_type")
    val warningType: String? = null,

    @SerializedName("comparison_price")
    val comparisonPrice: ComparisonPriceDto? = null,

    @SerializedName("submitted_value")
    val submittedValue: String? = null,

    @SerializedName("suggested_value")
    val suggestedValue: String? = null
)

data class ComparisonPriceDto(
    val value: String,
    val source: String,

    @SerializedName("created_at")
    val createdAt: String? = null
)

sealed class ApiResponse<out T> {
    data class Success<T>(val data: T) : ApiResponse<T>()
    data class Error(val message: String, val exception: Exception? = null) : ApiResponse<Nothing>()
}

data class ProductApiResponse(
    val id: Int,

    @SerializedName("gtin_code")
    val gtinCode: String,

    val name: String?,

    val brand: String?,

    val category: String?,

    @SerializedName("image_url")
    val imageUrl: String?,

    val status: String?,

    @SerializedName("today_price")
    val todayPrice: TodayPriceDto?,

    @SerializedName("my_prices")
    val myPrices: List<MyTodayPrice>?,

    @SerializedName("requires_price_submission")
    val requiresPriceSubmission: Boolean = false
) {
    fun toDomainModel(): Product {
        return Product(
            id = id,
            gtin = gtinCode,
            state = ProductState.fromApiStatus(status),
            name = name,
            brand = brand,
            category = category,
            imageUrl = imageUrl,
            prices = emptyList(),
            todayPrice = todayPrice?.toDomainModel(),
            myPrices = myPrices ?: emptyList(),
            requiresPriceSubmission = requiresPriceSubmission
        )
    }
}

data class TodayPriceDto(
    val id: Int,

    @SerializedName("value")
    val value: String,

    @SerializedName("status")
    val status: String?,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("store")
    val store: StoreDto?
) {
    fun toDomainModel(): TodayPrice {
        return TodayPrice(
            id = id,
            value = value.toDoubleOrNull() ?: 0.0,
            status = status ?: "pending",
            store = store?.let {
                Store(id = it.id, name = it.name, googlePlaceId = it.googlePlaceId)
            } ?: Store(id = 0, name = "", googlePlaceId = ""),
            createdAt = createdAt
        )
    }
}

data class StoreDto(
    val id: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("google_place_id")
    val googlePlaceId: String
)

data class NearbyPricesResponse(
    @SerializedName("prices")
    val prices: List<NearbyPriceDto>?
)

data class NearbyPriceDto(
    val id: Int,

    @SerializedName("value")
    val value: String,

    @SerializedName("store")
    val store: StoreDto?,

    @SerializedName("distance_meters")
    val distanceMeters: Double?,

    @SerializedName("last_seen_at")
    val lastSeenAt: String?
) {
    fun toDomainModel(): NearbyPrice {
        return NearbyPrice(
            id = id,
            value = value.toDoubleOrNull() ?: 0.0,
            store = store?.let {
                Store(id = it.id, name = it.name, googlePlaceId = it.googlePlaceId)
            } ?: Store(id = 0, name = "", googlePlaceId = ""),
            distanceMeters = distanceMeters,
            lastSeenAt = lastSeenAt
        )
    }
}

data class NearbyPrice(
    val id: Int,
    val value: Double,
    val store: Store,
    val distanceMeters: Double?,
    val lastSeenAt: String?
)
