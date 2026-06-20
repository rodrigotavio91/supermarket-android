package com.barcodescanner.app.ui.product

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.barcodescanner.app.R
import com.barcodescanner.app.data.model.ApiResponse
import com.barcodescanner.app.data.model.NearbyPrice
import com.barcodescanner.app.data.model.PriceInfo
import com.barcodescanner.app.data.model.ProductState
import com.barcodescanner.app.databinding.FragmentProductDetailBinding
import kotlinx.coroutines.Job
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ProductDetailFragment : Fragment() {

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!

    private val args: ProductDetailFragmentArgs by navArgs()
    
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
    
    private val priceFormatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    
    // Polling job for API requests
    private var pollingJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupNavigation()
        
        // Load product data if not already loaded (shared ViewModel may have it already)
        // The loadProduct method is idempotent - it won't refetch if data exists
        pollingJob?.cancel()
        pollingJob = scanFlowViewModel.loadProduct(args.barcode)
    }

    private fun setupObservers() {
        scanFlowViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.contentScrollView.visibility = if (isLoading) View.GONE else View.VISIBLE
            
            // Update ProductInfoView with loading state
            binding.productInfoView.setProduct(
                scanFlowViewModel.product.value,
                args.barcode,
                isLoading
            )
        }

        scanFlowViewModel.product.observe(viewLifecycleOwner) { product ->
            // Update ProductInfoView
            binding.productInfoView.setProduct(
                product,
                args.barcode,
                scanFlowViewModel.isLoading.value ?: false
            )

            product?.let {
                // Show price history for all states
                showPriceHistory(it)
                // Load nearby prices
                scanFlowViewModel.loadNearbyPrices(args.barcode)
            }
        }

        scanFlowViewModel.nearbyPrices.observe(viewLifecycleOwner) { response ->
            when (response) {
                is ApiResponse.Success -> showNearbyPrices(response.data)
                else -> { /* ignore loading/error for nearby prices */ }
            }
        }
    }

    private fun setupNavigation() {
        // Setup toolbar back button
        binding.toolbar.setNavigationOnClickListener {
            navigateBack()
        }

        // Handle system back button
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateBack()
                }
            }
        )
    }

    private fun showPriceHistory(product: com.barcodescanner.app.data.model.Product) {
        binding.readyStateContent.visibility = View.VISIBLE

        Log.d(TAG, "Product todayPrice: ${product.todayPrice}")
        Log.d(TAG, "Product todayPrice value: ${product.todayPrice?.value}")
        Log.d(TAG, "Product todayPrice store: ${product.todayPrice?.store?.name}")
        Log.d(TAG, "Product todayPrice createdAt: ${product.todayPrice?.createdAt}")

        if (product.todayPrice != null) {
            Log.d(TAG, "Displaying today_price")
            val storeName = product.todayPrice.store.name.ifEmpty {
                scanFlowViewModel.getCachedStoreName() ?: "Voce"
            }
            binding.tvLastPriceStore.text = storeName
            binding.tvLastPriceTime.text = formatCreatedAt(product.todayPrice.createdAt)
            binding.tvLastPrice.text = priceFormatter.format(product.todayPrice.value)

            binding.pricesList.removeAllViews()
            val filteredPrices = product.myPrices.filter { it.id != product.todayPrice.id }
            if (filteredPrices.isEmpty()) {
                addEmptyPricesMessage()
            } else {
                filteredPrices.forEach { price ->
                    addMyPriceItem(price)
                }
            }
        } else if (args.userPrice > 0) {
            Log.d(TAG, "Displaying userPrice fallback: ${args.userPrice}")
            val storeName = scanFlowViewModel.getCachedStoreName() ?: "Voce"
            binding.tvLastPriceStore.text = storeName
            binding.tvLastPriceTime.text = "agora"
            binding.tvLastPrice.text = priceFormatter.format(args.userPrice.toDouble())

            binding.pricesList.removeAllViews()
            if (product.prices.isEmpty()) {
                addEmptyPricesMessage()
            } else {
                product.prices.forEach { priceInfo ->
                    addPriceItem(priceInfo)
                }
            }
        } else {
            Log.d(TAG, "Displaying static prices fallback")
            if (product.prices.isNotEmpty()) {
                val lastPrice = product.prices.first()
                binding.tvLastPriceStore.text = lastPrice.storeName
                binding.tvLastPriceTime.text = formatTimeAgo(lastPrice.timestamp)
                binding.tvLastPrice.text = priceFormatter.format(lastPrice.price)

                binding.pricesList.removeAllViews()
                val otherPrices = product.prices.drop(1)
                if (otherPrices.isEmpty()) {
                    addEmptyPricesMessage()
                } else {
                    otherPrices.forEach { priceInfo ->
                        addPriceItem(priceInfo)
                    }
                }
            }
        }
    }

    private fun addEmptyPricesMessage() {
        val messageText = TextView(requireContext()).apply {
            text = "Este e o primeiro preco registrado por voce"
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        binding.pricesList.addView(messageText)
    }

    private fun addMyPriceItem(price: com.barcodescanner.app.data.model.MyTodayPrice) {
        val todayPrice = scanFlowViewModel.product.value?.todayPrice?.value ?: 0.0
        val priceDiff = price.value - todayPrice
        
        // Determine comparison state
        val (indicator, comparisonColor, diffText) = when {
            priceDiff < -0.01 -> Triple("▼", R.color.price_lower, "Economizou ${priceFormatter.format(Math.abs(priceDiff))}")
            priceDiff > 0.01 -> Triple("▲", R.color.price_higher, "Pagou ${priceFormatter.format(priceDiff)} a mais")
            else -> Triple("=", R.color.price_equal, "Mesmo preço")
        }
        
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 16, 0, 16)
        }
        
        val leftContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        val storeName = TextView(requireContext()).apply {
            text = price.storeName ?: "Você"
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }
        
        val timeAgo = TextView(requireContext()).apply {
            text = formatCreatedAt(price.createdAt)
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }
        
        val comparisonText = TextView(requireContext()).apply {
            text = diffText
            textSize = 13f
            setTextColor(ContextCompat.getColor(requireContext(), comparisonColor))
            setPadding(0, 4, 0, 0)
        }
        
        val rightContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.END
        }
        
        val priceWithIndicator = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        val indicatorText = TextView(requireContext()).apply {
            text = indicator
            textSize = 18f
            setTextColor(ContextCompat.getColor(requireContext(), comparisonColor))
            setPadding(0, 0, 8, 0)
        }
        
        val priceText = TextView(requireContext()).apply {
            text = priceFormatter.format(price.value)
            textSize = 20f
            setTextColor(ContextCompat.getColor(requireContext(), comparisonColor))
        }
        
        priceWithIndicator.addView(indicatorText)
        priceWithIndicator.addView(priceText)
        
        leftContainer.addView(storeName)
        leftContainer.addView(timeAgo)
        leftContainer.addView(comparisonText)
        rightContainer.addView(priceWithIndicator)
        
        container.addView(leftContainer)
        container.addView(rightContainer)
        
        binding.pricesList.addView(container)
    }
    
    private fun addPriceItem(priceInfo: PriceInfo) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 20)
        }
        
        val leftContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        val storeName = TextView(requireContext()).apply {
            text = priceInfo.storeName
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }
        
        val timeAgo = TextView(requireContext()).apply {
            text = formatTimeAgo(priceInfo.timestamp)
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }
        
        val priceText = TextView(requireContext()).apply {
            text = priceFormatter.format(priceInfo.price)
            textSize = 20f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }
        
        leftContainer.addView(storeName)
        leftContainer.addView(timeAgo)
        container.addView(leftContainer)
        container.addView(priceText)
        
        binding.pricesList.addView(container)
    }

    private fun showNearbyPrices(nearbyPrices: List<NearbyPrice>) {
        val todayPrice = scanFlowViewModel.product.value?.todayPrice?.value

        if (nearbyPrices.isEmpty()) return

        Log.d(TAG, "Showing ${nearbyPrices.size} nearby prices")

        removeNearbyPricesSection()

        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply { topMargin = 24; bottomMargin = 24 }
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            tag = NEARBY_TAG
        }
        binding.pricesList.addView(divider)

        val header = TextView(requireContext()).apply {
            text = getString(R.string.product_nearby_prices_label)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            setPadding(0, 0, 0, 16)
            tag = NEARBY_TAG
        }
        binding.pricesList.addView(header)

        nearbyPrices.forEach { price ->
            addNearbyPriceItem(price, todayPrice)
        }
    }

    private fun addNearbyPriceItem(price: NearbyPrice, todayPrice: Double?) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 12, 0, 12)
            tag = NEARBY_TAG
        }

        val leftContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val storeName = TextView(requireContext()).apply {
            text = price.store.name
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }

        val distanceText = if (price.distanceMeters != null) {
            if (price.distanceMeters >= 1000) {
                "%.1f km".format(price.distanceMeters / 1000)
            } else {
                "%.0f m".format(price.distanceMeters)
            }
        } else {
            null
        }

        val details = TextView(requireContext()).apply {
            text = listOfNotNull(
                distanceText,
                price.lastSeenAt?.let { formatCreatedAt(it) }
            ).joinToString(" · ")
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }

        val rightContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.END
        }

        val priceText = TextView(requireContext()).apply {
            text = priceFormatter.format(price.value)
            textSize = 20f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }

        rightContainer.addView(priceText)

        if (todayPrice != null && todayPrice > 0.01) {
            val diff = price.value - todayPrice
            val (comparisonText, comparisonColor) = when {
                diff < -0.01 -> "${priceFormatter.format(Math.abs(diff))} mais barato" to R.color.price_lower
                diff > 0.01 -> "${priceFormatter.format(diff)} mais caro" to R.color.price_higher
                else -> "Mesmo preco" to R.color.price_equal
            }

            val diffText = TextView(requireContext()).apply {
                text = comparisonText
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), comparisonColor))
                setPadding(0, 4, 0, 0)
            }
            rightContainer.addView(diffText)
        }

        leftContainer.addView(storeName)
        details.text?.let {
            if (it.isNotEmpty()) leftContainer.addView(details)
        }

        container.addView(leftContainer)
        container.addView(rightContainer)

        binding.pricesList.addView(container)
    }

    private fun removeNearbyPricesSection() {
        val toRemove = mutableListOf<View>()
        for (i in 0 until binding.pricesList.childCount) {
            val child = binding.pricesList.getChildAt(i)
            if (child.tag == NEARBY_TAG) {
                toRemove.add(child)
            }
        }
        toRemove.forEach { binding.pricesList.removeView(it) }
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "agora"
            diff < TimeUnit.HOURS.toMillis(1) -> "há ${TimeUnit.MILLISECONDS.toMinutes(diff)} min"
            diff < TimeUnit.DAYS.toMillis(1) -> "há ${TimeUnit.MILLISECONDS.toHours(diff)} hora${if (TimeUnit.MILLISECONDS.toHours(diff) > 1) "s" else ""}"
            diff < TimeUnit.DAYS.toMillis(7) -> "há ${TimeUnit.MILLISECONDS.toDays(diff)} dia${if (TimeUnit.MILLISECONDS.toDays(diff) > 1) "s" else ""}"
            else -> "há mais de uma semana"
        }
    }
    
    private fun formatCreatedAt(createdAt: String): String {
        return try {
            // Parse ISO 8601 timestamp from API (e.g., "2025-11-29T10:30:00Z")
            val instant = java.time.Instant.parse(createdAt)
            val timestamp = instant.toEpochMilli()
            formatTimeAgo(timestamp)
        } catch (e: Exception) {
            // Fallback if parsing fails
            "agora"
        }
    }

    private fun navigateBack() {
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel API polling to prevent resource waste when view is destroyed
        pollingJob?.cancel()
        pollingJob = null
        _binding = null
    }
    
    companion object {
        private const val TAG = "ProductDetailFragment"
        private const val NEARBY_TAG = "nearby_section"
    }
}
