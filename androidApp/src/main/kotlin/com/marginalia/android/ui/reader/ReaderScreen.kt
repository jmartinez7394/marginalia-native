package com.marginalia.android.ui.reader

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.marginalia.android.BuildConfig
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
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.KeyEvent
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.publication.Publication

private const val TAG = "ReaderScreen"
private const val HIGHLIGHT_GROUP = "highlights"
private const val SELECTION_POLL_MS = 300L
private const val SELECTION_STABLE_MS = 1200L

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
                initialLocator = state.initialLocator,
                viewModel = viewModel,
                onExit = onExit
            )
            is ReaderUiState.Error -> ReaderErrorState(
                message = state.message,
                onBack = onExit
            )
        }

        if (BuildConfig.DEBUG) {
            val debugMode by viewModel.debugRefreshMode.collectAsState()
            RefreshDebugOverlay(
                lastMode = debugMode,
                modifier = Modifier.align(Alignment.TopEnd)
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
    initialLocator: org.readium.r2.shared.publication.Locator?,
    viewModel: ReaderViewModel,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    val fragmentManager = activity.supportFragmentManager
    val containerId = remember { View.generateViewId() }
    var navigatorFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    val highlights by viewModel.currentHighlights.collectAsState()

    // Colour picker state (new highlight being created from selection)
    var showColourPicker by remember { mutableStateOf(false) }
    var pendingSelectionCfi by remember { mutableStateOf("") }
    var pendingSelectionHref by remember { mutableStateOf("") }
    var pendingSelectionLocatorJson by remember { mutableStateOf("") }
    var pendingSelectionText by remember { mutableStateOf("") }

    // Annotation sheet state (existing highlight tapped for editing)
    val selectedHighlightIdRef = remember { mutableStateOf<String?>(null) }
    var selectedHighlightId by selectedHighlightIdRef
    val selectedHighlight = highlights.find { it.id == selectedHighlightId }

    val colourPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // Fire GC16 when app returns to foreground while the reader is open.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onForegroundRestore()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Fire DU when colour picker opens (new highlight creation).
    LaunchedEffect(showColourPicker) {
        if (showColourPicker) viewModel.onAnnotationOpen()
    }

    // Fire DU when annotation sheet opens (existing highlight editing).
    LaunchedEffect(selectedHighlight) {
        if (selectedHighlight != null) viewModel.onAnnotationOpen()
    }

    // Full-screen FragmentContainerView. Background matches reading theme to prevent
    // white flash on WebView initialise and reflow.
    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = containerId
                setBackgroundColor(Color.WHITE)
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(publication) {
        val handler = Handler(Looper.getMainLooper())
        var registeredInputListener: InputListener? = null

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
            val fragmentFactory = navigatorFactory.createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = EpubPreferences(
                    spread = Spread.NEVER,
                    columnCount = ColumnCount.ONE
                )
            )
            fragmentManager.fragmentFactory = fragmentFactory
            fragmentManager.beginTransaction()
                .replace(containerId, EpubNavigatorFragment::class.java, null)
                .commitNow()
            val fragment = fragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
            navigatorFragment = fragment
            Log.d(TAG, "EpubNavigatorFragment added: $fragment")

            if (fragment != null) {
                fragment.view?.setBackgroundColor(Color.WHITE)

                // InputListener wired after the WebView processes taps, so long-press (text
                // selection) is never intercepted. onDrag fires during scroll and selection drag.
                val tapListener = object : InputListener {
                    override fun onTap(event: TapEvent): Boolean {
                        val widthPx = fragment.view?.width?.toFloat() ?: return false
                        return when {
                            event.point.x < widthPx * 0.30f -> {
                                fragment.goBackward(animated = false)
                                viewModel.onPageTurn()
                                true
                            }
                            event.point.x > widthPx * 0.70f -> {
                                fragment.goForward(animated = false)
                                viewModel.onPageTurn()
                                true
                            }
                            else -> false
                        }
                    }
                    override fun onDrag(event: DragEvent): Boolean {
                        viewModel.onScrollActive()
                        viewModel.scheduleScrollSettle()
                        return false
                    }
                    override fun onKey(event: KeyEvent): Boolean = false
                }
                fragment.addInputListener(tapListener)
                registeredInputListener = tapListener
            }
        }

        handler.post(addFragmentRunnable)

        onDispose {
            handler.removeCallbacks(addFragmentRunnable)
            val fragment = fragmentManager.findFragmentById(containerId)
            registeredInputListener?.let {
                (fragment as? EpubNavigatorFragment)?.removeInputListener(it)
            }
            navigatorFragment = null
            if (!fragmentManager.isStateSaved) {
                fragment?.let {
                    Log.d(TAG, "Removing EpubNavigatorFragment")
                    fragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
                }
            }
        }
    }

    // Register decoration activation listener so tapping a highlight opens the annotation sheet.
    // Decoration.Style.Highlight with isActive=true makes the decoration fire this listener.
    DisposableEffect(navigatorFragment) {
        val fragment = navigatorFragment
            ?: return@DisposableEffect onDispose {}
        val decorable = fragment as? DecorableNavigator
            ?: return@DisposableEffect onDispose {}
        val decorationListener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                if (event.group == HIGHLIGHT_GROUP) {
                    selectedHighlightIdRef.value = event.decoration.id
                    return true
                }
                return false
            }
        }
        decorable.addDecorationListener(HIGHLIGHT_GROUP, decorationListener)
        onDispose {
            decorable.removeDecorationListener(decorationListener)
        }
    }

    // Observe navigator position and persist reading progress on every page change.
    LaunchedEffect(navigatorFragment) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        fragment.currentLocator.collect { locator ->
            viewModel.updateProgress(locator)
        }
    }

    // Apply highlight decorations whenever the list changes.
    // Non-null extras Bundle makes each decoration activatable via onDecorationActivated.
    LaunchedEffect(navigatorFragment, highlights) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        val decorable = fragment as? DecorableNavigator ?: return@LaunchedEffect
        val decorations = highlights.mapNotNull { it.toDecoration() }
        Log.d(TAG, "applyDecorations: ${highlights.size} highlights → ${decorations.size} decorations")
        decorable.applyDecorations(decorations, HIGHLIGHT_GROUP)
    }

    // Poll for text selection. SelectableNavigator has no callback API; polling is
    // the only option. We wait for the selection to be STABLE (same text for
    // SELECTION_STABLE_MS) before showing the picker — this gives the user time to
    // drag the selection handles to extend the selection before we consume it.
    LaunchedEffect(navigatorFragment) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        val selectable = fragment as? SelectableNavigator ?: return@LaunchedEffect
        var stableText = ""
        var stableCfi = ""
        var stableHref = ""
        var stableLocatorJson = ""
        var stableStart = 0L
        while (true) {
            delay(SELECTION_POLL_MS)
            val selection = selectable.currentSelection()
            if (selection == null) {
                stableText = ""
                stableStart = 0L
                continue
            }
            val text = selection.locator.text.highlight
            if (text.isNullOrBlank()) {
                stableText = ""
                stableStart = 0L
                continue
            }
            val cfi = selection.locator.locations.fragments.firstOrNull() ?: ""
            val href = selection.locator.href.toString()
            val locatorJson = try { selection.locator.toJSON().toString() } catch (e: Exception) { "" }
            val now = System.currentTimeMillis()
            if (text != stableText) {
                Log.d(TAG, "Selection detected: text='$text' href='$href' cfi='$cfi'")
                viewModel.onTextSelectionStart()
                stableText = text
                stableCfi = cfi
                stableHref = href
                stableLocatorJson = locatorJson
                stableStart = now
            } else {
                // Update CFI and locator in case Readium's JS bridge fills them asynchronously
                if (cfi.isNotEmpty()) stableCfi = cfi
                if (href.isNotEmpty()) stableHref = href
                if (locatorJson.isNotEmpty()) stableLocatorJson = locatorJson
                if (now - stableStart >= SELECTION_STABLE_MS) {
                    viewModel.onTextSelectionEnd()
                    pendingSelectionCfi = stableCfi
                    pendingSelectionHref = stableHref
                    pendingSelectionLocatorJson = stableLocatorJson
                    pendingSelectionText = stableText
                    showColourPicker = true
                    selectable.clearSelection()
                    stableText = ""
                    stableStart = 0L
                }
            }
        }
    }

    // New highlight creation — colour picker sheet
    if (showColourPicker) {
        ModalBottomSheet(
            onDismissRequest = { showColourPicker = false },
            sheetState = colourPickerSheetState
        ) {
            HighlightColourPicker(
                onColourSelected = { colour ->
                    viewModel.createHighlight(
                        pendingSelectionCfi,
                        pendingSelectionHref,
                        pendingSelectionLocatorJson,
                        pendingSelectionText,
                        colour
                    )
                    coroutineScope.launch {
                        colourPickerSheetState.hide()
                        showColourPicker = false
                    }
                },
                onDismiss = {
                    coroutineScope.launch {
                        colourPickerSheetState.hide()
                        showColourPicker = false
                    }
                }
            )
        }
    }

    // Existing highlight editing — annotation sheet
    if (selectedHighlight != null) {
        AnnotationBottomSheet(
            highlight = selectedHighlight,
            onSave = { annotation, colour ->
                viewModel.updateHighlight(
                    selectedHighlight.copy(
                        annotation = annotation.trim().ifEmpty { null },
                        colour = colour
                    )
                )
                selectedHighlightId = null
            },
            onDelete = {
                viewModel.deleteHighlight(selectedHighlight.id)
                selectedHighlightId = null
            },
            onDismiss = { selectedHighlightId = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnotationBottomSheet(
    highlight: Highlight,
    onSave: (annotation: String, colour: HighlightColour) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var annotationText by remember(highlight.id) { mutableStateOf(highlight.annotation ?: "") }
    var selectedColour by remember(highlight.id) { mutableStateOf(highlight.colour) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Read-only passage preview — greyed context
            val preview = highlight.text.take(160).let {
                if (highlight.text.length > 160) "$it…" else it
            }
            Text(
                text = "\"$preview\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Annotation input
            OutlinedTextField(
                value = annotationText,
                onValueChange = { annotationText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.highlight_annotation_hint)) },
                minLines = 3,
                maxLines = 6
            )

            // Colour selection — filled Button for selected, OutlinedButton for others
            Text(
                text = stringResource(R.string.highlight_colour_change),
                style = MaterialTheme.typography.labelMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColourButton(
                    colour = HighlightColour.YELLOW,
                    selected = selectedColour == HighlightColour.YELLOW,
                    label = stringResource(R.string.highlight_colour_yellow),
                    onSelect = { selectedColour = it },
                    modifier = Modifier.weight(1f)
                )
                ColourButton(
                    colour = HighlightColour.GREEN,
                    selected = selectedColour == HighlightColour.GREEN,
                    label = stringResource(R.string.highlight_colour_green),
                    onSelect = { selectedColour = it },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColourButton(
                    colour = HighlightColour.BLUE,
                    selected = selectedColour == HighlightColour.BLUE,
                    label = stringResource(R.string.highlight_colour_blue),
                    onSelect = { selectedColour = it },
                    modifier = Modifier.weight(1f)
                )
                ColourButton(
                    colour = HighlightColour.PINK,
                    selected = selectedColour == HighlightColour.PINK,
                    label = stringResource(R.string.highlight_colour_pink),
                    onSelect = { selectedColour = it },
                    modifier = Modifier.weight(1f)
                )
            }

            // Save
            Button(
                onClick = {
                    val annotation = annotationText
                    val colour = selectedColour
                    scope.launch {
                        sheetState.hide()
                        onSave(annotation, colour)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.highlight_annotation_save))
            }

            // Delete
            OutlinedButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDelete()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.highlight_annotation_delete))
            }
        }
    }
}

