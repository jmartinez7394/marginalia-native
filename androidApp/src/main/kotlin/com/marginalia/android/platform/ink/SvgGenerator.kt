package com.marginalia.android.platform.ink

import com.marginalia.ink.Stroke
import com.marginalia.ink.StrokePoint

/**
 * Derives an SVG from stroke data using Catmull-Rom spline interpolation.
 * Normalised coordinates (0.0–1.0) are mapped to SVG units (0–1000 x, 0–1333 y)
 * maintaining the device screen aspect ratio per vault contract section 4.
 */
object SvgGenerator {

    private const val SVG_WIDTH = 1000
    private const val SVG_HEIGHT = 1333

    fun generate(strokes: List<Stroke>, noteId: String): String = buildString {
        append("""<svg viewBox="0 0 $SVG_WIDTH $SVG_HEIGHT" xmlns="http://www.w3.org/2000/svg" data-note-id="${escapeXml(noteId)}">""")
        append("\n")
        for (stroke in strokes) {
            if (stroke.erased || stroke.points.isEmpty()) continue
            val pathData = if (stroke.points.size == 1) {
                // Single point: render as a small circle via a degenerate path
                val x = fmt(stroke.points[0].x * SVG_WIDTH)
                val y = fmt(stroke.points[0].y * SVG_HEIGHT)
                "M $x $y m -1 0 a 1 1 0 1 0 2 0 a 1 1 0 1 0 -2 0"
            } else {
                catmullRomPath(stroke.points)
            }
            val strokeWidth = fmt(maxOf(stroke.width * SVG_WIDTH, 1.0f))
            append("""  <path d="$pathData" stroke="#1a1a1a" stroke-width="$strokeWidth" stroke-linecap="round" stroke-linejoin="round" fill="none" data-stroke-id="${escapeXml(stroke.id)}"/>""")
            append("\n")
        }
        append("</svg>")
    }

    private fun catmullRomPath(points: List<StrokePoint>): String = buildString {
        val xs = points.map { it.x * SVG_WIDTH.toDouble() }
        val ys = points.map { it.y * SVG_HEIGHT.toDouble() }
        append("M ${fmt(xs[0])} ${fmt(ys[0])}")
        for (i in 0 until points.size - 1) {
            val p0x = if (i > 0) xs[i - 1] else xs[i]
            val p0y = if (i > 0) ys[i - 1] else ys[i]
            val p1x = xs[i]; val p1y = ys[i]
            val p2x = xs[i + 1]; val p2y = ys[i + 1]
            val p3x = if (i + 2 < points.size) xs[i + 2] else xs[i + 1]
            val p3y = if (i + 2 < points.size) ys[i + 2] else ys[i + 1]
            val cp1x = p1x + (p2x - p0x) / 6.0
            val cp1y = p1y + (p2y - p0y) / 6.0
            val cp2x = p2x - (p3x - p1x) / 6.0
            val cp2y = p2y - (p3y - p1y) / 6.0
            append(" C ${fmt(cp1x)} ${fmt(cp1y)} ${fmt(cp2x)} ${fmt(cp2y)} ${fmt(p2x)} ${fmt(p2y)}")
        }
    }

    private fun fmt(d: Double): String = "%.2f".format(d)
    private fun fmt(f: Float): String = "%.2f".format(f)

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
