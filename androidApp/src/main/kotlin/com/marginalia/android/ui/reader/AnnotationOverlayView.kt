package com.marginalia.android.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.marginalia.ink.Stroke
import com.marginalia.ink.StrokePoint

/**
 * Full-page transparent overlay that renders ink annotation strokes for the current page.
 * Stroke coordinates are normalised (0–1); mapped to view dimensions at draw time.
 * Updated whenever the page changes via [updateStrokes].
 * Fires [onAnnotationTapped] with the annotation ID when the user taps an annotated area.
 */
class AnnotationOverlayView(context: Context) : View(context) {

    private var annotationData: List<Pair<String, List<Stroke>>> = emptyList()
    private var strokes: List<Stroke> = emptyList()

    var onAnnotationTapped: ((annotationId: String) -> Unit)? = null

    private val inkPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    fun updateStrokes(newStrokes: List<Stroke>) {
        strokes = newStrokes
        invalidate()
    }

    fun updateAnnotations(data: List<Pair<String, List<Stroke>>>) {
        annotationData = data
        strokes = data.flatMap { it.second }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return false
        if (onAnnotationTapped == null) return false
        val vw = if (width > 0) width.toFloat() else return false
        val vh = if (height > 0) height.toFloat() else return false
        val normX = event.x / vw
        val normY = event.y / vh
        for ((annotationId, annoStrokes) in annotationData) {
            val bounds = computeBounds(annoStrokes) ?: continue
            if (bounds.contains(normX, normY)) {
                onAnnotationTapped?.invoke(annotationId)
                return true
            }
        }
        return false
    }

    private fun computeBounds(strokes: List<Stroke>): RectF? {
        if (strokes.isEmpty()) return null
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (stroke in strokes) {
            for (pt in stroke.points) {
                if (pt.x < minX) minX = pt.x; if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x; if (pt.y > maxY) maxY = pt.y
            }
        }
        val padding = 0.02f
        return RectF(minX - padding, minY - padding, maxX + padding, maxY + padding)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (strokes.isEmpty() || width == 0 || height == 0) return
        val vw = width.toFloat()
        val vh = height.toFloat()

        for (stroke in strokes) {
            if (stroke.erased || stroke.points.size < 2) continue
            inkPaint.strokeWidth = maxOf(stroke.width * vw, 1f)
            val path = buildPath(stroke.points, vw, vh)
            canvas.drawPath(path, inkPaint)
        }
    }

    private fun buildPath(points: List<StrokePoint>, vw: Float, vh: Float): Path {
        val path = Path()
        path.moveTo(points[0].x * vw, points[0].y * vh)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x * vw, points[i].y * vh)
        }
        return path
    }
}