@Composable
private fun ColourButton(
    colour: HighlightColour,
    selected: Boolean,
    label: String,
    onSelect: (HighlightColour) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = { onSelect(colour) }, modifier = modifier) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        OutlinedButton(onClick = { onSelect(colour) }, modifier = modifier) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
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

private fun Highlight.toDecoration(): Decoration? {
    // Greyscale tints for e-ink: YELLOW=lightest (background highlight), PINK=darkest.
    val tint = when (colour) {
        HighlightColour.YELLOW -> Color.argb(100, 200, 200, 200)
        HighlightColour.GREEN -> Color.argb(120, 160, 160, 160)
        HighlightColour.BLUE -> Color.argb(140, 120, 120, 120)
        HighlightColour.PINK -> Color.argb(160, 80, 80, 80)
    }
    // Prefer the complete stored locator (preserves CFI, text context, progression).
    val locator = when {
        locatorJson.isNotEmpty() -> try {
            org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(locatorJson))
        } catch (e: Exception) {
            android.util.Log.e("ReaderScreen", "toDecoration: locatorJson parse failed", e)
            null
        }
        href.isNotEmpty() -> try {
            org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject("{\"href\":${
                org.json.JSONObject.quote(href)
            },\"type\":\"application/xhtml+xml\",\"locations\":{\"fragments\":[\"$cfi\"]},\"text\":{\"highlight\":${
                org.json.JSONObject.quote(text)
            }}}"))
        } catch (e: Exception) {
            null
        }
        else -> null
    }
    if (locator == null) {
        android.util.Log.w("ReaderScreen", "toDecoration: null locator for href=$href cfi=$cfi")
        return null
    }
    // isActive=true on Highlight style makes this decoration activatable — Readium fires
    // DecorableNavigator.Listener.onDecorationActivated when the user taps it.
    return Decoration(
        id = id,
        locator = locator,
        style = Decoration.Style.Highlight(tint = tint, isActive = true)
    )
}
