package com.barcodescanner.app.ui.location

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.barcodescanner.app.LocationActivity
import com.barcodescanner.app.R
import com.barcodescanner.app.data.location.LocationRepository
import com.barcodescanner.app.data.location.LocationState
import com.barcodescanner.app.databinding.FragmentLocationLoadingBinding

/**
 * Fragment that handles location detection on first launch
 */
class LocationLoadingFragment : Fragment() {
    
    private var _binding: FragmentLocationLoadingBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: LocationViewModel
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.onPermissionResult(isGranted)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationLoadingBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize ViewModel with repository
        val repository = LocationRepository.getInstance(requireContext())
        viewModel = ViewModelProvider(
            this,
            LocationViewModelFactory(repository)
        )[LocationViewModel::class.java]
        
        setupUI()
        observeLocationState()
        
        // Request location permission
        requestLocationPermission()
    }
    
    private fun setupUI() {
        binding.btnGrantPermission.setOnClickListener {
            requestLocationPermission()
        }
        
        binding.btnContinue.setOnClickListener {
            navigateToMainApp()
        }
        
        binding.btnContinueNoStore.setOnClickListener {
            navigateToMainApp()
        }
    }
    
    private fun observeLocationState() {
        viewModel.locationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LocationState.Loading -> {
                    showLoading()
                }
                is LocationState.Success -> {
                    showSuccess(state.storeName)
                }
                is LocationState.NoStoreFound -> {
                    showNoStore()
                }
                is LocationState.PermissionDenied -> {
                    showPermissionDenied()
                }
            }
        }
    }
    
    private fun requestLocationPermission() {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    
    private fun showLoading() {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.successContainer.visibility = View.GONE
        binding.noStoreContainer.visibility = View.GONE
        binding.permissionContainer.visibility = View.GONE
    }
    
    private fun showSuccess(storeName: String) {
        binding.loadingContainer.visibility = View.GONE
        binding.successContainer.visibility = View.VISIBLE
        binding.noStoreContainer.visibility = View.GONE
        binding.permissionContainer.visibility = View.GONE
        binding.tvStoreName.text = storeName
    }
    
    private fun showNoStore() {
        binding.loadingContainer.visibility = View.GONE
        binding.successContainer.visibility = View.GONE
        binding.noStoreContainer.visibility = View.VISIBLE
        binding.permissionContainer.visibility = View.GONE
    }
    
    private fun showPermissionDenied() {
        binding.loadingContainer.visibility = View.GONE
        binding.successContainer.visibility = View.GONE
        binding.noStoreContainer.visibility = View.GONE
        binding.permissionContainer.visibility = View.VISIBLE
    }
    
    private fun navigateToMainApp() {
        // Mark first launch as complete
        val repository = LocationRepository.getInstance(requireContext())
        repository.setFirstLaunchComplete()
        
        // Navigate to MainActivity
        (requireActivity() as? LocationActivity)?.navigateToMainActivity()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * ViewModelFactory for LocationViewModel
 */
class LocationViewModelFactory(
    private val repository: LocationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocationViewModel::class.java)) {
            return LocationViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
