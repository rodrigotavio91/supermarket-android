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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    private var currentCents: Long = 0
    private var isUpdating = false

    private var isPriceInputSetup = false
    private var isContinueButtonSetup = false

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
        val hasValidLocation = scanFlowViewModel.getCachedPlaceId() != null
            && scanFlowViewModel.getCachedStoreName() != null

        setupProductObserver()
        scanFlowViewModel.loadProduct(args.barcode)

        if (hasValidLocation) {
            binding.locationCard.visibility = View.VISIBLE
            binding.tvStoreName.text = scanFlowViewModel.getCachedStoreName()

            binding.priceInputContainer.visibility = View.VISIBLE
            binding.noLocationContainer.visibility = View.GONE
            binding.locationLoadingContainer.visibility = View.GONE

            setupPriceInput()
            setupContinueButton()

            observeLocationUpdates()
        } else {
            binding.locationCard.visibility = View.GONE
            binding.priceInputContainer.visibility = View.GONE
            binding.noLocationContainer.visibility = View.GONE
            binding.locationLoadingContainer.visibility = View.GONE

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
        scanFlowViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.productInfoView.setProduct(
                scanFlowViewModel.product.value,
                args.barcode,
                isLoading
            )
        }

        scanFlowViewModel.product.observe(viewLifecycleOwner) { product ->
            binding.productInfoView.setProduct(
                product,
                args.barcode,
                scanFlowViewModel.isLoading.value ?: false
            )
        }

        scanFlowViewModel.isPriceSubmitting.observe(viewLifecycleOwner) { isSubmitting ->
            if (isSubmitting) {
                binding.btnContinue.isEnabled = false
                binding.btnContinue.text = "Salvando..."
            } else {
                updateContinueButtonState()
            }
        }

        scanFlowViewModel.priceSubmitSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                val action = PriceInputFragmentDirections.actionPriceInputToProductDetail(
                    barcode = args.barcode,
                    userPrice = 0f
                )
                findNavController().navigate(action)
            }
        }

        scanFlowViewModel.priceSubmitError.observe(viewLifecycleOwner) { error ->
            error?.let {
                binding.btnContinue.isEnabled = currentCents > 0
                binding.btnContinue.text = "Continuar"
                Snackbar.make(
                    binding.root,
                    it,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        scanFlowViewModel.priceWarning.observe(viewLifecycleOwner) { warning ->
            warning?.let {
                binding.btnContinue.isEnabled = currentCents > 0
                binding.btnContinue.text = "Continuar"
                showPriceWarningDialog(it)
            }
        }
    }

    private fun showPriceWarningDialog(warning: com.barcodescanner.app.data.model.PriceWarningResponse) {
        val message = buildString {
            append(warning.message)
            warning.suggestedValue?.let {
                append("\n\nValor sugerido: R$ ${it.replace(".", ",")}")
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Verificar preco")
            .setMessage(message)
            .setPositiveButton("Confirmar assim mesmo") { _, _ ->
                scanFlowViewModel.retryWithWarningConfirmation()
            }
            .setNegativeButton("Corrigir") { _, _ ->
                scanFlowViewModel.dismissWarning()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupPriceInput() {
        if (isPriceInputSetup) return
        isPriceInputSetup = true

        binding.etPrice.setText(formatCurrency(0))

        binding.etPrice.requestFocus()

        binding.etPrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return

                isUpdating = true

                val digits = s.toString().replace(Regex("[^0-9]"), "")

                currentCents = if (digits.isEmpty()) 0 else digits.toLongOrNull() ?: 0

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

        val symbols = DecimalFormatSymbols(Locale("pt", "BR"))
        symbols.decimalSeparator = ','
        symbols.groupingSeparator = '.'

        val formatter = DecimalFormat("#,##0.00", symbols)
        return formatter.format(value)
    }

    private fun setupContinueButton() {
        if (isContinueButtonSetup) return
        isContinueButtonSetup = true

        binding.btnContinue.isEnabled = false
        binding.btnContinue.setOnClickListener {
            submitPrice()
        }
    }

    private fun updateContinueButtonState() {
        binding.btnContinue.isEnabled = currentCents > 0
        binding.btnContinue.text = "Continuar"
    }

    private fun submitPrice() {
        if (currentCents <= 0) {
            Snackbar.make(
                binding.root,
                "Por favor, digite um preco valido",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val product = scanFlowViewModel.product.value
        if (product == null) {
            Snackbar.make(
                binding.root,
                "Produto nao carregado ainda",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        binding.btnContinue.isEnabled = false
        binding.btnContinue.text = "Salvando..."

        scanFlowViewModel.submitPrice(product.id, args.barcode, currentCents)
    }

    private fun navigateBack() {
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
