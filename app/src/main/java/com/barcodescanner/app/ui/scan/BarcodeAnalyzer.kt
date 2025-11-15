package com.barcodescanner.app.ui.scan

import android.annotation.SuppressLint
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(
    private val viewWidth: Int,
    private val viewHeight: Int,
    private val scanFrameRect: RectF,
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        // Focus on GTIN codes (EAN-8, EAN-13, UPC-A, UPC-E)
                        when (barcode.format) {
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A,
                            Barcode.FORMAT_UPC_E -> {
                                barcode.rawValue?.let { value ->
                                    // Check if barcode is within scan frame boundaries
                                    if (isBarcodeInScanFrame(barcode, imageProxy) && isValidGTIN(value, barcode.format)) {
                                        onBarcodeDetected(value)
                                    }
                                }
                            }
                            else -> {
                                // Optionally handle other barcode types
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    // Handle errors silently for better performance
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    /**
     * Check if the barcode's center point is within the scan frame area.
     * This ensures we only scan barcodes that are properly positioned within the rectangle.
     */
    private fun isBarcodeInScanFrame(barcode: Barcode, imageProxy: ImageProxy): Boolean {
        val boundingBox = barcode.boundingBox ?: return false
        
        // Get image dimensions
        val imageWidth = imageProxy.width.toFloat()
        val imageHeight = imageProxy.height.toFloat()
        
        // Account for rotation
        val rotation = imageProxy.imageInfo.rotationDegrees
        val isRotated = rotation == 90 || rotation == 270
        
        val actualImageWidth = if (isRotated) imageHeight else imageWidth
        val actualImageHeight = if (isRotated) imageWidth else imageHeight
        
        // Calculate scale factors to map view coordinates to image coordinates
        val scaleX = actualImageWidth / viewWidth.toFloat()
        val scaleY = actualImageHeight / viewHeight.toFloat()
        
        // Map scan frame from view coordinates to image coordinates
        val scanFrameInImage = RectF(
            scanFrameRect.left * scaleX,
            scanFrameRect.top * scaleY,
            scanFrameRect.right * scaleX,
            scanFrameRect.bottom * scaleY
        )
        
        // Calculate barcode center in image coordinates
        val barcodeCenterX = boundingBox.exactCenterX()
        val barcodeCenterY = boundingBox.exactCenterY()
        
        // Check if barcode center is within the scan frame
        if (!scanFrameInImage.contains(barcodeCenterX, barcodeCenterY)) {
            return false
        }
        
        // Also verify that at least 70% of the barcode is within the frame to avoid partial scans
        val barcodeRect = RectF(boundingBox)
        val intersection = RectF()
        if (!intersection.setIntersect(barcodeRect, scanFrameInImage)) {
            return false
        }
        
        val barcodeArea = barcodeRect.width() * barcodeRect.height()
        val intersectionArea = intersection.width() * intersection.height()
        val overlapPercentage = if (barcodeArea > 0) intersectionArea / barcodeArea else 0f
        
        // Require at least 90% of barcode to be within scan frame
        return overlapPercentage >= 0.9f
    }

    /**
     * Validate GTIN code format and length to ensure complete scan.
     * This prevents accepting partial or corrupted barcode reads.
     */
    private fun isValidGTIN(value: String, format: Int): Boolean {
        // Remove any whitespace
        val cleanValue = value.trim()
        
        // Check length based on format
        val expectedLength = when (format) {
            Barcode.FORMAT_EAN_13 -> 13
            Barcode.FORMAT_EAN_8 -> 8
            Barcode.FORMAT_UPC_A -> 12
            Barcode.FORMAT_UPC_E -> 8
            else -> return false
        }
        
        // Verify length matches expected format
        if (cleanValue.length != expectedLength) {
            return false
        }
        
        // Verify all characters are digits
        if (!cleanValue.all { it.isDigit() }) {
            return false
        }
        
        // Validate GTIN checksum for extra reliability
        return validateGTINChecksum(cleanValue)
    }

    /**
     * Validate GTIN checksum using the standard algorithm.
     * This ensures the barcode was read completely and correctly.
     */
    private fun validateGTINChecksum(gtin: String): Boolean {
        if (gtin.isEmpty() || !gtin.all { it.isDigit() }) {
            return false
        }
        
        var sum = 0
        val digits = gtin.map { it.toString().toInt() }
        
        // Process all digits except the last one (which is the checksum)
        for (i in digits.size - 2 downTo 0) {
            val multiplier = if ((digits.size - 2 - i) % 2 == 0) 3 else 1
            sum += digits[i] * multiplier
        }
        
        val checkDigit = (10 - (sum % 10)) % 10
        return checkDigit == digits.last()
    }
}
