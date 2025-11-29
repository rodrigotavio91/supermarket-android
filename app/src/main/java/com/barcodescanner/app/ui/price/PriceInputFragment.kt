package com.barcodescanner.app.ui.price

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.barcodescanner.app.data.location.LocationRepository
import com.barcodescanner.app.databinding.FragmentPriceInputBinding
import com.google.android.material.snackbar.Snackbar

class PriceInputFragment : Fragment() {

    private var _binding: FragmentPriceInputBinding? = null
    private val binding get() = _binding!!

    private val args: PriceInputFragmentArgs by navArgs()
    private lateinit var locationRepository: LocationRepository

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
        setupPriceInput()
        setupContinueButton()
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
        val storeName = locationRepository.getCachedStore() ?: "Loja Desconhecida"
        binding.tvStoreName.text = storeName
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
        
        // Navigate to product detail with price
        val action = PriceInputFragmentDirections.actionPriceInputToProductDetail(
            barcode = args.barcode,
            userPrice = price.toFloat()
        )
        findNavController().navigate(action)
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
