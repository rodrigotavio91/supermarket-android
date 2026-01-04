package com.barcodescanner.app.ui.price

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.barcodescanner.app.R
import com.barcodescanner.app.data.location.LocationRepository
import com.barcodescanner.app.data.location.LocationState
import com.barcodescanner.app.databinding.FragmentPriceInputBinding
import com.barcodescanner.app.ui.product.ScanFlowViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class PriceInputFragment : Fragment() {

    private var _binding: FragmentPriceInputBinding? = null
    private val binding get() = _binding!!

    private val args: PriceInputFragmentArgs by navArgs()
    private lateinit var locationRepository: LocationRepository
    
    // ATM-style currency mask
    private var currentCents: Long = 0
    private var isUpdating = false
    
    // Flags to track if UI components have been set up
    private var isPriceInputSetup = false
    private var isContinueButtonSetup = false
    
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
        
        locationRepository = LocationRepository.getInstance(requireContext())
        
        setupNavigation()
        checkLocationAndSetupUI()
    }
    
    private fun checkLocationAndSetupUI() {
        // Check if user has valid location
        val hasValidLocation = scanFlowViewModel.getCachedPlaceId() != null 
            && scanFlowViewModel.getCachedStoreName() != null
        
        // Always setup product observer and fetch product data
        setupProductObserver()
        scanFlowViewModel.loadProduct(args.barcode)
        
        if (hasValidLocation) {
            // Show location card with cached store name
            binding.locationCard.visibility = View.VISIBLE
            binding.tvStoreName.text = scanFlowViewModel.getCachedStoreName()
            
            // Show normal price input UI
            binding.priceInputContainer.visibility = View.VISIBLE
            binding.noLocationContainer.visibility = View.GONE
            binding.locationLoadingContainer.visibility = View.GONE
            
            setupPriceInput()
            setupContinueButton()
            
            // Still observe for updates (in case location changes)
            observeLocationUpdates()
        } else {
            // No cached location - start observing and show appropriate state
            // Hide everything initially, let the observer show the right state
            binding.locationCard.visibility = View.GONE
            binding.priceInputContainer.visibility = View.GONE
            binding.noLocationContainer.visibility = View.GONE
            binding.locationLoadingContainer.visibility = View.GONE
            
            // Start observing location updates - will show loading or result
            observeLocationUpdates()
        }
    }
    
    private fun observeLocationUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            locationRepository.getCurrentStore().collect { state ->
                when (state) {
                    is LocationState.Loading -> {
                        showLocationLoading()
                    }
                    is LocationState.Success -> {
                        showLocationSuccess(state.storeName)
                    }
                    is LocationState.NoStoreFound -> {
                        showNoLocation()
                    }
                    is LocationState.PermissionDenied -> {
                        showNoLocation()
                    }
                }
            }
        }
    }
    
    private fun showLocationLoading() {
        binding.locationCard.visibility = View.GONE
        binding.priceInputContainer.visibility = View.GONE
        binding.noLocationContainer.visibility = View.GONE
        binding.locationLoadingContainer.visibility = View.VISIBLE
    }
    
    private fun showLocationSuccess(storeName: String) {
        binding.tvStoreName.text = storeName
        
        binding.locationCard.visibility = View.VISIBLE
        binding.priceInputContainer.visibility = View.VISIBLE
        binding.noLocationContainer.visibility = View.GONE
        binding.locationLoadingContainer.visibility = View.GONE
        
        setupPriceInput()
        setupContinueButton()
    }
    
    private fun showNoLocation() {
        binding.locationCard.visibility = View.GONE
        binding.priceInputContainer.visibility = View.GONE
        binding.noLocationContainer.visibility = View.VISIBLE
        binding.locationLoadingContainer.visibility = View.GONE
        
        setupViewProductButton()
    }
    
    private fun setupViewProductButton() {
        binding.btnViewProduct.setOnClickListener {
            // Navigate to product detail without price
            val action = PriceInputFragmentDirections.actionPriceInputToProductDetail(
                barcode = args.barcode,
                userPrice = 0f
            )
            findNavController().navigate(action)
        }
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

    private fun setupProductObserver() {
        // Observe product loading state and product data
        scanFlowViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Update ProductInfoView with loading state
            binding.productInfoView.setProduct(
                scanFlowViewModel.product.value,
                args.barcode,
                isLoading
            )
        }
        
        // Observe product data
        scanFlowViewModel.product.observe(viewLifecycleOwner) { product ->
            // Update ProductInfoView with product data
            binding.productInfoView.setProduct(
                product,
                args.barcode,
                scanFlowViewModel.isLoading.value ?: false
            )
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

    private fun setupPriceInput() {
        // Only setup once to avoid duplicate listeners
        if (isPriceInputSetup) return
        isPriceInputSetup = true
        
        // Initialize with 0,00
        binding.etPrice.setText(formatCurrency(0))
        
        // Focus on price input when screen loads
        binding.etPrice.requestFocus()
        
        // Add ATM-style currency mask
        binding.etPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                
                isUpdating = true
                
                // Extract only digits from the input
                val digits = s.toString().replace(Regex("[^0-9]"), "")
                
                // Convert to cents
                currentCents = if (digits.isEmpty()) 0 else digits.toLongOrNull() ?: 0
                
                // Format and update the text
                val formatted = formatCurrency(currentCents)
                binding.etPrice.setText(formatted)
                binding.etPrice.setSelection(formatted.length)
                
                updateContinueButtonState()
                
                isUpdating = false
            }
        })
    }
    
    private fun formatCurrency(cents: Long): String {
        val value = cents.toDouble() / 100.0
        
        // Create Brazilian locale formatter
        val symbols = DecimalFormatSymbols(Locale("pt", "BR"))
        symbols.decimalSeparator = ','
        symbols.groupingSeparator = '.'
        
        val formatter = DecimalFormat("#,##0.00", symbols)
        return formatter.format(value)
    }

    private fun setupContinueButton() {
        // Only setup once to avoid duplicate listeners
        if (isContinueButtonSetup) return
        isContinueButtonSetup = true
        
        binding.btnContinue.isEnabled = false
        binding.btnContinue.setOnClickListener {
            submitPrice()
        }
    }

    private fun updateContinueButtonState() {
        binding.btnContinue.isEnabled = currentCents > 0
    }

    private fun submitPrice() {
        val price = currentCents.toDouble() / 100.0
        
        if (price <= 0) {
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
        // Not used anymore, but keeping for compatibility
        // Price is now managed via currentCents
        return if (currentCents > 0) currentCents.toDouble() / 100.0 else null
    }

    private fun navigateBack() {
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
