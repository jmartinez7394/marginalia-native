package com.marginalia.android.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marginalia.model.Book
import com.marginalia.vault.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    object Empty : LibraryUiState()
    data class Books(val list: List<Book>) : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun loadBooks(territoryId: String) {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            try {
                val books = libraryRepository.getAllBooks(territoryId)
                Log.d(TAG, "loadBooks($territoryId): ${books.size} books returned")
                _uiState.value = if (books.isEmpty()) LibraryUiState.Empty
                else LibraryUiState.Books(books)
            } catch (e: Exception) {
                Log.e(TAG, "loadBooks($territoryId): exception — ${e.message}", e)
                _uiState.value = LibraryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    companion object {
        private const val TAG = "LibraryViewModel"
    }
}
