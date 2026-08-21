package com.wsmonitor.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView with pinch-to-zoom, pan when zoomed, and double-tap zoom.
 * Used for the live screenshot in the device detail screen.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private var minScale = 1f
    private var maxScale = 4f
    private var isScaling = false
    private val lastTouch = PointF()

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                applyScale(detector.scaleFactor)
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val target = if (currentScale() > minScale * 1.1f) minScale else (minScale * 2.5f).coerceAtMost(maxScale)
                zoomTo(target, e.x, e.y)
                return true
            }
        })

    init {
        scaleType = ScaleType.MATRIX
        setImageMatrix(matrix)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        if (bm != null) fitToView()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) fitToView()
    }

    private fun fitToView() {
        if (width <= 0 || height <= 0) return
        val d = drawable ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return
        val scale = minOf(viewW / dw, viewH / dh)
        minScale = scale
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate((viewW - dw * scale) / 2f, (viewH - dh * scale) / 2f)
        setImageMatrix(matrix)
    }

    private fun currentScale(): Float {
        val v = FloatArray(9)
        matrix.getValues(v)
        return v[Matrix.MSCALE_X]
    }

    private fun applyScale(factor: Float) {
        val cur = currentScale()
        val next = (cur * factor).coerceIn(minScale, maxScale)
        val actual = next / cur
        matrix.postScale(actual, actual, width / 2f, height / 2f)
        clampTranslation()
        setImageMatrix(matrix)
    }

    private fun zoomTo(target: Float, fx: Float, fy: Float) {
        matrix.postScale(target / currentScale(), target / currentScale(), fx, fy)
        clampTranslation()
        setImageMatrix(matrix)
    }

    private fun clampTranslation() {
        val d = drawable ?: return
        val v = FloatArray(9)
        matrix.getValues(v)
        val scale = v[Matrix.MSCALE_X]
        val dx = v[Matrix.MTRANS_X]
        val dy = v[Matrix.MTRANS_Y]
        val dw = d.intrinsicWidth * scale
        val dh = d.intrinsicHeight * scale
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val minX = if (dw > viewW) viewW - dw else (viewW - dw) / 2f
        val maxX = if (dw > viewW) 0f else (viewW - dw) / 2f
        val minY = if (dh > viewH) viewH - dh else (viewH - dh) / 2f
        val maxY = if (dh > viewH) 0f else (viewH - dh) / 2f
        val ndx = dx.coerceIn(minX, maxX)
        val ndy = dy.coerceIn(minY, maxY)
        if (ndx != dx || ndy != dy) matrix.postTranslate(ndx - dx, ndy - dy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isScaling = false
                lastTouch.set(event.x, event.y)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> isScaling = true
            MotionEvent.ACTION_MOVE -> {
                if (!isScaling && canPan()) {
                    val dx = event.x - lastTouch.x
                    val dy = event.y - lastTouch.y
                    matrix.postTranslate(dx, dy)
                    clampTranslation()
                    setImageMatrix(matrix)
                }
                lastTouch.set(event.x, event.y)
            }
        }
        return true
    }

    private fun canPan(): Boolean {
        val d = drawable ?: return false
        val scale = currentScale()
        return d.intrinsicWidth * scale > width || d.intrinsicHeight * scale > height
    }
}