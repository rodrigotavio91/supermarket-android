package com.barcodescanner.app.data.api

import com.barcodescanner.app.data.model.NearbyPricesResponse
import com.barcodescanner.app.data.model.ProductApiResponse
import com.barcodescanner.app.data.model.SubmitPriceRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ProductApiService {

    @Headers("Accept: application/json")
    @GET("products/{gtin_code}")
    suspend fun fetchProduct(
        @Path("gtin_code") gtinCode: String,
        @Query("place_id") placeId: String? = null
    ): ProductApiResponse

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("product_price_submissions")
    suspend fun submitPrice(
        @Body request: SubmitPriceRequest
    ): Response<Unit>

    @Headers("Accept: application/json")
    @GET("products/{gtin_code}/nearby_prices")
    suspend fun getNearbyPrices(
        @Path("gtin_code") gtinCode: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radius_meters") radiusMeters: Int,
        @Query("place_id") placeId: String? = null
    ): NearbyPricesResponse

}
