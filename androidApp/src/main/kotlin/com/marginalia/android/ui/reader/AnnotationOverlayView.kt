package com.marginalia.android.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.marginalia.ink.Stroke
import com.marginalia.ink.StrokePoint

/**
 * Full-page transparent overlay that renders ink annotation strokes for the current page.
 * Stroke coordinates are normalised (0–1); mapped to view dimensions at draw time.
 * Updated whenever the page changes via [updateStrokes].
 */
class AnnotationOverlayView(context: Context) : View(context) {

    private var strokes: List<Stroke> = emptyList()

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
