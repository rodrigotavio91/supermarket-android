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
 * Shared ViewModel for the scan flow (ScanFragment -> PriceInputFragment -> ProductDetailFragment)
 * Scoped to the scan_navigation graph to share state between fragments
 * 
 * This allows:
 * - Early product fetch in PriceInputFragment while user enters price
 * - Zero loading time in ProductDetailFragment (data already available)
 * - Progressive disclosure of product information
 */
class ScanFlowViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    private val repository = ProductRepository()
    private val locationRepository = LocationRepository(application.applicationContext)
    
    private val _product = MutableLiveData<Product?>()
    val product: LiveData<Product?> = _product
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private val _isPriceSubmitting = MutableLiveData<Boolean>()
    val isPriceSubmitting: LiveData<Boolean> = _isPriceSubmitting
    
    private val _priceSubmitSuccess = MutableLiveData<Boolean>()
    val priceSubmitSuccess: LiveData<Boolean> = _priceSubmitSuccess
    
    private val _priceSubmitError = MutableLiveData<String?>()
    val priceSubmitError: LiveData<String?> = _priceSubmitError
    
    private var pollingJob: Job? = null
    private var currentGtin: String? = null
    
    /**
     * Get cached store name from location repository
     */
    fun getCachedStoreName(): String? {
        return locationRepository.getCachedStore()
    }
    
    /**
     * Get cached place ID from location repository
     */
    fun getCachedPlaceId(): String? {
        return locationRepository.getCachedPlaceId()
    }
    
    /**
     * Submit price to the API
     * 
     * @param gtin The product GTIN code
     * @param price The price value entered by the user
     */
    fun submitPrice(gtin: String, price: Double) {
        viewModelScope.launch {
            _isPriceSubmitting.value = true
            _priceSubmitSuccess.value = false
            _priceSubmitError.value = null
            
            // Get cached location data
            val placeId = getCachedPlaceId()
            val placeName = getCachedStoreName()
            
            if (placeId.isNullOrBlank() || placeName.isNullOrBlank()) {
                _isPriceSubmitting.value = false
                _priceSubmitError.value = "Localização não disponível"
                return@launch
            }
            
            // Submit price to API
            when (val response = repository.addPrice(gtin, placeId, placeName, price)) {
                is ApiResponse.Success -> {
                    // Update the product with the new data from the server
                    _product.value = response.data
                    _isPriceSubmitting.value = false
                    _priceSubmitSuccess.value = true
                }
                is ApiResponse.Error -> {
                    _isPriceSubmitting.value = false
                    _priceSubmitError.value = response.message
                }
            }
        }
    }
    
    /**
     * Load product information by GTIN code
     * If product is PENDING, polls every 2.5s until READY or NOT_FOUND
     * Returns the Job so it can be cancelled when needed
     * 
     * This method is idempotent - calling it multiple times with the same GTIN
     * will reuse existing data and not restart the fetch
     */
    fun loadProduct(gtin: String): Job? {
        // If we're already loading this GTIN, don't start a new fetch
        if (currentGtin == gtin && pollingJob?.isActive == true) {
            return pollingJob
        }
        
        // If we already have data for this GTIN and it's not PENDING, reuse it
        if (currentGtin == gtin && _product.value != null && _product.value?.state != ProductState.PENDING) {
            return null
        }
        
        // Cancel any existing job for a different GTIN
        pollingJob?.cancel()
        currentGtin = gtin
        
        pollingJob = viewModelScope.launch {
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
        
        return pollingJob
    }
    
    /**
     * Cancel ongoing product fetch
     * Useful when navigating away from the flow
     */
    fun cancelProductFetch() {
        pollingJob?.cancel()
        pollingJob = null
    }
    
    /**
     * Reset the ViewModel state for a new scan
     * Call this when starting a new scan flow
     */
    fun reset() {
        pollingJob?.cancel()
        pollingJob = null
        currentGtin = null
        _product.value = null
        _isLoading.value = false
        _error.value = null
        _isPriceSubmitting.value = false
        _priceSubmitSuccess.value = false
        _priceSubmitError.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
