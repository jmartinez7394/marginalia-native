package com.marginalia.android.ui.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marginalia.android.platform.reader.OpenPublicationResult
import com.marginalia.android.platform.reader.ReadiumBookOpener
import com.marginalia.device.DisplayRefreshManager
import com.marginalia.model.Highlight
import com.marginalia.model.HighlightColour
import com.marginalia.model.Result
import com.marginalia.vault.HighlightRepository
import com.marginalia.vault.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val displayRefreshManager: DisplayRefreshManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _highlights = MutableStateFlow<List<Highlight>>(emptyList())
    val currentHighlights: StateFlow<List<Highlight>> = _highlights.asStateFlow()

    private var openPublication: Publication? = null
    private var currentBookId: String? = null

    fun openBook(bookId: String) {
        viewModelScope.launch {
            currentBookId = bookId
            _uiState.value = ReaderUiState.Loading
            val book = libraryRepository.getBook(bookId)
                ?: run {
                    _uiState.value = ReaderUiState.Error("Book not found")
                    return@launch
                }

            // Load existing highlights
            val highlights = highlightRepository.getHighlights(bookId)
            _highlights.value = highlights

            when (val result = bookOpener.open(book.filePath, book.format)) {
                is OpenPublicationResult.Success -> {
                    openPublication = result.publication
                    Log.d(TAG, "Publication opened, emitting Ready")
                    _uiState.value = ReaderUiState.Ready(
                        publication = result.publication,
                        initialLocator = null
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

    fun createHighlight(cfi: String, text: String, colour: HighlightColour) {
        val bookId = currentBookId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val highlight = Highlight(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                cfi = cfi,
                text = text,
                colour = colour,
                annotation = null,
                createdAt = System.currentTimeMillis(),
                pageNumber = null
            )
            when (val result = highlightRepository.addHighlight(highlight)) {
                is Result.Success -> {
                    _highlights.value = _highlights.value + highlight
                    Log.d(TAG, "Highlight created: ${highlight.id}")
                }
                is Result.Failure ->
                    Log.e(TAG, "Failed to save highlight: ${result.error}")
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

    fun onPageTurn() {
        displayRefreshManager.refreshFull()
    }

    fun updateProgress(locator: Locator) {
        // Progress persistence deferred to reading progress session
    }

    override fun onCleared() {
        super.onCleared()
        openPublication?.let { bookOpener.close(it) }
        openPublication = null
    }

    companion object {
        private const val TAG = "ReaderViewModel"
    }
}
