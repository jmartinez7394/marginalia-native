package com.marginalia.android.ui.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marginalia.android.di.AppSettings
import com.marginalia.android.platform.reader.OpenPublicationResult
import com.marginalia.android.platform.reader.ReadiumBookOpener
import com.marginalia.animachora.Territory
import com.marginalia.animachora.TerritoryDefaults
import com.marginalia.animachora.TerritoryType
import com.marginalia.device.DisplayRefreshManager
import com.marginalia.device.RefreshMode
import com.marginalia.model.Book
import com.marginalia.model.Highlight
import com.marginalia.model.HighlightColour
import com.marginalia.model.ReadingProgress
import com.marginalia.model.ReadingStatus
import com.marginalia.model.Result
import com.marginalia.settings.SettingsRegistry
import com.marginalia.vault.ConceptRegistry
import com.marginalia.vault.HighlightRepository
import com.marginalia.vault.LibraryRepository
import com.marginalia.vault.LinkedNoteGenerator
import com.marginalia.vault.LinkedNoteService
import com.marginalia.vault.RegistrySignalService
import com.marginalia.vault.SignalDetector
import com.marginalia.vault.VaultFileSystem
import com.marginalia.model.SignalSourceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.util.UUID
import javax.inject.Inject

sealed class ReaderUiState {
    object Loading : ReaderUiState()
    data class Ready(
        val publication: Publication,
        val initialLocator: Locator?
    ) : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookOpener: ReadiumBookOpener,
    private val libraryRepository: LibraryRepository,
    private val highlightRepository: HighlightRepository,
    private val linkedNoteService: LinkedNoteService,
    private val registrySignalService: RegistrySignalService,
    private val conceptRegistry: ConceptRegistry,
    private val fileSystem: VaultFileSystem,
    private val displayRefreshManager: DisplayRefreshManager,
    private val settingsRegistry: SettingsRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _highlights = MutableStateFlow<List<Highlight>>(emptyList())
    val currentHighlights: StateFlow<List<Highlight>> = _highlights.asStateFlow()

    private val _debugRefreshMode = MutableStateFlow<RefreshMode?>(null)
    val debugRefreshMode: StateFlow<RefreshMode?> = _debugRefreshMode.asStateFlow()

    private val _annotationModeActive = MutableStateFlow(false)
    val annotationModeActive: StateFlow<Boolean> = _annotationModeActive.asStateFlow()

    private val _activeHighlightColour = MutableStateFlow(HighlightColour.YELLOW)
    val activeHighlightColour: StateFlow<HighlightColour> = _activeHighlightColour.asStateFlow()

    private var openPublication: Publication? = null
    private var currentBookId: String? = null
    private var currentBook: Book? = null
    private var scrollSettleJob: Job? = null
    private var inactivityTimeoutJob: Job? = null

    fun openBook(bookId: String) {
        viewModelScope.launch {
            currentBookId = bookId
            _uiState.value = ReaderUiState.Loading
            val book = libraryRepository.getBook(bookId)
                ?: run {
                    _uiState.value = ReaderUiState.Error("Book not found")
                    return@launch
                }
            currentBook = book

            val highlights = highlightRepository.getHighlights(bookId)
            _highlights.value = highlights

            // Restore last reading position from saved progress.
            val savedLocator: Locator? = book.readingProgress.cfi?.takeIf { it.isNotEmpty() }?.let { stored ->
                try {
                    Locator.fromJSON(JSONObject(stored))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not restore reading position: ${e.message}")
                    null
                }
            }
            if (savedLocator != null) {
                Log.d(TAG, "Restoring position: ${(book.readingProgress.percentage * 100).toInt()}%")
            }

            when (val result = bookOpener.open(book.filePath, book.format)) {
                is OpenPublicationResult.Success -> {
                    openPublication = result.publication
                    Log.d(TAG, "Publication opened, emitting Ready")
                    _uiState.value = ReaderUiState.Ready(
                        publication = result.publication,
                        initialLocator = savedLocator
                    )
                }
                is OpenPublicationResult.FileNotFound ->
                    _uiState.value = ReaderUiState.Error("File not found: ${result.path}")
                is OpenPublicationResult.UnsupportedFormat ->
                    _uiState.value = ReaderUiState.Error("Unsupported format: ${result.path}")
                is OpenPublicationResult.CorruptFile ->
                    _uiState.value = ReaderUiState.Error(result.message)
            }
        }
    }

