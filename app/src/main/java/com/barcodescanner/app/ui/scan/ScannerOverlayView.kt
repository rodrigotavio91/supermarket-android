package com.barcodescanner.app.ui.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.barcodescanner.app.R

class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val overlayPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.scanner_overlay)
        style = Paint.Style.FILL
    }

    private val framePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.scanner_frame)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.scanner_frame_stroke_width)
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.scanner_corner)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.scanner_frame_corner_width)
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val path = Path()
    private val scanFrame = RectF()
    private var frameWidth = 0f
    private var frameHeight = 0f
    private var cornerLength = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        frameWidth = resources.getDimension(R.dimen.scanner_frame_width)
        frameHeight = resources.getDimension(R.dimen.scanner_frame_height)
        cornerLength = resources.getDimension(R.dimen.scanner_frame_corner_length)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val halfFrameWidth = frameWidth / 2f
        val halfFrameHeight = frameHeight / 2f

        // Calculate scan frame position
        scanFrame.set(
            centerX - halfFrameWidth,
            centerY - halfFrameHeight,
            centerX + halfFrameWidth,
            centerY + halfFrameHeight
        )

        // Draw semi-transparent overlay with transparent center
        path.reset()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addRect(scanFrame, Path.Direction.CCW)
        canvas.drawPath(path, overlayPaint)

        // Draw frame border
        canvas.drawRect(scanFrame, framePaint)

        // Draw corner accents
        drawCorners(canvas)
    }

    private fun drawCorners(canvas: Canvas) {
        val left = scanFrame.left
        val top = scanFrame.top
        val right = scanFrame.right
        val bottom = scanFrame.bottom

        // Top-left corner
        canvas.drawLine(left, top, left + cornerLength, top, cornerPaint)
        canvas.drawLine(left, top, left, top + cornerLength, cornerPaint)

        // Top-right corner
        canvas.drawLine(right - cornerLength, top, right, top, cornerPaint)
        canvas.drawLine(right, top, right, top + cornerLength, cornerPaint)

        // Bottom-left corner
        canvas.drawLine(left, bottom - cornerLength, left, bottom, cornerPaint)
        canvas.drawLine(left, bottom, left + cornerLength, bottom, cornerPaint)

        // Bottom-right corner
        canvas.drawLine(right - cornerLength, bottom, right, bottom, cornerPaint)
        canvas.drawLine(right, bottom - cornerLength, right, bottom, cornerPaint)
    }

    fun getScanFrameRect(): RectF = scanFrame
}
