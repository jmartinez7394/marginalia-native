package com.marginalia.android.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    // Normalised (0–1) coordinates and pressure; t is millis since stroke start
    var onStrokeBegin: ((normX: Float, normY: Float, pressure: Float, t: Long) -> Unit)? = null
    var onStrokePoint: ((normX: Float, normY: Float, pressure: Float, t: Long) -> Unit)? = null
    var onStrokeComplete: (() -> Unit)? = null
    // Highlight gesture: fired on hover exit with the normalised bounding box of the hover stroke
    var onHighlightGesture: ((normBounds: RectF) -> Unit)? = null
    // Called during hover for A2 refresh
    var onHoverActive: (() -> Unit)? = null
    // Highlight colour for visual feedback (set from ViewModel)
    var highlightColourArgb: Int = Color.argb(100, 200, 200, 200)

    private val completedPaths = mutableListOf<Path>()
    private var activePath: Path? = null

    // Hover path for highlight gesture visual feedback
    private var hoverPath: Path? = null
    private val hoverPoints = mutableListOf<Pair<Float, Float>>()

    private val inkPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        strokeWidth = 4f
    }

    private val hoverPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        strokeWidth = 12f
    }

    private var lastDownTime = 0L
    private var lastDownX = 0f
    private var lastDownY = 0f
    private var strokeStartTime = 0L

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw hover highlight stroke (visible even when annotation mode inactive)
        hoverPath?.let { path ->
            hoverPaint.color = highlightColourArgb
            canvas.drawPath(path, hoverPaint)
        }
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
        strokeStartTime = SystemClock.uptimeMillis()
        val path = Path()
        path.moveTo(event.x, event.y)
        activePath = path
        val vw = if (width > 0) width.toFloat() else 1f
        val vh = if (height > 0) height.toFloat() else 1f
        onStrokeBegin?.invoke(event.x / vw, event.y / vh, event.pressure, 0L)
        return true
    }

    private fun handleMove(event: MotionEvent): Boolean {
        if (!annotationModeActive) return false
        val path = activePath ?: return false
        // Consume all historical points for smoother ink at high polling rate
        val vw = if (width > 0) width.toFloat() else 1f
        val vh = if (height > 0) height.toFloat() else 1f
        val baseT = SystemClock.uptimeMillis() - strokeStartTime
        for (i in 0 until event.historySize) {
            path.lineTo(event.getHistoricalX(i), event.getHistoricalY(i))
            val t = baseT - (event.historySize - i) * 8L // approximate 8ms per history step
            onStrokePoint?.invoke(
                event.getHistoricalX(i) / vw,
                event.getHistoricalY(i) / vh,
                event.getHistoricalPressure(i),
                maxOf(t, 0L)
            )
        }
        path.lineTo(event.x, event.y)
        onStrokePoint?.invoke(event.x / vw, event.y / vh, event.pressure, baseT)
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
        onStrokeComplete?.invoke()
        invalidate()
        return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        // Only handle hover when annotation mode is INACTIVE (hover = highlight gesture)
        if (annotationModeActive) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> {
                if (hoverPath == null) {
                    val path = Path()
                    path.moveTo(event.x, event.y)
                    hoverPath = path
                } else {
                    hoverPath?.lineTo(event.x, event.y)
                }
                hoverPoints.add(Pair(event.x, event.y))
                onHoverActive?.invoke()
                invalidate()
                true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                if (hoverPoints.isNotEmpty()) {
                    val bounds = computeNormBounds()
                    onHighlightGesture?.invoke(bounds)
                }
                hoverPath = null
                hoverPoints.clear()
                invalidate()
                true
            }
            MotionEvent.ACTION_HOVER_ENTER -> true
            else -> false
        }
    }

    private fun computeNormBounds(): RectF {
        val vw = if (width > 0) width.toFloat() else 1f
        val vh = if (height > 0) height.toFloat() else 1f
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
        for ((x, y) in hoverPoints) {
            if (x < minX) minX = x; if (y < minY) minY = y
            if (x > maxX) maxX = x; if (y > maxY) maxY = y
        }
        return RectF(minX / vw, minY / vh, maxX / vw, maxY / vh)
    }

    fun clearStrokes() {
        completedPaths.clear()
        activePath = null
        invalidate()
    }
}
