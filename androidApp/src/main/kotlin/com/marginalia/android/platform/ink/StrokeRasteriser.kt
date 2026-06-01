package com.marginalia.android.platform.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.marginalia.ink.Stroke
import java.io.ByteArrayOutputStream

/**
 * Rasterises a list of ink strokes to a PNG byte array in memory.
 * The PNG is a transient API call artifact and must never be written to disk.
 */
object StrokeRasteriser {

    private const val DEFAULT_WIDTH = 1080
    private const val DEFAULT_HEIGHT = 1440

    fun rasterise(
        strokes: List<Stroke>,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        for (stroke in strokes) {
            if (stroke.erased || stroke.points.size < 2) continue
            paint.strokeWidth = maxOf(stroke.width * width, 1f)
            val path = Path()
            path.moveTo(stroke.points[0].x * width, stroke.points[0].y * height)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x * width, stroke.points[i].y * height)
            }
            canvas.drawPath(path, paint)
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        bitmap.recycle()
        return outputStream.toByteArray()
    }
}
