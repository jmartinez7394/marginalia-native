package com.marginalia.vault

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignalDetectorTest {

    @Test
    fun `capitalised mid-sentence word is a candidate`() {
        val candidates = SignalDetector.extractCandidates("He studied Stoicism as a practice.")
        assertTrue(candidates.contains("Stoicism"))
    }

    @Test
    fun `sentence-start word is not extracted`() {
        val candidates = SignalDetector.extractCandidates("Virtue is the highest good.")
        assertFalse(candidates.contains("Virtue"))
    }

    @Test
    fun `all-caps word is not extracted`() {
        val candidates = SignalDetector.extractCandidates("The FBI investigated the case.")
        assertFalse(candidates.contains("FBI"))
    }

    @Test
    fun `word shorter than 3 chars is not extracted`() {
        val candidates = SignalDetector.extractCandidates("He met Ed in Rome.")
        assertFalse(candidates.contains("Ed"))
    }

    @Test
    fun `multiple candidates from one sentence`() {
        val candidates = SignalDetector.extractCandidates(
            "He studied Stoicism and Eudaimonia together."
        )
        assertTrue(candidates.contains("Stoicism"))
        assertTrue(candidates.contains("Eudaimonia"))
    }

    @Test
    fun `candidates from multiple sentences`() {
        val text = "He was interested in Logos. The concept of Virtue was central."
        val candidates = SignalDetector.extractCandidates(text)
        assertTrue(candidates.contains("Logos"))
        assertTrue(candidates.contains("Virtue"))
    }

    @Test
    fun `empty text returns empty list`() {
        assertTrue(SignalDetector.extractCandidates("").isEmpty())
    }
}
