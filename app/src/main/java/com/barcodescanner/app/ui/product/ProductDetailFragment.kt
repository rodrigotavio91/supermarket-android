package com.barcodescanner.app.ui.product

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.barcodescanner.app.R
import com.barcodescanner.app.data.model.PriceInfo
import com.barcodescanner.app.data.model.ProductState
import com.barcodescanner.app.databinding.FragmentProductDetailBinding
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ProductDetailFragment : Fragment() {

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!

    private val args: ProductDetailFragmentArgs by navArgs()
    private val viewModel: ProductDetailViewModel by viewModels()
    
    private val priceFormatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    
    // Animation handler for pending messages
    private val animationHandler = Handler(Looper.getMainLooper())
    private var messageAnimationRunnable: Runnable? = null
    private var currentMessageIndex = 0
    private lateinit var pendingMessages: Array<String>

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
        
        // Load product data
        viewModel.loadProduct(args.barcode)
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.contentScrollView.visibility = if (isLoading) View.GONE else View.VISIBLE
        }

        viewModel.product.observe(viewLifecycleOwner) { product ->
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

        // Setup back button click
        binding.btnBack.setOnClickListener {
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
        
        binding.tvProductName.text = product.name
        binding.tvProductBrand.text = product.brand
        binding.tvProductCategory.text = product.category
        binding.tvBarcodeReady.text = product.gtin
        
        // Display prices
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
        binding.tvAnimatedMessage.text = pendingMessages[currentMessageIndex]
        binding.tvAnimatedMessage.alpha = 1f
        
        // Setup the animation runnable
        messageAnimationRunnable = object : Runnable {
            override fun run() {
                // Fade out
                ObjectAnimator.ofFloat(binding.tvAnimatedMessage, "alpha", 1f, 0f).apply {
                    duration = 300
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
                
                // Change text after fade out
                animationHandler.postDelayed({
                    currentMessageIndex = (currentMessageIndex + 1) % pendingMessages.size
                    binding.tvAnimatedMessage.text = pendingMessages[currentMessageIndex]
                    
                    // Fade in
                    ObjectAnimator.ofFloat(binding.tvAnimatedMessage, "alpha", 0f, 1f).apply {
                        duration = 300
                        interpolator = AccelerateDecelerateInterpolator()
                        start()
                    }
                }, 300)
                
                // Schedule next message change (2 seconds total: 0.3s fade out + 1.4s display + 0.3s fade in)
                animationHandler.postDelayed(this, 2000)
            }
        }
        
        // Start the animation loop after a short delay to let user read the first message
        messageAnimationRunnable?.let { animationHandler.postDelayed(it, 2000) }
    }
    
    private fun stopMessageAnimation() {
        messageAnimationRunnable?.let { animationHandler.removeCallbacks(it) }
        messageAnimationRunnable = null
    }
    
    private fun addPriceItem(priceInfo: PriceInfo) {
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
            textSize = 18f
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
            diff < TimeUnit.HOURS.toMillis(1) -> "há ${TimeUnit.MILLISECONDS.toMinutes(diff)} min"
            diff < TimeUnit.DAYS.toMillis(1) -> "há ${TimeUnit.MILLISECONDS.toHours(diff)} hora${if (TimeUnit.MILLISECONDS.toHours(diff) > 1) "s" else ""}"
            diff < TimeUnit.DAYS.toMillis(7) -> "há ${TimeUnit.MILLISECONDS.toDays(diff)} dia${if (TimeUnit.MILLISECONDS.toDays(diff) > 1) "s" else ""}"
            else -> "há mais de uma semana"
        }
    }

    private fun navigateBack() {
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopMessageAnimation()
        _binding = null
    }
}
