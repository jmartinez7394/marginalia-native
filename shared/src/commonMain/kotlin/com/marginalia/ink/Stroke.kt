package com.marginalia.ink

data class Stroke(
    val id: String,
    val points: List<StrokePoint>,
    val colour: InkColour,
    val width: Float,
    val erased: Boolean,
    val timestamp: Long
)

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float
)

enum class InkColour { BLACK, HIGHLIGHT }
