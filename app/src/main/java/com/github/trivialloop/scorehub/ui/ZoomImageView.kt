package com.github.trivialloop.scorehub.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * Pinch-to-zoom, drag and double-tap image view.
 * Starts fitted to the width (tall sheets scroll vertically); can zoom out until the
 * whole sheet fits, and in up to 5x the fitted width.
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val m = Matrix()
    private val v = FloatArray(9)
    private var fitWidthScale = 1f
    private var minScale = 1f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale() > fitWidthScale * 1.05f) resetFit() else zoomBy(2.5f, e.x, e.y)
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (scaleDetector.isInProgress) return true
                m.postTranslate(-dx, -dy)
                applyMatrix()
                return true
            }
        })

    init { scaleType = ScaleType.MATRIX }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        resetFit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetFit()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun currentScale(): Float { m.getValues(v); return v[Matrix.MSCALE_X] }

    private fun resetFit() {
        val d = drawable ?: return
        if (width == 0 || height == 0 || d.intrinsicWidth <= 0) return
        fitWidthScale = width.toFloat() / d.intrinsicWidth
        minScale = min(fitWidthScale, height.toFloat() / d.intrinsicHeight)
        m.setScale(fitWidthScale, fitWidthScale)
        applyMatrix()
    }

    private fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        val current = currentScale()
        val target = (current * factor).coerceIn(minScale, fitWidthScale * 5f)
        val f = target / current
        m.postScale(f, f, focusX, focusY)
        applyMatrix()
    }

    /** Keeps the image inside the view (centered when smaller than the view on an axis). */
    private fun applyMatrix() {
        val d = drawable
        if (d != null) {
            m.getValues(v)
            val w = d.intrinsicWidth * v[Matrix.MSCALE_X]
            val h = d.intrinsicHeight * v[Matrix.MSCALE_Y]
            val tx = if (w <= width) (width - w) / 2f else v[Matrix.MTRANS_X].coerceIn(width - w, 0f)
            val ty = if (h <= height) (height - h) / 2f else v[Matrix.MTRANS_Y].coerceIn(height - h, 0f)
            m.postTranslate(tx - v[Matrix.MTRANS_X], ty - v[Matrix.MTRANS_Y])
        }
        imageMatrix = m
    }
}
