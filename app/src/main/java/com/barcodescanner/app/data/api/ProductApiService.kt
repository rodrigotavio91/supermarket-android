package com.barcodescanner.app.data.api

import com.barcodescanner.app.data.model.PriceInfo
import com.barcodescanner.app.data.model.Product
import com.barcodescanner.app.data.model.ProductState
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * API Service interface for product operations
 * This is a mock implementation that randomly returns products in different states
 * In production, this would be replaced with a Retrofit service
 */
interface ProductApiService {
    suspend fun fetchProduct(gtin: String): Product
}

/**
 * Mock implementation of ProductApiService
 * Simulates API calls with network delay and random product states
 * Tracks pending products and transitions them to READY/NOT_FOUND after 3 polls
 */
class MockProductApiService : ProductApiService {
    
    // Track pending products and their poll counts
    private val pendingProducts = mutableMapOf<String, Int>()
    
    override suspend fun fetchProduct(gtin: String): Product {
        // Simulate network delay
        delay(500)
        
        // Check if this GTIN is already pending
        val pollCount = pendingProducts[gtin] ?: 0
        
        return if (pollCount > 0) {
            // Product is pending - increment poll count
            pendingProducts[gtin] = pollCount + 1
            
            if (pollCount >= 3) {
                // After 3 polls, transition to final state
                pendingProducts.remove(gtin)
                // 50/50 chance of READY or NOT_FOUND
                if (Random.nextBoolean()) {
                    createReadyProduct(gtin)
                } else {
                    createNotFoundProduct(gtin)
                }
            } else {
                createPendingProduct(gtin)
            }
        } else {
            // First request for this GTIN - randomly assign initial state
            // 50% chance of PENDING, 25% READY, 25% NOT_FOUND
            when (Random.nextInt(4)) {
                0, 1 -> {
                    // 50% chance - Start as pending
                    pendingProducts[gtin] = 1
                    createPendingProduct(gtin)
                }
                2 -> createReadyProduct(gtin)
                else -> createNotFoundProduct(gtin)
            }
        }
    }
    
    private fun createPendingProduct(gtin: String): Product {
        return Product(
            id = generateProductId(),
            gtin = gtin,
            state = ProductState.PENDING,
            name = null,
            brand = null,
            category = null,
            imageUrl = null,
            prices = emptyList()
        )
    }
    
    private fun createReadyProduct(gtin: String): Product {
        return Product(
            id = generateProductId(),
            gtin = gtin,
            state = ProductState.READY,
            name = "Coca-Cola Original 2L",
            brand = "Coca-Cola",
            category = "Bebidas",
            imageUrl = null,
            prices = generateMockPrices()
        )
    }
    
    private fun createNotFoundProduct(gtin: String): Product {
        return Product(
            id = generateProductId(),
            gtin = gtin,
            state = ProductState.NOT_FOUND,
            name = null,
            brand = null,
            category = null,
            imageUrl = null,
            prices = emptyList()
        )
    }
    
    private fun generateMockPrices(): List<PriceInfo> {
        val baseTime = System.currentTimeMillis()
        return listOf(
            PriceInfo("Carrefour", 5.99, baseTime - 3600000), // 1 hour ago
            PriceInfo("Pão de Açúcar", 6.49, baseTime - 7200000), // 2 hours ago
            PriceInfo("Extra", 5.79, baseTime - 10800000), // 3 hours ago
            PriceInfo("Walmart", 6.29, baseTime - 14400000), // 4 hours ago
            PriceInfo("Mercado Livre", 7.50, baseTime - 86400000) // 1 day ago
        )
    }
    
    private fun generateProductId(): String {
        return "product_${System.currentTimeMillis()}"
    }
}
