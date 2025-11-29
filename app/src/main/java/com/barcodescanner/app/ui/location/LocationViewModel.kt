package com.barcodescanner.app.ui.location

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.barcodescanner.app.data.location.LocationRepository
import com.barcodescanner.app.data.location.LocationState
import kotlinx.coroutines.launch

/**
 * ViewModel for location detection
 */
class LocationViewModel(private val repository: LocationRepository) : ViewModel() {
    
    private val _locationState = MutableLiveData<LocationState>()
    val locationState: LiveData<LocationState> = _locationState
    
    /**
     * Request location update
     */
    fun requestLocationUpdate() {
        viewModelScope.launch {
            repository.getCurrentStore().collect { state ->
                _locationState.value = state
                
                // Mark first launch as complete on success
                if (state is LocationState.Success) {
                    repository.setFirstLaunchComplete()
                }
            }
        }
    }
    
    /**
     * Handle permission result
     */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            requestLocationUpdate()
        } else {
            _locationState.value = LocationState.PermissionDenied
        }
    }
}
