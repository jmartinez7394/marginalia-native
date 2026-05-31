package com.marginalia.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadingProgressTest {

    @Test
    fun percentageClampedAboveOne() {
        val progress = ReadingProgress.create(null, null, 1.5f, null)
        assertEquals(1.0f, progress.percentage)
    }

    @Test
    fun percentageClampedBelowZero() {
        val progress = ReadingProgress.create(null, null, -0.1f, null)
        assertEquals(0.0f, progress.percentage)
    }

    @Test
    fun percentageWithinRangeIsUnchanged() {
        val progress = ReadingProgress.create(null, null, 0.5f, null)
        assertEquals(0.5f, progress.percentage)
    }

    @Test
    fun percentageAtBoundariesIsUnchanged() {
        assertEquals(0.0f, ReadingProgress.create(null, null, 0.0f, null).percentage)
        assertEquals(1.0f, ReadingProgress.create(null, null, 1.0f, null).percentage)
    }

    @Test
    fun validCfiStringPreserved() {
        val cfi = "epubcfi(/6/4[chap01]!/4/2/1:0)"
        val progress = ReadingProgress.create(cfi, null, 0.3f, null)
        assertEquals(cfi, progress.cfi)
    }

    @Test
    fun pdfProgressUsesPageNumber() {
        val progress = ReadingProgress.create(null, 42, 0.4f, null)
        assertNull(progress.cfi)
        assertEquals(42, progress.pageNumber)
    }
}