    fun createHighlight(cfi: String, href: String, locatorJson: String, text: String, colour: HighlightColour) {
        val bookId = currentBookId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val highlight = Highlight(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                href = href,
                cfi = cfi,
                locatorJson = locatorJson,
                text = text,
                colour = colour,
                annotation = null,
                createdAt = System.currentTimeMillis(),
                pageNumber = null
            )
            when (val result = highlightRepository.addHighlight(highlight)) {
                is Result.Success -> {
                    val updated = _highlights.value + highlight
                    _highlights.value = updated
                    Log.d(TAG, "Highlight created: ${highlight.id}")
                    onHighlightApplied()
                    updateLinkedNote(bookId, updated)
                    recordSignals(highlight)
                }
                is Result.Failure ->
                    Log.e(TAG, "Failed to save highlight: ${result.error}")
            }
        }
    }

    fun updateHighlight(highlight: Highlight) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = highlightRepository.updateHighlight(highlight)) {
                is Result.Success -> {
                    val updated = _highlights.value.map { if (it.id == highlight.id) highlight else it }
                    _highlights.value = updated
                    Log.d(TAG, "Highlight updated: ${highlight.id}")
                    displayRefreshManager.refreshRegalFull()
                    _debugRefreshMode.value = RefreshMode.REGAL
                    val bookId = currentBookId ?: return@launch
                    updateLinkedNote(bookId, updated)
                }
                is Result.Failure ->
                    Log.e(TAG, "Failed to update highlight: ${result.error}")
            }
        }
    }

    fun deleteHighlight(highlightId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = highlightRepository.deleteHighlight(highlightId)) {
                is Result.Success -> {
                    _highlights.value = _highlights.value.filter { it.id != highlightId }
                    Log.d(TAG, "Highlight deleted: $highlightId")
                }
                is Result.Failure ->
                    Log.e(TAG, "Failed to delete highlight: ${result.error}")
            }
        }
    }

    // --- Refresh events ---

    fun onPageTurn() {
        val delayMs = settingsRegistry.get(AppSettings.REFRESH_PAGE_TURN_DELAY_MS).toLong()
        if (delayMs > 0) {
            viewModelScope.launch {
                delay(delayMs)
                displayRefreshManager.refreshFull()
                _debugRefreshMode.value = RefreshMode.GC16
            }
        } else {
            displayRefreshManager.refreshFull()
            _debugRefreshMode.value = RefreshMode.GC16
        }
    }

    fun onChapterNav() {
        val delayMs = settingsRegistry.get(AppSettings.REFRESH_PAGE_TURN_DELAY_MS).toLong()
        if (delayMs > 0) {
            viewModelScope.launch {
                delay(delayMs)
                displayRefreshManager.refreshFull()
                _debugRefreshMode.value = RefreshMode.GC16
            }
        } else {
            displayRefreshManager.refreshFull()
            _debugRefreshMode.value = RefreshMode.GC16
        }
    }

    fun onScrollActive() {
        scrollSettleJob?.cancel()
        displayRefreshManager.refreshFast()
        _debugRefreshMode.value = RefreshMode.A2
    }

    fun scheduleScrollSettle() {
        scrollSettleJob?.cancel()
        val thresholdMs = settingsRegistry.get(AppSettings.REFRESH_SCROLL_PAUSE_MS).toLong()
        scrollSettleJob = viewModelScope.launch {
            delay(thresholdMs)
            displayRefreshManager.refreshRegalFull()
            _debugRefreshMode.value = RefreshMode.REGAL
        }
    }

    fun onTextSelectionStart() {
        displayRefreshManager.refreshDU()
        _debugRefreshMode.value = RefreshMode.DU
    }

    fun onTextSelectionEnd() {
        displayRefreshManager.refreshRegalFull()
        _debugRefreshMode.value = RefreshMode.REGAL
    }

    fun onAnnotationOpen() {
        displayRefreshManager.refreshDU()
        _debugRefreshMode.value = RefreshMode.DU
    }

    fun onForegroundRestore() {
        displayRefreshManager.refreshFull()
        _debugRefreshMode.value = RefreshMode.GC16
    }

    private fun onHighlightApplied() {
        displayRefreshManager.refreshRegalFull()
        _debugRefreshMode.value = RefreshMode.REGAL
    }

    // --- Annotation mode ---

    fun activateAnnotationMode() {
        inactivityTimeoutJob?.cancel()
        _annotationModeActive.value = true
        displayRefreshManager.refreshRegalFull()
        _debugRefreshMode.value = RefreshMode.REGAL
        scheduleAnnotationInactivity()
    }

    fun deactivateAnnotationMode() {
        inactivityTimeoutJob?.cancel()
        _annotationModeActive.value = false
        displayRefreshManager.refreshFull()
        _debugRefreshMode.value = RefreshMode.GC16
    }

    fun resetAnnotationInactivityTimer() {
        if (!_annotationModeActive.value) return
        inactivityTimeoutJob?.cancel()
        displayRefreshManager.refreshFast()
        _debugRefreshMode.value = RefreshMode.A2
        scheduleAnnotationInactivity()
    }

    private fun scheduleAnnotationInactivity() {
        val timeoutMs = settingsRegistry.get(AppSettings.ANNOTATION_INACTIVITY_TIMEOUT_MS).toLong()
        if (timeoutMs <= 0L) return
        inactivityTimeoutJob = viewModelScope.launch {
            delay(timeoutMs)
            deactivateAnnotationMode()
        }
    }

    fun setHighlightColour(colour: HighlightColour) {
        _activeHighlightColour.value = colour
    }

    fun setPenStrokeWidth(width: Int) {
        settingsRegistry.set(AppSettings.PEN_STROKE_WIDTH, width)
    }

    fun setPressureSensitivity(key: String) {
        settingsRegistry.set(AppSettings.PEN_PRESSURE_SENSITIVITY, key)
    }

    fun getIntSetting(setting: com.marginalia.settings.Setting<Int>): Int =
        settingsRegistry.get(setting)

    fun getStringSetting(setting: com.marginalia.settings.Setting<String>): String =
        settingsRegistry.get(setting)

    // --- End annotation mode ---

    // --- End refresh events ---

    private suspend fun updateLinkedNote(bookId: String, highlights: List<Highlight>) {
        val book = currentBook ?: return
        val territory = bookToTerritory(book)
        val existing = linkedNoteService.getLinkedNote(bookId)
        if (existing == null) {
            when (val result = linkedNoteService.createLinkedNote(book, territory)) {
                is Result.Success -> {
                    Log.d(TAG, "Linked note created for $bookId")
                    linkedNoteService.updateLinkedNote(result.value, highlights)
                }
                is Result.Failure ->
                    Log.e(TAG, "Failed to create linked note: ${result.error}")
            }
        } else {
            when (val result = linkedNoteService.updateLinkedNote(existing, highlights)) {
                is Result.Success -> Log.d(TAG, "Linked note updated for $bookId")
                is Result.Failure ->
                    Log.e(TAG, "Failed to update linked note: ${result.error}")
            }
        }
    }

    suspend fun searchConcepts(query: String): List<com.marginalia.model.ConceptNote> {
        val book = currentBook ?: return emptyList()
        return if (query.isBlank()) emptyList()
        else conceptRegistry.getAllConcepts(book.territoryId)
            .filter { it.name.contains(query, ignoreCase = true) }
    }

    fun createConceptFromHighlight(
        conceptName: String,
        highlight: Highlight,
        onSuccess: (String) -> Unit,
        onError: () -> Unit
    ) {
        val book = currentBook ?: return onError()
        viewModelScope.launch(Dispatchers.IO) {
            val territory = bookToTerritory(book)
            when (val result = conceptRegistry.createFromHighlight(conceptName, highlight, book, territory)) {
                is Result.Success -> {
                    val wikilink = "[[${conceptName}]]"
                    val updatedHighlight = highlight.copy(conceptLink = wikilink)
                    highlightRepository.updateHighlight(updatedHighlight)
                    val updated = _highlights.value.map { if (it.id == highlight.id) updatedHighlight else it }
                    _highlights.value = updated
                    Log.d(TAG, "Concept created: $conceptName, wikilink added to highlight")
                    onSuccess(wikilink)
                    updateLinkedNote(book.id, updated)
                }
                is Result.Failure -> {
                    Log.e(TAG, "Failed to create concept: ${result.error}")
                    onError()
                }
            }
        }
    }

    // Read linked note file content for the quick-view panel
    suspend fun getLinkedNoteContent(): String? {
        val book = currentBook ?: return null
        val title = LinkedNoteGenerator.sanitiseFilename(book.title)
        val author = LinkedNoteGenerator.sanitiseFilename(book.author)
        return fileSystem.readFile("${book.territoryId}/notes/$title - $author.md")
    }

    private suspend fun recordSignals(highlight: Highlight) {
        val book = currentBook ?: return
        val candidates = SignalDetector.extractCandidates(highlight.text)
        val notePath = "${book.territoryId}/notes/${book.title} - ${book.author}.md"
        for (candidate in candidates) {
            try {
                registrySignalService.recordSignal(
                    conceptCandidate = candidate,
                    sourceType = SignalSourceType.HIGHLIGHT,
                    sourceId = highlight.id,
                    sourcePath = notePath,
                    territoryId = book.territoryId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record signal for '$candidate': ${e.message}")
            }
        }
    }

    private fun bookToTerritory(book: Book): Territory = Territory(
        id = book.territoryId,
        name = book.territoryId,
        type = TerritoryType.LIBRARY,
        folderPath = book.territoryId,
        symbol = TerritoryDefaults.defaultSymbol(TerritoryType.LIBRARY),
        shade = TerritoryDefaults.defaultShade(TerritoryType.LIBRARY),
        colour = null,
        ghostSymbolOpacity = 5,
        isPrivate = false,
        createdAt = 0L,
        lastEnteredAt = null
    )

    fun updateProgress(locator: Locator) {
        val book = currentBook ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val locatorJson = try { locator.toJSON().toString() } catch (e: Exception) { null }
            val percentage = locator.locations.progression?.toFloat()
                ?: book.readingProgress.percentage
            val updatedBook = book.copy(
                readingProgress = ReadingProgress.create(
                    cfi = locatorJson,
                    pageNumber = null,
                    percentage = percentage,
                    lastReadAt = System.currentTimeMillis()
                ),
                status = if (book.status == ReadingStatus.UNREAD) ReadingStatus.READING
                         else book.status,
                lastOpenedAt = System.currentTimeMillis()
            )
            currentBook = updatedBook
            when (val result = libraryRepository.updateBook(updatedBook)) {
                is Result.Success ->
                    Log.d(TAG, "Progress saved: ${(percentage * 100).toInt()}%")
                is Result.Failure ->
                    Log.e(TAG, "Failed to save progress: ${result.error}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scrollSettleJob?.cancel()
        inactivityTimeoutJob?.cancel()
        openPublication?.let { bookOpener.close(it) }
        openPublication = null
    }

    companion object {
        private const val TAG = "ReaderViewModel"
    }
}
