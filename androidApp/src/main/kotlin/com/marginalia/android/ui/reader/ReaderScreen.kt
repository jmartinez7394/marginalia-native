package com.marginalia.android.ui.reader

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.hilt.navigation.compose.hiltViewModel
import com.marginalia.android.R
import com.marginalia.model.Highlight
import com.marginalia.model.HighlightColour
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication

private const val TAG = "ReaderScreen"
private const val HIGHLIGHT_GROUP = "highlights"
private const val SELECTION_POLL_MS = 250L

@Composable
fun ReaderScreen(
    bookId: String,
    onExit: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(bookId) {
        viewModel.openBook(bookId)
    }

    BackHandler { onExit() }

    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState()
            is ReaderUiState.Ready -> ReadyReader(
                publication = state.publication,
                viewModel = viewModel,
                onExit = onExit
            )
            is ReaderUiState.Error -> ReaderErrorState(
                message = state.message,
                onBack = onExit
            )
        }
    }
}

@Composable
private fun ReaderLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.Black)
            Text(
                text = stringResource(R.string.reader_loading),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ReaderErrorState(message: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.reader_error_back))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyReader(
    publication: Publication,
    viewModel: ReaderViewModel,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    val fragmentManager = activity.supportFragmentManager
    val containerId = remember { View.generateViewId() }
    var navigatorFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    val highlights by viewModel.currentHighlights.collectAsState()

    // Bottom sheet state for colour picker
    var showColourPicker by remember { mutableStateOf(false) }
    var pendingSelectionCfi by remember { mutableStateOf("") }
    var pendingSelectionText by remember { mutableStateOf("") }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // Full-screen FragmentContainerView for the Readium WebView
    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply { id = containerId }
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(publication) {
        val handler = Handler(Looper.getMainLooper())

        val addFragmentRunnable = Runnable {
            if (fragmentManager.isStateSaved) return@Runnable

            val existing = fragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
            if (existing != null) {
                Log.d(TAG, "EpubNavigatorFragment already present, reusing")
                navigatorFragment = existing
                return@Runnable
            }

            Log.d(TAG, "Adding EpubNavigatorFragment to container $containerId")
            val navigatorFactory = EpubNavigatorFactory(publication)
            val fragmentFactory = navigatorFactory.createFragmentFactory(initialLocator = null)
            fragmentManager.fragmentFactory = fragmentFactory
            fragmentManager.beginTransaction()
                .replace(containerId, EpubNavigatorFragment::class.java, null)
                .commitNow()
            navigatorFragment = fragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
            Log.d(TAG, "EpubNavigatorFragment added: $navigatorFragment")
        }

        handler.post(addFragmentRunnable)

        onDispose {
            handler.removeCallbacks(addFragmentRunnable)
            navigatorFragment = null
            if (!fragmentManager.isStateSaved) {
                fragmentManager.findFragmentById(containerId)?.let {
                    Log.d(TAG, "Removing EpubNavigatorFragment")
                    fragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
                }
            }
        }
    }

    // Apply highlight decorations whenever highlight list changes
    LaunchedEffect(navigatorFragment, highlights) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        val decorable = fragment as? DecorableNavigator ?: return@LaunchedEffect
        val decorations = highlights.map { it.toDecoration() }
        decorable.applyDecorations(decorations, HIGHLIGHT_GROUP)
    }

    // Poll for text selection — shows colour picker when user selects text
    LaunchedEffect(navigatorFragment) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        val selectable = fragment as? SelectableNavigator ?: return@LaunchedEffect
        while (true) {
            delay(SELECTION_POLL_MS)
            val selection = selectable.currentSelection() ?: continue
            val text = selection.locator.text.highlight ?: continue
            if (text.isBlank()) continue
            val cfi = selection.locator.locations.fragments.firstOrNull() ?: ""
            pendingSelectionCfi = cfi
            pendingSelectionText = text
            showColourPicker = true
            selectable.clearSelection()
        }
    }

    // Colour picker bottom sheet
    if (showColourPicker) {
        ModalBottomSheet(
            onDismissRequest = { showColourPicker = false },
            sheetState = bottomSheetState
        ) {
            HighlightColourPicker(
                onColourSelected = { colour ->
                    viewModel.createHighlight(pendingSelectionCfi, pendingSelectionText, colour)
                    coroutineScope.launch {
                        bottomSheetState.hide()
                        showColourPicker = false
                    }
                },
                onDismiss = {
                    coroutineScope.launch {
                        bottomSheetState.hide()
                        showColourPicker = false
                    }
                }
            )
        }
    }

    // Gesture overlay — tap zones and swipe-to-exit
    var swipeStartY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(navigatorFragment) {
                detectTapGestures { offset ->
                    val widthPx = size.width.toFloat()
                    val fragment = navigatorFragment ?: return@detectTapGestures
                    when {
                        offset.x < widthPx * 0.30f -> {
                            fragment.goBackward(animated = false)
                            viewModel.onPageTurn()
                        }
                        offset.x > widthPx * 0.70f -> {
                            fragment.goForward(animated = false)
                            viewModel.onPageTurn()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> swipeStartY = offset.y },
                    onDragEnd = {},
                    onDragCancel = {}
                ) { _, dragAmount ->
                    if (swipeStartY < size.height * 0.15f && dragAmount > 40f) {
                        onExit()
                    }
                }
            }
    )
}

@Composable
private fun HighlightColourPicker(
    onColourSelected: (HighlightColour) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.highlight_add_annotation),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onColourSelected(HighlightColour.YELLOW) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.highlight_colour_yellow)) }
            OutlinedButton(
                onClick = { onColourSelected(HighlightColour.GREEN) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.highlight_colour_green)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onColourSelected(HighlightColour.BLUE) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.highlight_colour_blue)) }
            OutlinedButton(
                onClick = { onColourSelected(HighlightColour.PINK) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.highlight_colour_pink)) }
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            Text(stringResource(R.string.highlight_delete))
        }
    }
}

private fun Highlight.toDecoration(): Decoration {
    val tint = when (colour) {
        HighlightColour.YELLOW -> Color.argb(255, 220, 220, 220) // light grey
        HighlightColour.GREEN -> Color.argb(255, 180, 180, 180)  // medium grey
        HighlightColour.BLUE -> Color.argb(255, 140, 140, 140)   // dark grey
        HighlightColour.PINK -> Color.argb(255, 160, 160, 160)   // slightly darker grey
    }
    val locator = try {
        org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject("{\"href\":\"\",\"type\":\"application/xhtml+xml\",\"locations\":{\"fragments\":[\"$cfi\"]},\"text\":{\"highlight\":${
            org.json.JSONObject.quote(text)
        }}}"))
    } catch (e: Exception) {
        null
    }
    return Decoration(
        id = id,
        locator = locator ?: org.readium.r2.shared.publication.Locator(
            href = org.readium.r2.shared.util.Url("about:blank")!!,
            mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML
        ),
        style = Decoration.Style.Underline(tint = tint)
    )
}
