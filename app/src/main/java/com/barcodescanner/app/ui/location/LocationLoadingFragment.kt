package com.barcodescanner.app.ui.location

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.barcodescanner.app.LocationActivity
import com.barcodescanner.app.databinding.FragmentLocationLoadingBinding

/**
 * Fragment that handles location permission request.
 * 
 * Shows a permission request screen with two states:
 * 1. Can request permission - Shows "Grant Permission" button that triggers system dialog
 * 2. Cannot request (permanent denial) - Shows "Open Settings" button
 * 
 * Automatically navigates to MainActivity when permission is granted.
 */
class LocationLoadingFragment : Fragment() {
    
    private var _binding: FragmentLocationLoadingBinding? = null
    private val binding get() = _binding!!
    
    // Track if we've requested permission at least once in this session
    // Used to distinguish "never asked" from "permanently denied"
    private var hasRequestedPermission = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasRequestedPermission = true
        if (isGranted) {
            navigateToMainApp()
        } else {
            // Permission denied - update UI based on whether we can ask again
            updatePermissionUI()
        }
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
        setupUI()
    }
    
    override fun onResume() {
        super.onResume()
        // Re-check permission when returning from Settings
        if (hasLocationPermission()) {
            navigateToMainApp()
        } else {
            updatePermissionUI()
        }
    }
    
    private fun setupUI() {
        binding.btnGrantPermission.setOnClickListener {
            if (canRequestPermission()) {
                requestLocationPermission()
            } else {
                openAppSettings()
            }
        }
    }
    
    private fun updatePermissionUI() {
        binding.permissionContainer.visibility = View.VISIBLE
        
        if (canRequestPermission()) {
            // Can still request permission - show grant button
            binding.tvPermissionTitle.text = getString(com.barcodescanner.app.R.string.location_permission_title)
            binding.tvPermissionMessage.text = getString(com.barcodescanner.app.R.string.location_permission_message)
            binding.btnGrantPermission.text = getString(com.barcodescanner.app.R.string.location_grant_permission)
        } else {
            // Permanently denied - show settings button
            binding.tvPermissionTitle.text = getString(com.barcodescanner.app.R.string.location_permission_denied_title)
            binding.tvPermissionMessage.text = getString(com.barcodescanner.app.R.string.location_permission_denied_message)
            binding.btnGrantPermission.text = getString(com.barcodescanner.app.R.string.location_open_settings)
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun canRequestPermission(): Boolean {
        // If we haven't requested yet, we can request
        if (!hasRequestedPermission) {
            return true
        }
        // If shouldShowRequestPermissionRationale returns true, we can still ask
        return shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    
    private fun requestLocationPermission() {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }
    
    private fun navigateToMainApp() {
        (requireActivity() as? LocationActivity)?.navigateToMainActivity()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
