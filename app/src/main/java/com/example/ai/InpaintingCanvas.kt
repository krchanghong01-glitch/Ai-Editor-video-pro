package com.example.ai

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class InpaintingCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val drawingPath = Path()
    private val drawPaint = Paint().apply {
        color = Color.parseColor("#80FF3D00") // 50% opacity red/orange for high contrast masking
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 32f // Premium thick brush for easy masking
        isAntiAlias = true
    }

    private val pathList = mutableListOf<Path>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Redraw all completed paths
        for (p in pathList) {
            canvas.drawPath(p, drawPaint)
        }
        // Draw active tracking segment
        canvas.drawPath(drawingPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                drawingPath.moveTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                drawingPath.lineTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                drawingPath.lineTo(x, y)
                pathList.add(Path(drawingPath))
                drawingPath.reset()
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    fun clear() {
        drawingPath.reset()
        pathList.clear()
        invalidate()
    }

    fun isMaskEmpty(): Boolean {
        return pathList.isEmpty()
    }

    /**
     * Helper to retrieve mask coordinate bounds or overlays
     */
    fun getMaskCoordinates(): List<Path> {
        return pathList
    }
}
