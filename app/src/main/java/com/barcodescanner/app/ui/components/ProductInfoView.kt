package com.barcodescanner.app.ui.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.barcodescanner.app.R
import com.barcodescanner.app.data.model.Product
import com.barcodescanner.app.data.model.ProductState
import com.barcodescanner.app.databinding.ViewProductInfoBinding

/**
 * Unified component for displaying product information across different screens
 * Handles all product states: LOADING, PENDING, READY, NOT_FOUND
 * Includes animated messages for PENDING state
 */
class ProductInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewProductInfoBinding
    
    // Animation handler for pending messages
    private val animationHandler = Handler(Looper.getMainLooper())
    private var messageAnimationRunnable: Runnable? = null
    private var currentMessageIndex = 0
    private val pendingMessages: Array<String>
    private var isAnimating = false
    
    private var currentGtin: String? = null

    init {
        orientation = VERTICAL
        
        binding = ViewProductInfoBinding.inflate(
            LayoutInflater.from(context),
            this
        )
        
        // Load pending messages from resources
        pendingMessages = context.resources.getStringArray(R.array.pending_messages)
    }

    /**
     * Update the view based on product state
     * @param product The product data (null shows loading state)
     * @param gtin The GTIN code to display for PENDING and NOT_FOUND states
     * @param isLoading Whether data is being loaded
     */
    fun setProduct(product: Product?, gtin: String, isLoading: Boolean = false) {
        currentGtin = gtin
        
        when {
            isLoading && product == null -> showLoadingState()
            product == null -> showLoadingState()
            else -> {
                when (product.state) {
                    ProductState.READY -> showReadyState(product.name ?: gtin)
                    ProductState.PENDING -> showPendingState(gtin)
                    ProductState.NOT_FOUND -> showNotFoundState(gtin)
                }
            }
        }
    }

    private fun showLoadingState() {
        stopMessageAnimation()
        binding.loadingStateContainer.isVisible = true
        binding.tvProductName.isVisible = false
        binding.pendingStateContainer.isVisible = false
        binding.notFoundStateContainer.isVisible = false
    }

    private fun showReadyState(name: String) {
        stopMessageAnimation()
        binding.loadingStateContainer.isVisible = false
        binding.tvProductName.isVisible = true
        binding.tvProductName.text = name
        binding.pendingStateContainer.isVisible = false
        binding.notFoundStateContainer.isVisible = false
    }

    private fun showPendingState(gtin: String) {
        binding.loadingStateContainer.isVisible = false
        binding.tvProductName.isVisible = false
        binding.pendingStateContainer.isVisible = true
        binding.tvProductGtinPending.text = context.getString(R.string.price_input_product_label) + " " + gtin
        binding.notFoundStateContainer.isVisible = false
        
        // Start message animation only if not already running
        if (messageAnimationRunnable == null) {
            startMessageAnimation()
        }
    }

    private fun showNotFoundState(gtin: String) {
        stopMessageAnimation()
        binding.loadingStateContainer.isVisible = false
        binding.tvProductName.isVisible = false
        binding.pendingStateContainer.isVisible = false
        binding.notFoundStateContainer.isVisible = true
        binding.tvProductGtinNotFound.text = context.getString(R.string.price_input_product_label) + " " + gtin
    }

    private fun startMessageAnimation() {
        // Set the first message immediately
        currentMessageIndex = 0
        if (pendingMessages.isEmpty()) {
            binding.tvAnimatedMessage.text = context.getString(R.string.app_name)
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
                binding.tvAnimatedMessage.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        if (!isAnimating) return@withEndAction
                        
                        // Change text after fade out
                        currentMessageIndex = (currentMessageIndex + 1) % pendingMessages.size
                        binding.tvAnimatedMessage.text = pendingMessages[currentMessageIndex]
                        
                        // Fade in
                        binding.tvAnimatedMessage.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .withEndAction {
                                // Schedule next message change after display time
                                if (isAnimating) {
                                    animationHandler.postDelayed(this, 1400) // 1.4s display time
                                }
                            }
                            .start()
                    }
                    .start()
            }
        }
        
        // Start the animation loop after a short delay to let user read the first message
        messageAnimationRunnable?.let { animationHandler.postDelayed(it, 2000) }
    }

    private fun stopMessageAnimation() {
        isAnimating = false
        messageAnimationRunnable?.let { animationHandler.removeCallbacks(it) }
        messageAnimationRunnable = null
        // Cancel any ongoing view animations
        binding.tvAnimatedMessage.animate().cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Stop animations and cancel all pending handlers
        stopMessageAnimation()
        animationHandler.removeCallbacksAndMessages(null)
    }
}
