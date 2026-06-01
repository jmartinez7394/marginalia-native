package com.marginalia.android.ui.registry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marginalia.android.R
import com.marginalia.model.ConceptNote
import com.marginalia.model.ConceptStatus

@Composable
fun ConceptRegistryScreen(
    territoryId: String,
    onBack: () -> Unit,
    onReviewCandidates: () -> Unit = {},
    pendingCandidateCount: Int = 0,
    viewModel: ConceptRegistryViewModel = hiltViewModel()
) {
    LaunchedEffect(territoryId) {
        viewModel.loadConcepts(territoryId)
    }

    BackHandler { onBack() }

    val vmPendingCount by viewModel.pendingCandidateCount.collectAsState()
    val effectivePendingCount = if (vmPendingCount > 0) vmPendingCount else pendingCandidateCount
    var selectedConcept by remember { mutableStateOf<ConceptNote?>(null) }

    if (selectedConcept != null) {
        ConceptNoteEditor(
            concept = selectedConcept!!,
            viewModel = viewModel,
            onBack = { selectedConcept = null }
        )
    } else {
        ConceptListContent(
            viewModel = viewModel,
            pendingCandidateCount = effectivePendingCount,
            onConceptClick = { selectedConcept = it },
            onReviewCandidates = onReviewCandidates,
            onBack = onBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConceptListContent(
    viewModel: ConceptRegistryViewModel,
    pendingCandidateCount: Int = 0,
    onConceptClick: (ConceptNote) -> Unit,
    onReviewCandidates: () -> Unit = {},
    onBack: () -> Unit
) {
    val concepts by viewModel.filteredConcepts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf<ConceptStatus?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.registry_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.reader_error_back))
            }
        }

        // Pending candidates banner
        if (pendingCandidateCount > 0) {
            OutlinedButton(
                onClick = onReviewCandidates,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "$pendingCandidateCount concept candidate${if (pendingCandidateCount > 1) "s" else ""} ready for review",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it; viewModel.setSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.registry_search_hint)) },
            singleLine = true
        )

        // Filter tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(label = stringResource(R.string.registry_filter_all), active = activeFilter == null) {
                activeFilter = null; viewModel.setFilter(null)
            }
            FilterChip(label = stringResource(R.string.registry_filter_settled), active = activeFilter == ConceptStatus.SETTLED) {
                activeFilter = ConceptStatus.SETTLED; viewModel.setFilter(ConceptStatus.SETTLED)
            }
            FilterChip(label = stringResource(R.string.registry_filter_developing), active = activeFilter == ConceptStatus.DEVELOPING) {
                activeFilter = ConceptStatus.DEVELOPING; viewModel.setFilter(ConceptStatus.DEVELOPING)
            }
            FilterChip(label = stringResource(R.string.registry_filter_seeds), active = activeFilter == ConceptStatus.SEED) {
                activeFilter = ConceptStatus.SEED; viewModel.setFilter(ConceptStatus.SEED)
            }
        }

        if (concepts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.registry_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(concepts, key = { it.id }) { concept ->
                    ConceptListItem(concept = concept, onClick = { onConceptClick(concept) })
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    if (active) {
        Button(onClick = onClick) { Text(label, style = MaterialTheme.typography.labelSmall) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun ConceptListItem(concept: ConceptNote, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = concept.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = statusIndicator(concept.status),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun statusIndicator(status: ConceptStatus) = when (status) {
    ConceptStatus.SETTLED -> "● settled"
    ConceptStatus.DEVELOPING -> "◐ developing"
    ConceptStatus.SEED -> "○ seed"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConceptNoteEditor(
    concept: ConceptNote,
    viewModel: ConceptRegistryViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val fileContent by produceState(initialValue = "") {
        value = viewModel.loadConceptContent(concept)
    }
    var bodyText by remember(fileContent) { mutableStateOf(extractBodyFromContent(fileContent)) }
    var selectedStatus by remember(concept) { mutableStateOf(concept.status) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = concept.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        // Status selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConceptStatus.values().forEach { status ->
                if (status == selectedStatus) {
                    Button(onClick = {}) { Text(status.name.lowercase(), style = MaterialTheme.typography.labelSmall) }
                } else {
                    OutlinedButton(onClick = { selectedStatus = status }) {
                        Text(status.name.lowercase(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Editable body text
        OutlinedTextField(
            value = bodyText,
            onValueChange = { bodyText = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text(stringResource(R.string.registry_editor_hint)) },
            minLines = 5
        )

        // Save button
        Button(
            onClick = { viewModel.saveConcept(concept, selectedStatus, bodyText); onBack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.highlight_annotation_save))
        }
    }
}

private fun extractBodyFromContent(content: String): String {
    if (content.isBlank()) return ""
    val endFm = content.indexOf("---\n", 3).takeIf { it >= 0 }?.let { it + 4 } ?: return content
    return content.substring(endFm).trimStart('\n')
}
