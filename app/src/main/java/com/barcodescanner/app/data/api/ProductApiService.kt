package com.barcodescanner.app.data.api

import com.barcodescanner.app.data.model.AddPriceRequest
import com.barcodescanner.app.data.model.ProductApiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit API Service for product operations
 * Connects to the live supermarket API
 */
interface ProductApiService {
    
    @Headers("Accept: application/json")
    @GET("products/{gtin_code}")
    suspend fun fetchProduct(@Path("gtin_code") gtinCode: String): ProductApiResponse
    
    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("products/{gtin_code}/add_price")
    suspend fun addPrice(
        @Path("gtin_code") gtinCode: String,
        @Body request: AddPriceRequest
    )
    
}
