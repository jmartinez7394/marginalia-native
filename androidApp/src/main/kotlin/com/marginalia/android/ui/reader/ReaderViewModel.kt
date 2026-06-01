package com.marginalia.android.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marginalia.android.platform.reader.OpenPublicationResult
import com.marginalia.android.platform.reader.ReadiumBookOpener
import com.marginalia.device.DisplayRefreshManager
import com.marginalia.vault.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
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
    private val displayRefreshManager: DisplayRefreshManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var openPublication: Publication? = null

    fun openBook(bookId: String) {
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            val book = libraryRepository.getBook(bookId)
                ?: run {
                    _uiState.value = ReaderUiState.Error("Book not found")
                    return@launch
                }

            // Locator restoration deferred — will be implemented in reading progress session
            when (val result = bookOpener.open(book.filePath, book.format)) {
                is OpenPublicationResult.Success -> {
                    openPublication = result.publication
                    android.util.Log.d("ReaderViewModel", "Publication opened, emitting Ready")
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
}
