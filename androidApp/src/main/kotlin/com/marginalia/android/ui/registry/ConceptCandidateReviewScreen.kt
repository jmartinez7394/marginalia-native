package com.marginalia.android.ui.registry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marginalia.model.RegistrySignal
import kotlin.math.roundToInt

@Composable
fun ConceptCandidateReviewScreen(
    territoryId: String,
    onBack: () -> Unit,
    viewModel: ConceptCandidateReviewViewModel = hiltViewModel()
) {
    LaunchedEffect(territoryId) { viewModel.loadCandidates(territoryId) }
    BackHandler { onBack() }

    val candidates by viewModel.candidates.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Concept Candidates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        when {
            candidates.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No pending candidates.\nAll concepts reviewed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            candidates.size >= 3 -> {
                // Batch swipe mode hint
                Text(
                    text = "${candidates.size} candidates — swipe right to accept, left to dismiss",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SwipableCandidateCard(
                    signal = candidates.first(),
                    onAccept = { viewModel.accept(candidates.first()) },
                    onDismiss = { viewModel.dismiss(candidates.first()) },
                    onDefer = { viewModel.defer(candidates.first()) }
                )
            }
            else -> {
                Text(
                    text = "${candidates.size} candidate${if (candidates.size > 1) "s" else ""} to review",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CandidateCard(
                    signal = candidates.first(),
                    onAccept = { viewModel.accept(candidates.first()) },
                    onDefer = { viewModel.defer(candidates.first()) },
                    onDismiss = { viewModel.dismiss(candidates.first()) }
                )
            }
        }
    }
}

@Composable
private fun CandidateCard(
    signal: RegistrySignal,
    onAccept: () -> Unit,
    onDefer: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Concept candidate name
            Text(
                text = "\"${signal.conceptCandidate}\"",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Occurrence count
            val booksText = if (signal.occurrenceCount == 1) "1 time" else "${signal.occurrenceCount} times"
            Text(
                text = "Appeared $booksText across your reading",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Source note path (parsed to book name)
            val sourceBook = sourceBookFromPath(signal.sourcePath)
            if (sourceBook.isNotEmpty()) {
                Text(
                    text = "From: $sourceBook",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action buttons — 2×2 grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                    Text("Accept as new concept", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Text("Link to existing", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onDefer, modifier = Modifier.weight(1f)) {
                    Text("Defer (7 days)", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Dismiss", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// Batch swipe mode card — swipe right = accept, swipe left = dismiss
@Composable
private fun SwipableCandidateCard(
    signal: RegistrySignal,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onDefer: () -> Unit
) {
    var offsetX by remember(signal.signalId) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(signal.signalId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            offsetX > 200f -> onAccept()   // swipe right = accept
                            offsetX < -200f -> onDismiss()  // swipe left = dismiss
                            else -> offsetX = 0f            // snap back
                        }
                    }
                ) { _, dragAmount ->
                    offsetX += dragAmount
                }
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "\"${signal.conceptCandidate}\"",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                val booksText = if (signal.occurrenceCount == 1) "1 time" else "${signal.occurrenceCount} times"
                Text(
                    text = "Appeared $booksText · swipe right to accept, left to dismiss",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val sourceBook = sourceBookFromPath(signal.sourcePath)
                if (sourceBook.isNotEmpty()) {
                    Text(
                        text = "From: $sourceBook",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onDefer, modifier = Modifier.fillMaxWidth()) {
                    Text("Defer (7 days)")
                }
            }
        }
    }
}

private fun sourceBookFromPath(path: String): String {
    // "library/notes/Book Title - Author.md" → "Book Title - Author"
    val filename = path.substringAfterLast("/").removeSuffix(".md")
    return filename
}
