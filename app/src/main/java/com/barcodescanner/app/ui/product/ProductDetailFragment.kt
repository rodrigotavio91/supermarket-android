package com.barcodescanner.app.ui.product

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    
    // Animation handler for pending messages
    private val animationHandler = Handler(Looper.getMainLooper())
    private var messageAnimationRunnable: Runnable? = null
    private var currentMessageIndex = 0
    private lateinit var pendingMessages: Array<String>
    private var isAnimating = false
    
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

        // Load pending messages from resources
        pendingMessages = resources.getStringArray(R.array.pending_messages)

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
        }

        scanFlowViewModel.product.observe(viewLifecycleOwner) { product ->
            product?.let {
                when (it.state) {
                    ProductState.PENDING -> showPendingState()
                    ProductState.READY -> showReadyState(it)
                    ProductState.NOT_FOUND -> showNotFoundState()
                }
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

    private fun showPendingState() {
        binding.pendingStateContent.visibility = View.VISIBLE
        binding.readyStateContent.visibility = View.GONE
        binding.notFoundStateContent.visibility = View.GONE
        
        // Start message animation only if not already running
        if (messageAnimationRunnable == null) {
            startMessageAnimation()
        }
    }

    private fun showReadyState(product: com.barcodescanner.app.data.model.Product) {
        binding.pendingStateContent.visibility = View.GONE
        binding.readyStateContent.visibility = View.VISIBLE
        binding.notFoundStateContent.visibility = View.GONE
        
        // Stop message animation
        stopMessageAnimation()
        
        binding.tvProductName.text = product.name ?: "Unknown Product"
        
        // Debug logging
        Log.d(TAG, "Product myTodayPrice: ${product.myTodayPrice}")
        Log.d(TAG, "Product myTodayPrice value: ${product.myTodayPrice?.value}")
        Log.d(TAG, "Product myTodayPrice storeName: ${product.myTodayPrice?.storeName}")
        Log.d(TAG, "Product myTodayPrice createdAt: ${product.myTodayPrice?.createdAt}")
        
        // Display my_today_price from API if available
        if (product.myTodayPrice != null) {
            Log.d(TAG, "Displaying my_today_price")
            // Use store_name from my_today_price, fallback to cached store name
            val storeName = product.myTodayPrice.storeName ?: scanFlowViewModel.getCachedStoreName() ?: "Você"
            binding.tvLastPriceStore.text = storeName
            binding.tvLastPriceTime.text = formatCreatedAt(product.myTodayPrice.createdAt)
            binding.tvLastPrice.text = priceFormatter.format(product.myTodayPrice.value)
            
            // Display my_prices history (excluding today's price which is shown above)
            binding.pricesList.removeAllViews()
            product.myPrices
                .filter { it.id != product.myTodayPrice.id } // Exclude today's price
                .forEach { price ->
                    addMyPriceItem(price)
                }
        } else if (args.userPrice > 0) {
            Log.d(TAG, "Displaying userPrice fallback: ${args.userPrice}")
            // Fallback to userPrice arg if my_current_price is not available (shouldn't happen with new flow)
            val storeName = scanFlowViewModel.getCachedStoreName() ?: "Você"
            binding.tvLastPriceStore.text = storeName
            binding.tvLastPriceTime.text = "agora"
            binding.tvLastPrice.text = priceFormatter.format(args.userPrice.toDouble())
            
            // Display other prices from the product data
            binding.pricesList.removeAllViews()
            product.prices.forEach { priceInfo ->
                addPriceItem(priceInfo)
            }
        } else {
            Log.d(TAG, "Displaying static prices fallback")
            // Fallback to original behavior if no user price
            if (product.prices.isNotEmpty()) {
                val lastPrice = product.prices.first()
                binding.tvLastPriceStore.text = lastPrice.storeName
                binding.tvLastPriceTime.text = formatTimeAgo(lastPrice.timestamp)
                binding.tvLastPrice.text = priceFormatter.format(lastPrice.price)
                
                // Display other prices
                binding.pricesList.removeAllViews()
                product.prices.drop(1).forEach { priceInfo ->
                    addPriceItem(priceInfo)
                }
            }
        }
    }

    private fun showNotFoundState() {
        binding.pendingStateContent.visibility = View.GONE
        binding.readyStateContent.visibility = View.GONE
        binding.notFoundStateContent.visibility = View.VISIBLE
        
        // Stop message animation
        stopMessageAnimation()
    }
    
    private fun startMessageAnimation() {
        // Set the first message immediately
        currentMessageIndex = 0
        if (pendingMessages.isEmpty()) {
            binding.tvAnimatedMessage.text = getString(R.string.app_name)
            return
        }
        
        binding.tvAnimatedMessage.text = pendingMessages[currentMessageIndex]
        binding.tvAnimatedMessage.alpha = 1f
        isAnimating = true
        
        // Setup the animation runnable
        messageAnimationRunnable = object : Runnable {
            override fun run() {
                if (!isAnimating) return
                
                // Fade out using ViewPropertyAnimator (hardware-accelerated)
                binding?.tvAnimatedMessage?.animate()
                    ?.alpha(0f)
                    ?.setDuration(300)
                    ?.withEndAction {
                        if (!isAnimating) return@withEndAction
                        
                        // Null-safe check for binding
                        _binding?.let { safeBinding ->
                            // Change text after fade out
                            currentMessageIndex = (currentMessageIndex + 1) % pendingMessages.size
                            safeBinding.tvAnimatedMessage.text = pendingMessages[currentMessageIndex]
                            
                            // Fade in
                            safeBinding.tvAnimatedMessage.animate()
                                .alpha(1f)
                                .setDuration(300)
                                .withEndAction {
                                    // Schedule next message change after display time
                                    if (isAnimating && _binding != null) {
                                        animationHandler.postDelayed(this, 1400) // 1.4s display time
                                    }
                                }
                                .start()
                        }
                    }
                    ?.start()
            }
        }
        
        // Start the animation loop after a short delay to let user read the first message
        messageAnimationRunnable?.let { animationHandler.postDelayed(it, 2000) }
    }
    
    private fun stopMessageAnimation() {
        isAnimating = false
        messageAnimationRunnable?.let { animationHandler.removeCallbacks(it) }
        messageAnimationRunnable = null
        // Cancel any ongoing view animations with null-safe check
        _binding?.tvAnimatedMessage?.animate()?.cancel()
    }
    
    private fun addMyPriceItem(price: com.barcodescanner.app.data.model.MyTodayPrice) {
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
            text = price.storeName ?: "Você"
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }
        
        val timeAgo = TextView(requireContext()).apply {
            text = formatCreatedAt(price.createdAt)
            textSize = 12f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }
        
        val priceText = TextView(requireContext()).apply {
            text = priceFormatter.format(price.value)
            textSize = 20f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }
        
        leftContainer.addView(storeName)
        leftContainer.addView(timeAgo)
        container.addView(leftContainer)
        container.addView(priceText)
        
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
        // Stop animations and cancel all pending handlers
        stopMessageAnimation()
        animationHandler.removeCallbacksAndMessages(null)
        _binding = null
    }
    
    companion object {
        private const val TAG = "ProductDetailFragment"
    }
}
