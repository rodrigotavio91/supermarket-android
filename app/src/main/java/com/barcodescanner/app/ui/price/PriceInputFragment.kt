package com.barcodescanner.app.ui.price

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.barcodescanner.app.R
import com.barcodescanner.app.data.location.LocationRepository
import com.barcodescanner.app.data.location.LocationState
import com.barcodescanner.app.data.model.ProductState
import com.barcodescanner.app.databinding.FragmentPriceInputBinding
import com.barcodescanner.app.ui.product.ScanFlowViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class PriceInputFragment : Fragment() {

    private var _binding: FragmentPriceInputBinding? = null
    private val binding get() = _binding!!

    private val args: PriceInputFragmentArgs by navArgs()
    private lateinit var locationRepository: LocationRepository
    
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPriceInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        locationRepository = LocationRepository(requireContext())
        
        setupNavigation()
        setupStoreDisplay()
        setupProductObserver()
        setupPriceInput()
        setupContinueButton()
        
        // Start fetching product data in background
        scanFlowViewModel.loadProduct(args.barcode)
    }

    private fun setupNavigation() {
        binding.toolbar.setNavigationOnClickListener {
            navigateBack()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateBack()
                }
            }
        )
    }

    private fun setupStoreDisplay() {
        // Check if we should refresh the location based on cache expiration
        viewLifecycleOwner.lifecycleScope.launch {
            locationRepository.getCurrentStore().collect { state ->
                when (state) {
                    is LocationState.Success -> {
                        binding.tvStoreName.text = state.storeName
                    }
                    is LocationState.NoStoreFound -> {
                        binding.tvStoreName.text = "Localização não identificada"
                    }
                    is LocationState.PermissionDenied -> {
                        binding.tvStoreName.text = "Localização não identificada"
                    }
                    is LocationState.Loading -> {
                        // Keep current store name while loading
                    }
                }
            }
        }
    }
    
    private fun setupProductObserver() {
        // Observe product loading state
        scanFlowViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                showLoadingState()
            }
        }
        
        // Observe product data
        scanFlowViewModel.product.observe(viewLifecycleOwner) { product ->
            if (product != null) {
                when (product.state) {
                    ProductState.READY -> {
                        // Show product name
                        showProductName(product.name ?: args.barcode)
                    }
                    ProductState.PENDING -> {
                        // Show GTIN with pending message
                        showPendingState()
                    }
                    ProductState.NOT_FOUND -> {
                        // Show GTIN with "new product" message
                        showNotFoundState()
                    }
                }
            }
        }
        
        // Observe price submission state
        scanFlowViewModel.isPriceSubmitting.observe(viewLifecycleOwner) { isSubmitting ->
            if (isSubmitting) {
                binding.btnContinue.isEnabled = false
                binding.btnContinue.text = "Salvando..."
            }
        }
        
        // Observe price submission success
        scanFlowViewModel.priceSubmitSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                // Navigate to product detail without userPrice (we'll use my_current_price from API)
                val action = PriceInputFragmentDirections.actionPriceInputToProductDetail(
                    barcode = args.barcode,
                    userPrice = 0f // Not needed anymore, we have my_current_price
                )
                findNavController().navigate(action)
            }
        }
        
        // Observe price submission error
        scanFlowViewModel.priceSubmitError.observe(viewLifecycleOwner) { error ->
            error?.let {
                binding.btnContinue.isEnabled = true
                binding.btnContinue.text = "Continuar"
                Snackbar.make(
                    binding.root,
                    it,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun showLoadingState() {
        binding.loadingStateContainer.isVisible = true
        binding.tvProductName.isVisible = false
        binding.pendingStateContainer.isVisible = false
        binding.notFoundStateContainer.isVisible = false
    }
    
    private fun showProductName(name: String) {
        binding.loadingStateContainer.isVisible = false
        binding.tvProductName.isVisible = true
        binding.tvProductName.text = name
        binding.pendingStateContainer.isVisible = false
        binding.notFoundStateContainer.isVisible = false
    }
    
    private fun showPendingState() {
        binding.loadingStateContainer.isVisible = false
        binding.tvProductName.isVisible = false
        binding.pendingStateContainer.isVisible = true
        binding.tvProductGtinPending.text = getString(R.string.price_input_product_label) + " " + args.barcode
        binding.notFoundStateContainer.isVisible = false
    }
    
    private fun showNotFoundState() {
        binding.loadingStateContainer.isVisible = false
        binding.tvProductName.isVisible = false
        binding.pendingStateContainer.isVisible = false
        binding.notFoundStateContainer.isVisible = true
        binding.tvProductGtinNotFound.text = getString(R.string.price_input_product_label) + " " + args.barcode
    }

    private fun setupPriceInput() {
        // Focus on price input when screen loads
        binding.etPrice.requestFocus()
        
        // Add text watcher to enable/disable continue button
        binding.etPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateContinueButtonState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupContinueButton() {
        binding.btnContinue.isEnabled = false
        binding.btnContinue.setOnClickListener {
            submitPrice()
        }
    }

    private fun updateContinueButtonState() {
        val priceText = binding.etPrice.text?.toString() ?: ""
        binding.btnContinue.isEnabled = priceText.isNotBlank() && parsePrice(priceText) != null
    }

    private fun submitPrice() {
        val priceText = binding.etPrice.text?.toString() ?: ""
        val price = parsePrice(priceText)
        
        if (price == null) {
            Snackbar.make(
                binding.root,
                "Por favor, digite um preço válido",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        
        // Disable button and show loading state
        binding.btnContinue.isEnabled = false
        binding.btnContinue.text = "Salvando..."
        
        // Submit price to API
        scanFlowViewModel.submitPrice(args.barcode, price)
    }

    private fun parsePrice(text: String): Double? {
        return try {
            // Replace comma with dot for decimal separator
            val normalized = text.replace(",", ".")
            val price = normalized.toDoubleOrNull()
            if (price != null && price > 0) price else null
        } catch (e: Exception) {
            null
        }
    }

    private fun navigateBack() {
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
