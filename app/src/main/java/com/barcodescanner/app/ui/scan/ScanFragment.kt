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
            
            viewLifecycleOwner.lifecycleScope.launch {
                Log.d(TAG, "GTIN Code detected: $barcode")
                
                // Check lifecycle state before navigation to prevent crash
                if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    Log.w(TAG, "Fragment not in valid state for navigation, skipping")
                    return@launch
                }
                
                try {
                    // Reset ViewModel state for new scan
                    scanFlowViewModel.reset()
                    
                    // Load product to check if myTodayPrice exists
                    scanFlowViewModel.loadProduct(barcode)
                    
                    // Observe product data (one-time observation for navigation decision)
                    var hasNavigated = false
                    scanFlowViewModel.product.observe(viewLifecycleOwner) { product ->
                        // Only navigate once when we get the first product response
                        if (!hasNavigated && product != null) {
                            hasNavigated = true
                            
                            // Check lifecycle state again before navigation
                            if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                Log.w(TAG, "Fragment not in valid state for navigation, skipping")
                                return@observe
                            }
                            
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
                        }
                    }
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Navigation failed - fragment may be detached", e)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset loading state when returning to scan fragment
        // This ensures the overlay is hidden if user navigates back
        binding.loadingOverlay.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "ScanFragment"
    }
}
