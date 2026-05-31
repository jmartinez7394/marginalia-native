package com.marginalia.android.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marginalia.android.R
import com.marginalia.model.Book
import com.marginalia.model.ReadingStatus

@Composable
fun LibraryScreen(
    territoryId: String,
    onBookClick: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    LaunchedEffect(territoryId) {
        viewModel.loadBooks(territoryId)
    }

    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is LibraryUiState.Loading -> LibraryLoadingState()
        is LibraryUiState.Empty -> LibraryEmptyState()
        is LibraryUiState.Books -> LibraryBookGrid(state.list, onBookClick)
        is LibraryUiState.Error -> LibraryErrorState(state.message) {
            viewModel.loadBooks(territoryId)
        }
    }
}

@Composable
private fun LibraryLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.Black)
            Text(
                text = stringResource(R.string.library_loading),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun LibraryEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.library_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Button(
                onClick = {},
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.library_add_book))
            }
        }
    }
}

@Composable
private fun LibraryErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(stringResource(R.string.library_error_retry))
            }
        }
    }
}

@Composable
private fun LibraryBookGrid(books: List<Book>, onBookClick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(books, key = { it.id }) { book ->
            BookCard(book = book, onClick = { onBookClick(book.id) })
        }
    }
}

@Composable
private fun BookCard(book: Book, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Color.White)
    ) {
        // Cover placeholder — greyscale rectangle until real covers are loaded
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = book.title.take(1),
                fontSize = 32.sp,
                color = Color(0xFF808080),
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = readingStatusLabel(book.status),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Progress bar at bottom of card
        if (book.readingProgress.percentage > 0f) {
            LinearProgressIndicator(
                progress = { book.readingProgress.percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color.Black,
                trackColor = Color(0xFFE0E0E0)
            )
        }
    }
}

@Composable
private fun readingStatusLabel(status: ReadingStatus): String = when (status) {
    ReadingStatus.UNREAD -> stringResource(R.string.library_reading_status_unread)
    ReadingStatus.READING -> stringResource(R.string.library_reading_status_reading)
    ReadingStatus.FINISHED -> stringResource(R.string.library_reading_status_finished)
    ReadingStatus.ABANDONED -> stringResource(R.string.library_reading_status_abandoned)
}
