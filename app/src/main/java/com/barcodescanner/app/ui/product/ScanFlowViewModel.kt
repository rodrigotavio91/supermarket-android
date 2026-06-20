package com.barcodescanner.app.ui.product

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.barcodescanner.app.data.api.ApiClient
import com.barcodescanner.app.data.location.LocationRepository
import com.barcodescanner.app.data.model.ApiResponse
import com.barcodescanner.app.data.model.NearbyPrice
import com.barcodescanner.app.data.model.PriceWarningResponse
import com.barcodescanner.app.data.model.Product
import com.barcodescanner.app.data.model.ProductState
import com.barcodescanner.app.data.model.SubmitPriceRequest
import com.barcodescanner.app.data.repository.PriceSubmissionResult
import com.barcodescanner.app.data.repository.ProductRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class ScanFlowViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = ProductRepository(
        ApiClient.getInstance(application).productApiService
    )
    private val locationRepository = LocationRepository.getInstance(application.applicationContext)

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

    private val _priceWarning = MutableLiveData<PriceWarningResponse?>()
    val priceWarning: LiveData<PriceWarningResponse?> = _priceWarning

    private val _nearbyPrices = MutableLiveData<ApiResponse<List<NearbyPrice>>?>()
    val nearbyPrices: LiveData<ApiResponse<List<NearbyPrice>>?> = _nearbyPrices

    private var pollingJob: Job? = null
    private var currentGtin: String? = null

    private var pendingPriceCents: Long = 0
    private var pendingProductId: Int = 0

    fun getCachedStoreName(): String? {
        return locationRepository.getCachedStore()
    }

    fun getCachedPlaceId(): String? {
        return locationRepository.getCachedPlaceId()
    }

    fun submitPrice(productId: Int, gtin: String, priceCents: Long) {
        val placeId = getCachedPlaceId()
        val placeName = getCachedStoreName()

        if (placeId.isNullOrBlank() || placeName.isNullOrBlank()) {
            _isPriceSubmitting.value = false
            _priceSubmitError.value = "Localizacao nao disponivel"
            return
        }

        pendingPriceCents = priceCents
        pendingProductId = productId

        doSubmitPrice(productId, gtin, placeId, placeName, priceCents, confirmWarning = false)
    }

    fun retryWithWarningConfirmation() {
        val placeId = getCachedPlaceId()
        val placeName = getCachedStoreName()

        if (placeId.isNullOrBlank() || placeName.isNullOrBlank()) {
            _isPriceSubmitting.value = false
            _priceSubmitError.value = "Localizacao nao disponivel"
            return
        }

        _priceWarning.value = null
        doSubmitPrice(pendingProductId, currentGtin ?: "", placeId, placeName, pendingPriceCents, confirmWarning = true)
    }

    fun dismissWarning() {
        _priceWarning.value = null
        _isPriceSubmitting.value = false
    }

    private fun doSubmitPrice(
        productId: Int,
        gtin: String,
        placeId: String,
        placeName: String,
        priceCents: Long,
        confirmWarning: Boolean
    ) {
        viewModelScope.launch {
            _isPriceSubmitting.value = true
            _priceSubmitSuccess.value = false
            _priceSubmitError.value = null
        _priceWarning.value = null
        _nearbyPrices.value = null

            val priceValue = String.format(Locale.US, "%.2f", priceCents.toDouble() / 100.0)
            val userLatitude = locationRepository.getCachedLatitude()
            val userLongitude = locationRepository.getCachedLongitude()
            val accuracy = locationRepository.getCachedAccuracy()
            val storeLatitude = locationRepository.getCachedStoreLatitude()
            val storeLongitude = locationRepository.getCachedStoreLongitude()

            val request = SubmitPriceRequest(
                productId = productId,
                placeId = placeId,
                placeName = placeName,
                value = priceValue,
                storeSelectionMethod = "geolocated",
                latitude = userLatitude,
                longitude = userLongitude,
                locationAccuracyMeters = accuracy?.toDouble(),
                storeLatitude = storeLatitude,
                storeLongitude = storeLongitude,
                confirmWarning = confirmWarning
            )

            when (val result = repository.submitPriceSubmission(request)) {
                is PriceSubmissionResult.Success -> {
                    _isPriceSubmitting.value = false
                    _priceSubmitSuccess.value = true
                    refreshProduct(gtin)
                }
                is PriceSubmissionResult.Warning -> {
                    _isPriceSubmitting.value = false
                    _priceWarning.value = result.warning
                }
                is PriceSubmissionResult.Error -> {
                    _isPriceSubmitting.value = false
                    _priceSubmitError.value = result.message
                }
            }
        }
    }

    private fun refreshProduct(gtin: String) {
        viewModelScope.launch {
            val placeId = getCachedPlaceId()
            when (val response = repository.getProductByGtin(gtin, placeId)) {
                is ApiResponse.Success -> {
                    _product.value = response.data
                }
                is ApiResponse.Error -> {
                    Log.e("ScanFlowViewModel", "Failed to refresh product: ${response.message}")
                }
            }
        }
    }

    fun loadNearbyPrices(gtin: String) {
        _nearbyPrices.value = null
        viewModelScope.launch {
            val lat = locationRepository.getCachedLatitude()
            val lng = locationRepository.getCachedLongitude()
            if (lat == null || lng == null) return@launch
            val excludePlaceId = getCachedPlaceId()
            val response = repository.getNearbyPrices(gtin, lat, lng, placeId = excludePlaceId)
            _nearbyPrices.postValue(response)
        }
    }

    fun loadProduct(gtin: String): Job? {
        if (currentGtin == gtin && pollingJob?.isActive == true) {
            return pollingJob
        }

        if (currentGtin == gtin && _product.value != null && _product.value?.state != ProductState.PENDING) {
            return null
        }

        pollingJob?.cancel()
        currentGtin = gtin

        pollingJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val placeId = getCachedPlaceId()

            while (true) {
                when (val response = repository.getProductByGtin(gtin, placeId)) {
                    is ApiResponse.Success -> {
                        _product.value = response.data
                        _isLoading.value = false

                        if (response.data.state == ProductState.PENDING) {
                            delay(2500)
                        } else {
                            break
                        }
                    }
                    is ApiResponse.Error -> {
                        _error.value = response.message
                        _isLoading.value = false
                        break
                    }
                }
            }
        }

        return pollingJob
    }

    fun cancelProductFetch() {
        pollingJob?.cancel()
        pollingJob = null
    }

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
        _priceWarning.value = null
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
