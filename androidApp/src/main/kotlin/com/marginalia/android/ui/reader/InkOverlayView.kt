package com.marginalia.android.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Full-page transparent overlay positioned above the Readium WebView container.
 *
 * Inactive state: passes all events through (returns false from onTouchEvent).
 * Active (annotation mode): captures stylus and touch events, draws ink strokes on canvas.
 *
 * Double-tap detection works for all input types so the emulator can trigger annotation mode.
 * On Boox with EMR stylus, only stylus events will fire naturally (palm rejection via hardware).
 */
class InkOverlayView(context: Context) : View(context) {

    var annotationModeActive: Boolean = false
        set(value) {
            field = value
            if (!value) clearStrokes()
        }

    var doubleTapThresholdMs: Int = 300
    var doubleTapTolerancePx: Float = 50f
    var strokeWidthPx: Float = 4f

    var onDoubleTap: (() -> Unit)? = null
    var onAnnotationStroke: (() -> Unit)? = null

    private val completedPaths = mutableListOf<Path>()
    private var activePath: Path? = null

    private val inkPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        strokeWidth = 4f
    }

    private var lastDownTime = 0L
    private var lastDownX = 0f
    private var lastDownY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!annotationModeActive) return
        inkPaint.strokeWidth = strokeWidthPx
        for (path in completedPaths) canvas.drawPath(path, inkPaint)
        activePath?.let { canvas.drawPath(it, inkPaint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleUp(event)
            else -> false
        }
    }

    private fun handleDown(event: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastDownTime
        val dx = abs(event.x - lastDownX)
        val dy = abs(event.y - lastDownY)
        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        val isDoubleTap = elapsed in 1 until doubleTapThresholdMs && dist < doubleTapTolerancePx

        if (isDoubleTap && !annotationModeActive) {
            lastDownTime = 0L
            onDoubleTap?.invoke()
            return true
        }

        lastDownTime = now
        lastDownX = event.x
        lastDownY = event.y

        if (!annotationModeActive) return false

        // Start new stroke
        val path = Path()
        path.moveTo(event.x, event.y)
        activePath = path
        onAnnotationStroke?.invoke()
        return true
    }

    private fun handleMove(event: MotionEvent): Boolean {
        if (!annotationModeActive) return false
        val path = activePath ?: return false
        // Consume all historical points for smoother ink at high polling rate
        for (i in 0 until event.historySize) {
            path.lineTo(event.getHistoricalX(i), event.getHistoricalY(i))
        }
        path.lineTo(event.x, event.y)
        onAnnotationStroke?.invoke()
        invalidate()
        return true
    }

    private fun handleUp(event: MotionEvent): Boolean {
        if (!annotationModeActive) return false
        activePath?.let { path ->
            path.lineTo(event.x, event.y)
            completedPaths.add(path)
        }
        activePath = null
        invalidate()
        return true
    }

    fun clearStrokes() {
        completedPaths.clear()
        activePath = null
        invalidate()
    }
}
