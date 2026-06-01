package com.marginalia.android.ui.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marginalia.android.di.AppSettings
import com.marginalia.android.di.VaultRootPath
import com.marginalia.device.DisplayRefreshManager
import com.marginalia.device.RefreshMode
import com.marginalia.model.Book
import com.marginalia.model.BookFormat
import com.marginalia.model.ReadingProgress
import com.marginalia.model.ReadingStatus
import com.marginalia.model.Result
import com.marginalia.settings.SettingsRegistry
import com.marginalia.vault.LibraryRepository
import com.marginalia.vault.RegistrySignalService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.inject.Inject

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    object Empty : LibraryUiState()
    data class Books(val list: List<Book>) : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    @ApplicationContext private val context: Context,
    @VaultRootPath private val vaultRootPath: String,
    private val displayRefreshManager: DisplayRefreshManager,
    private val settingsRegistry: SettingsRegistry,
    private val registrySignalService: RegistrySignalService
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _debugRefreshMode = MutableStateFlow<RefreshMode?>(null)
    val debugRefreshMode: StateFlow<RefreshMode?> = _debugRefreshMode.asStateFlow()

    private val _pendingCandidateCount = MutableStateFlow(0)
    val pendingCandidateCount: StateFlow<Int> = _pendingCandidateCount.asStateFlow()

    private var currentTerritoryId = "library"
    private var scrollSettleJob: Job? = null

    fun loadBooks(territoryId: String) {
        currentTerritoryId = territoryId
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
            checkPendingCandidates(territoryId)
        }
    }

    private fun checkPendingCandidates(territoryId: String) {
        val threshold = settingsRegistry.get(AppSettings.REGISTRY_SIGNAL_THRESHOLD)
        viewModelScope.launch(Dispatchers.IO) {
            val pending = registrySignalService.getPendingCandidates(territoryId, threshold)
            _pendingCandidateCount.value = pending.size
            if (pending.isNotEmpty()) {
                Log.d(TAG, "checkPendingCandidates: ${pending.size} candidates ready for review")
            }
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bookId = UUID.randomUUID().toString()
                val destRelative = "$currentTerritoryId/books/$bookId.epub"
                val destFile = File(vaultRootPath, destRelative)
                destFile.parentFile?.mkdirs()

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    Log.e(TAG, "importBook: could not open input stream for $uri")
                    return@launch
                }

                val (title, author) = extractEpubMetadata(destFile)

                val book = Book(
                    id = bookId,
                    title = title,
                    author = author,
                    format = BookFormat.EPUB,
                    filePath = destRelative,
                    coverPath = null,
                    addedAt = System.currentTimeMillis(),
                    lastOpenedAt = null,
                    readingProgress = ReadingProgress(null, null, 0f, null),
                    status = ReadingStatus.UNREAD,
                    territoryId = currentTerritoryId
                )

                when (val result = libraryRepository.addBook(book)) {
                    is Result.Success -> {
                        Log.d(TAG, "importBook: added book '$title' by $author")
                        withContext(Dispatchers.Main) {
                            loadBooks(currentTerritoryId)
                        }
                    }
                    is Result.Failure ->
                        Log.e(TAG, "importBook: failed to save book — ${result.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "importBook: exception — ${e.message}", e)
            }
        }
    }

    // --- Refresh events ---

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

    fun onAddBookTap() {
        displayRefreshManager.refreshDU()
        _debugRefreshMode.value = RefreshMode.DU
    }

    fun onFilePickerClose() {
        displayRefreshManager.refreshRegalFull()
        _debugRefreshMode.value = RefreshMode.REGAL
    }

    // --- End refresh events ---

    override fun onCleared() {
        super.onCleared()
        scrollSettleJob?.cancel()
    }

    private fun extractEpubMetadata(epubFile: File): Pair<String, String> {
        return try {
            ZipFile(epubFile).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml")
                    ?: return fallbackMetadata(epubFile)
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                val opfPath = Regex("""full-path="([^"]+)"""").find(containerXml)?.groupValues?.get(1)
                    ?: return fallbackMetadata(epubFile)
                val opfEntry = zip.getEntry(opfPath) ?: return fallbackMetadata(epubFile)
                val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
                val title = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.DOT_MATCHES_ALL)
                    .find(opfContent)?.groupValues?.get(1)?.trim()
                    ?.let { android.text.Html.fromHtml(it, 0).toString() }
                    ?: fallbackTitle(epubFile)
                val author = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", RegexOption.DOT_MATCHES_ALL)
                    .find(opfContent)?.groupValues?.get(1)?.trim()
                    ?.let { android.text.Html.fromHtml(it, 0).toString() }
                    ?: "Unknown"
                title to author
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractEpubMetadata: failed — ${e.message}")
            fallbackMetadata(epubFile)
        }
    }

    private fun fallbackMetadata(file: File): Pair<String, String> =
        file.nameWithoutExtension to "Unknown"

    private fun fallbackTitle(file: File): String =
        file.nameWithoutExtension

    companion object {
        private const val TAG = "LibraryViewModel"
    }
}
