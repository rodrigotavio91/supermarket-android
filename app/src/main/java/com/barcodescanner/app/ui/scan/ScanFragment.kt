package com.barcodescanner.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.barcodescanner.app.R
import com.barcodescanner.app.databinding.FragmentScanBinding
import com.barcodescanner.app.ui.product.ScanFlowViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var lastScannedCode: String? = null
    private var lastScannedTime: Long = 0
    private var isNavigating: Boolean = false
    
    // Shared ViewModel scoped to navigation_scan graph
    // Initialize lazily to ensure fragment is attached to navigation graph
    private val scanFlowViewModel: ScanFlowViewModel by lazy {
        val navController = findNavController()
        val navBackStackEntry = navController.getBackStackEntry(R.id.navigation_scan)
        ViewModelProvider(
            navBackStackEntry,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[ScanFlowViewModel::class.java]
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            showPermissionDenied()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnRequestPermission.setOnClickListener {
            requestCameraPermission()
        }
        
        // Observe loading state to show/hide overlay
        scanFlowViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showPermissionDenied() {
        binding.previewView.visibility = View.GONE
        binding.scannerOverlay.visibility = View.GONE
        binding.scanInstruction.visibility = View.GONE
        binding.permissionContainer.visibility = View.VISIBLE
    }

    private fun startCamera() {
        binding.previewView.visibility = View.VISIBLE
        binding.scannerOverlay.visibility = View.VISIBLE
        binding.scanInstruction.visibility = View.VISIBLE
        binding.permissionContainer.visibility = View.GONE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // Wait for the overlay to be laid out before creating the analyzer
            binding.scannerOverlay.post {
                // Get scan frame from overlay and view dimensions
                val scanFrame = binding.scannerOverlay.getScanFrameRect()
                val viewWidth = binding.scannerOverlay.width
                val viewHeight = binding.scannerOverlay.height
                
                // Validate view dimensions before creating analyzer
                if (viewWidth <= 0 || viewHeight <= 0) {
                    Log.e(TAG, "Invalid view dimensions: width=$viewWidth, height=$viewHeight")
                    Toast.makeText(
                        requireContext(),
                        "Erro ao configurar scanner",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@post
                }
                
                // Image analyzer with scan frame boundaries
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            cameraExecutor, 
                            BarcodeAnalyzer(viewWidth, viewHeight, scanFrame) { barcode ->
                                handleBarcodeDetected(barcode)
                            }
                        )
                    }

                // Select back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )

                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                    Toast.makeText(
                        requireContext(),
                        "Erro ao iniciar câmera",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun handleBarcodeDetected(barcode: String) {
        val currentTime = System.currentTimeMillis()
        
        // Debounce: only process if different code or 2 seconds passed
        if (barcode != lastScannedCode || currentTime - lastScannedTime > 2000) {
            lastScannedCode = barcode
            lastScannedTime = currentTime
            
            // Prevent multiple navigation attempts
            if (isNavigating) {
                Log.d(TAG, "Already navigating, skipping barcode: $barcode")
                return
            }
            
            viewLifecycleOwner.lifecycleScope.launch {
                Log.d(TAG, "GTIN Code detected: $barcode")
                
                // Check lifecycle state before navigation to prevent crash
                if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    Log.w(TAG, "Fragment not in valid state for navigation, skipping")
                    return@launch
                }
                
                // Check if fragment is still attached
                if (!isAdded) {
                    Log.w(TAG, "Fragment not attached, skipping")
                    return@launch
                }
                
                try {
                    isNavigating = true
                    
                    // Reset ViewModel state for new scan
                    scanFlowViewModel.reset()
                    
                    // Load product to check if myTodayPrice exists
                    scanFlowViewModel.loadProduct(barcode)
                    
                    // Use one-time observers to avoid memory leaks
                    scanFlowViewModel.product.observe(viewLifecycleOwner) { product ->
                        // Only navigate once when we get the first product response
                        if (product != null && isNavigating) {
                            // Remove observers immediately to prevent multiple calls
                            scanFlowViewModel.product.removeObservers(viewLifecycleOwner)
                            scanFlowViewModel.error.removeObservers(viewLifecycleOwner)
                            
                            // Final lifecycle and attachment check before navigation
                            if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                Log.w(TAG, "Fragment not in valid state for navigation, skipping")
                                isNavigating = false
                                return@observe
                            }
                            
                            if (!isAdded) {
                                Log.w(TAG, "Fragment not attached, skipping navigation")
                                isNavigating = false
                                return@observe
                            }
                            
                            try {
                                if (product.myTodayPrice != null) {
                                    // User already entered a price today - skip price input
                                    Log.d(TAG, "myTodayPrice exists, navigating directly to product detail")
                                    val action = ScanFragmentDirections.actionScanToProductDetail(
                                        barcode = barcode,
                                        userPrice = 0f
                                    )
                                    findNavController().navigate(action)
                                } else {
                                    // No price entered today - show price input
                                    Log.d(TAG, "myTodayPrice is null, navigating to price input")
                                    val action = ScanFragmentDirections.actionScanToPriceInput(barcode)
                                    findNavController().navigate(action)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Navigation failed", e)
                                isNavigating = false
                            }
                        }
                    }
                    
                    // Observe errors to reset navigation state on API failures
                    scanFlowViewModel.error.observe(viewLifecycleOwner) { errorMessage ->
                        if (errorMessage != null && isNavigating) {
                            Log.e(TAG, "API error during scan: $errorMessage")
                            // Remove observers to prevent leaks
                            scanFlowViewModel.product.removeObservers(viewLifecycleOwner)
                            scanFlowViewModel.error.removeObservers(viewLifecycleOwner)
                            
                            // Reset navigation state so user can scan again
                            isNavigating = false
                            
                            // Show error feedback to user
                            if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    errorMessage,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling barcode scan", e)
                    isNavigating = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset loading state when returning to scan fragment
        // This ensures the overlay is hidden if user navigates back
        binding.loadingOverlay.visibility = View.GONE
        // Reset navigation flag when returning to fragment
        isNavigating = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up observers to prevent leaks
        scanFlowViewModel.product.removeObservers(viewLifecycleOwner)
        scanFlowViewModel.error.removeObservers(viewLifecycleOwner)
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "ScanFragment"
    }
}
