package com.marginalia.vault

import com.marginalia.model.SignalSourceType
import com.marginalia.model.SignalUserAction
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistrySignalServiceTest {

    private fun makeService() = RegistrySignalFileService(FakeVaultFileSystem())

    private suspend fun RegistrySignalFileService.record(term: String, id: String = "h1") =
        recordSignal(term, SignalSourceType.HIGHLIGHT, id, "library/notes/Book.md", "library")

    @Test
    fun `new signal created for unknown term`() = runTest {
        val service = makeService()
        service.record("Eudaimonia", "h1")
        val signals = service.getSignals("library")
        assertEquals(1, signals.size)
        assertEquals("Eudaimonia", signals.first().conceptCandidate)
        assertEquals(1, signals.first().occurrenceCount)
        assertFalse(signals.first().processed)
        assertEquals(null, signals.first().userAction)
    }

    @Test
    fun `existing signal incremented on second occurrence`() = runTest {
        val service = makeService()
        service.record("Eudaimonia", "h1")
        service.record("Eudaimonia", "h2")
        val signals = service.getSignals("library")
        assertEquals(1, signals.size)
        assertEquals(2, signals.first().occurrenceCount)
    }

    @Test
    fun `case-insensitive match increments existing signal`() = runTest {
        val service = makeService()
        service.record("Virtue", "h1")
        service.record("virtue", "h2")
        assertEquals(1, service.getSignals("library").size)
        assertEquals(2, service.getSignals("library").first().occurrenceCount)
    }

    @Test
    fun `threshold check - 2 occurrences not pending, 3 is pending`() = runTest {
        val service = makeService()
        service.record("Logos", "h1")
        service.record("Logos", "h2")
        assertTrue(service.getPendingCandidates("library", minOccurrences = 3).isEmpty())
        service.record("Logos", "h3")
        assertEquals(1, service.getPendingCandidates("library", minOccurrences = 3).size)
    }

    @Test
    fun `ACCEPTED marks signal as processed`() = runTest {
        val service = makeService()
        service.record("Stoicism")
        val signalId = service.getSignals("library").first().signalId
        service.processUserAction(signalId, SignalUserAction.ACCEPTED, "library")
        val signal = service.getSignals("library").first()
        assertTrue(signal.processed)
        assertEquals(SignalUserAction.ACCEPTED, signal.userAction)
    }

    @Test
    fun `REJECTED marks signal as processed`() = runTest {
        val service = makeService()
        service.record("Stoicism")
        val signalId = service.getSignals("library").first().signalId
        service.processUserAction(signalId, SignalUserAction.REJECTED, "library")
        val signal = service.getSignals("library").first()
        assertTrue(signal.processed)
        assertEquals(SignalUserAction.REJECTED, signal.userAction)
    }

    @Test
    fun `DEFERRED keeps signal not processed`() = runTest {
        val service = makeService()
        repeat(3) { i -> service.record("Stoicism", "h$i") }
        val signalId = service.getSignals("library").first().signalId
        service.processUserAction(signalId, SignalUserAction.DEFERRED, "library")
        val signal = service.getSignals("library").first()
        assertFalse(signal.processed)
        assertEquals(SignalUserAction.DEFERRED, signal.userAction)
    }

    @Test
    fun `deferred signal excluded from getPendingCandidates`() = runTest {
        val service = makeService()
        repeat(3) { i -> service.record("Stoicism", "h$i") }
        val signalId = service.getSignals("library").first().signalId
        service.processUserAction(signalId, SignalUserAction.DEFERRED, "library")
        assertTrue(service.getPendingCandidates("library").isEmpty())
    }
}
