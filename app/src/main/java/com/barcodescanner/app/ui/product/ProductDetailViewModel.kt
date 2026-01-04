package com.barcodescanner.app.ui.product

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.barcodescanner.app.data.location.LocationRepository
import com.barcodescanner.app.data.model.ApiResponse
import com.barcodescanner.app.data.model.Product
import com.barcodescanner.app.data.model.ProductState
import com.barcodescanner.app.data.repository.ProductRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel for ProductDetailFragment
 * Handles product data loading and state management
 * Implements polling for PENDING products
 */
class ProductDetailViewModel(
    application: Application,
    private val repository: ProductRepository = ProductRepository()
) : AndroidViewModel(application) {
    
    private val locationRepository = LocationRepository.getInstance(application.applicationContext)
    
    private val _product = MutableLiveData<Product?>()
    val product: LiveData<Product?> = _product
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    /**
     * Get cached store name from location repository
     */
    fun getCachedStoreName(): String? {
        return locationRepository.getCachedStore()
    }
    
    /**
     * Load product information by GTIN code
     * If product is PENDING, polls every 2.5s until READY or NOT_FOUND
     * Returns the Job so it can be cancelled when the view is destroyed
     */
    fun loadProduct(gtin: String): Job {
        return viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Poll until product is no longer PENDING
            while (true) {
                when (val response = repository.getProductByGtin(gtin)) {
                    is ApiResponse.Success -> {
                        _product.value = response.data
                        _isLoading.value = false // Hide loading spinner after first response
                        
                        // Continue polling only if PENDING
                        if (response.data.state == ProductState.PENDING) {
                            delay(2500) // Wait 2.5 seconds before next poll
                        } else {
                            // READY or NOT_FOUND - stop polling
                            break
                        }
                    }
                    is ApiResponse.Error -> {
                        _error.value = response.message
                        _isLoading.value = false // Hide loading spinner on error
                        break
                    }
                }
            }
        }
    }
}
