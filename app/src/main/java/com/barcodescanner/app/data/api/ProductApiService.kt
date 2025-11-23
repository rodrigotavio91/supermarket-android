package com.barcodescanner.app.data.api

import com.barcodescanner.app.BuildConfig
import com.barcodescanner.app.data.model.ProductApiResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * Retrofit API Service for product operations
 * Connects to the live supermarket API
 */
interface ProductApiService {
    
    @Headers("Accept: application/json")
    @GET("products/{gtin_code}")
    suspend fun fetchProduct(@Path("gtin_code") gtinCode: String): ProductApiResponse
    
    companion object {
        private const val BASE_URL = BuildConfig.API_BASE_URL
        
        /**
         * Creates a configured instance of ProductApiService
         */
        fun create(): ProductApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            return retrofit.create(ProductApiService::class.java)
        }
    }
}
