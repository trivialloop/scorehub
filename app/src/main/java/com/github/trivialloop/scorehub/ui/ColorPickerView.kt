package com.github.trivialloop.scorehub.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * A circular HSV color picker.
 *
 * The hue is mapped around the circle (0°–360°), the saturation from center (0)
 * to edge (1), and the value (brightness) is fixed at 1 for vivid colors.
 * A small indicator circle follows the user's finger.
 */
class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val hueSatPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.LTGRAY
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val indicatorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var bitmap: Bitmap? = null
    private var indicatorX = 0f
    private var indicatorY = 0f
    private var currentColor = Color.RED

    var onColorChanged: ((Int) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildBitmap(w, h)
        // Place indicator at the current color position
        placeIndicatorAtColor(currentColor)
    }

    private fun buildBitmap(w: Int, h: Int) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cx = w / 2f
        val cy = h / 2f
        val radius = (minOf(w, h) / 2f) - 4f

        for (x in 0 until w) {
            for (y in 0 until h) {
                val dx = x - cx
                val dy = y - cy
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= radius) {
                    val hue = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
                    val sat = (dist / radius).coerceIn(0f, 1f)
                    bmp.setPixel(x, y, Color.HSVToColor(floatArrayOf(hue, sat, 1f)))
                }
            }
        }
        bitmap = bmp
        hueSatPaint.shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    private fun placeIndicatorAtColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val sat = hsv[1]
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - 4f
        val angleRad = hue * PI.toFloat() / 180f
        indicatorX = cx + cos(angleRad) * sat * radius
        indicatorY = cy + sin(angleRad) * sat * radius
        invalidate()
    }

    fun setColor(color: Int) {
        currentColor = color
        indicatorFillPaint.color = color
        if (width > 0) placeIndicatorAtColor(color)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - 4f

        // Draw the color wheel
        canvas.drawCircle(cx, cy, radius, hueSatPaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // Draw the indicator
        indicatorFillPaint.color = currentColor
        canvas.drawCircle(indicatorX, indicatorY, 12f, indicatorFillPaint)
        canvas.drawCircle(indicatorX, indicatorY, 12f, indicatorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = (minOf(width, height) / 2f) - 4f

            val dx = event.x - cx
            val dy = event.y - cy
            val dist = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
            val hue = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
            val sat = (dist / radius).coerceIn(0f, 1f)

            currentColor = Color.HSVToColor(floatArrayOf(hue, sat, 1f))
            indicatorX = cx + (cos(atan2(dy, dx)) * sat * radius).toFloat()
            indicatorY = cy + (sin(atan2(dy, dx)) * sat * radius).toFloat()

            indicatorFillPaint.color = currentColor
            invalidate()
            onColorChanged?.invoke(currentColor)
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Always square
        val size = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        ).coerceAtLeast(200)
        setMeasuredDimension(size, size)
    }
}
