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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.foundation.layout.wrapContentSize
import com.marginalia.android.BuildConfig
import com.marginalia.android.R
import com.marginalia.android.di.AppSettings
import com.marginalia.model.ConceptNote
import com.marginalia.model.EmotionalTag
import com.marginalia.model.Highlight
import com.marginalia.model.HighlightColour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val activeHighlightColour by viewModel.activeHighlightColour.collectAsState()
    val annotationModeActive by viewModel.annotationModeActive.collectAsState()

    // Highlighter colour picker (opened from always-visible button)
    var showHighlighterColourPicker by remember { mutableStateOf(false) }
    val highlighterPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenScope = rememberCoroutineScope()

    // Pen settings sheet
    var showPenSettings by remember { mutableStateOf(false) }
    val penSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

        // Always-visible buttons — top-right, visible whenever the reader is not in error/loading
        if (uiState !is ReaderUiState.Error) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = { showHighlighterColourPicker = true },
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = "${colourDot(activeHighlightColour)} ${stringResource(R.string.annotation_highlighter_button)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                OutlinedButton(
                    onClick = { showPenSettings = true },
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = stringResource(R.string.annotation_pen_settings_button),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Annotation mode active indicator — top-left, subtle
        if (annotationModeActive) {
            Text(
                text = stringResource(R.string.annotation_mode_active),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 8.dp, start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (BuildConfig.DEBUG) {
            val debugMode by viewModel.debugRefreshMode.collectAsState()
            RefreshDebugOverlay(
                lastMode = debugMode,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 36.dp)
            )
        }
    }

    // Highlighter colour picker sheet (from always-visible button)
    if (showHighlighterColourPicker) {
        ModalBottomSheet(
            onDismissRequest = { showHighlighterColourPicker = false },
            sheetState = highlighterPickerSheetState
        ) {
            HighlighterColourSelector(
                currentColour = activeHighlightColour,
                onColourSelected = { colour ->
                    viewModel.setHighlightColour(colour)
                    screenScope.launch {
                        highlighterPickerSheetState.hide()
                        showHighlighterColourPicker = false
                    }
                },
                onDismiss = {
                    screenScope.launch {
                        highlighterPickerSheetState.hide()
                        showHighlighterColourPicker = false
                    }
                }
            )
        }
    }

    // Pen settings sheet
    if (showPenSettings) {
        ModalBottomSheet(
            onDismissRequest = { showPenSettings = false },
            sheetState = penSettingsSheetState
        ) {
            PenSettingsSheet(
                viewModel = viewModel,
                onDismiss = {
                    screenScope.launch {
                        penSettingsSheetState.hide()
                        showPenSettings = false
                    }
                }
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

    // Chrome state ref declared early so the InputListener closure inside DisposableEffect can capture it
    val showChromeRef = remember { mutableStateOf(false) }

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

    // Annotation overlay — renders saved ink strokes for the current page (above Readium, below InkOverlay).
    val visibleStrokes by viewModel.visibleAnnotationStrokes.collectAsState()
    val annotationOverlayRef = remember { mutableStateOf<AnnotationOverlayView?>(null) }
    AndroidView(
        factory = { ctx ->
            AnnotationOverlayView(ctx).also {
                annotationOverlayRef.value = it
                it.setBackgroundColor(Color.TRANSPARENT)
            }
        },
        update = { overlay ->
            overlay.updateStrokes(visibleStrokes)
        },
        modifier = Modifier.fillMaxSize()
    )

    // Ink overlay — positioned above Readium, transparent to input when annotation inactive.
    // Captures stylus events and double-tap in annotation mode. Double-tap works for all
    // input types so the emulator can trigger annotation mode without a physical stylus.
    val inkOverlayRef = remember { mutableStateOf<InkOverlayView?>(null) }
    val annotationModeFromVm by viewModel.annotationModeActive.collectAsState()
    val doubleTapThreshold = settingsValue(viewModel, AppSettings.ANNOTATION_DOUBLE_TAP_THRESHOLD_MS)
    val doubleTapTolerance = settingsValue(viewModel, AppSettings.ANNOTATION_DOUBLE_TAP_TOLERANCE_PX)
    val strokeWidth = settingsValue(viewModel, AppSettings.PEN_STROKE_WIDTH)

    AndroidView(
        factory = { ctx ->
            InkOverlayView(ctx).also { overlay ->
                inkOverlayRef.value = overlay
                overlay.onDoubleTap = { viewModel.activateAnnotationMode() }
                overlay.onStrokeBegin = { x, y, p, t -> viewModel.onAnnotationStrokeBegin(x, y, p, t) }
                overlay.onStrokePoint = { x, y, p, t -> viewModel.onAnnotationStrokePoint(x, y, p, t) }
                overlay.onStrokeComplete = { viewModel.onAnnotationStrokeComplete() }
                overlay.setBackgroundColor(Color.TRANSPARENT)
            }
        },
        update = { overlay ->
            overlay.annotationModeActive = annotationModeFromVm
            overlay.doubleTapThresholdMs = doubleTapThreshold
            overlay.doubleTapTolerancePx = doubleTapTolerance.toFloat()
            overlay.strokeWidthPx = strokeWidth.toFloat() * 2f // dp-ish scaling
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
                            else -> {
                                // Centre zone — toggle reader chrome (DU refresh)
                                showChromeRef.value = !showChromeRef.value
                                true
                            }
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

    // Reader chrome panels
    var showHighlightsPanel by remember { mutableStateOf(false) }
    var showLinkedNotePanel by remember { mutableStateOf(false) }

    if (showChromeRef.value) {
        ReaderChromeOverlay(
            onHighlightsClick = { showHighlightsPanel = true; showChromeRef.value = false },
            onNotesClick = { showLinkedNotePanel = true; showChromeRef.value = false },
            onDismiss = { showChromeRef.value = false }
        )
    }

    if (showHighlightsPanel) {
        HighlightsPanel(
            highlights = highlights,
            onHighlightClick = { h ->
                navigatorFragment?.let { frag ->
                    val locator = h.locatorJson.takeIf { it.isNotEmpty() }?.let {
                        try { org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(it)) }
                        catch (e: Exception) { null }
                    }
                    if (locator != null) {
                        coroutineScope.launch { frag.go(locator, animated = false) }
                    }
                }
                showHighlightsPanel = false
            },
            onDeleteHighlight = { h ->
                viewModel.deleteHighlight(h.id)
            },
            onDismiss = { showHighlightsPanel = false }
        )
    }

    if (showLinkedNotePanel) {
        LinkedNotePanel(
            viewModel = viewModel,
            onDismiss = { showLinkedNotePanel = false }
        )
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
    var showConceptLinkSheet by remember { mutableStateOf(false) }

    if (selectedHighlight != null) {
        AnnotationBottomSheet(
            highlight = selectedHighlight,
            onSave = { annotation, colour, tag ->
                viewModel.updateHighlight(
                    selectedHighlight.copy(
                        annotation = annotation.trim().ifEmpty { null },
                        colour = colour,
                        emotionalTag = tag
                    )
                )
                selectedHighlightId = null
            },
            onDelete = {
                viewModel.deleteHighlight(selectedHighlight.id)
                selectedHighlightId = null
            },
            onAddToConcept = { showConceptLinkSheet = true },
            onDismiss = { selectedHighlightId = null }
        )
    }

    if (showConceptLinkSheet && selectedHighlight != null) {
        val capturedHighlight = selectedHighlight
        ConceptLinkSheet(
            highlight = capturedHighlight,
            onSearchConcepts = { query -> viewModel.searchConcepts(query) },
            onCreateNew = { name ->
                viewModel.createConceptFromHighlight(
                    conceptName = name,
                    highlight = capturedHighlight,
                    onSuccess = { showConceptLinkSheet = false; selectedHighlightId = null },
                    onError = {}
                )
            },
            onLinkExisting = { concept ->
                viewModel.updateHighlight(capturedHighlight.copy(conceptLink = "[[${concept.name}]]"))
                showConceptLinkSheet = false
                selectedHighlightId = null
            },
            onDismiss = { showConceptLinkSheet = false }
        )
    }
}

@Composable
private fun ReaderChromeOverlay(
    onHighlightsClick: () -> Unit,
    onNotesClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
    ) {
        // Bottom chrome strip
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onHighlightsClick,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.reader_chrome_highlights)) }
            OutlinedButton(
                onClick = onNotesClick,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.reader_chrome_notes)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighlightsPanel(
    highlights: List<Highlight>,
    onHighlightClick: (Highlight) -> Unit,
    onDeleteHighlight: (Highlight) -> Unit,
    onDismiss: () -> Unit
) {
    var filterColour by remember { mutableStateOf<HighlightColour?>(null) }
    var filterEmotion by remember { mutableStateOf<EmotionalTag?>(null) }
    var annotationOnly by remember { mutableStateOf(false) }

    val filtered = highlights
        .filter { filterColour == null || it.colour == filterColour }
        .filter { filterEmotion == null || it.emotionalTag == filterEmotion }
        .filter { !annotationOnly || !it.annotation.isNullOrEmpty() }
        .sortedBy { it.createdAt }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_chrome_highlights),
                style = MaterialTheme.typography.titleSmall
            )
            // Colour filter row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "All" chip
                if (filterColour == null) {
                    Button(onClick = { filterColour = null }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.registry_filter_all), style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    OutlinedButton(onClick = { filterColour = null }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.registry_filter_all), style = MaterialTheme.typography.labelSmall)
                    }
                }
                HighlightColour.values().forEach { colour ->
                    ColourButton(
                        colour = colour,
                        selected = filterColour == colour,
                        label = colourLabel(colour),
                        onSelect = { filterColour = if (filterColour == colour) null else colour },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            // Annotation-only toggle
            OutlinedButton(
                onClick = { annotationOnly = !annotationOnly },
                modifier = Modifier.fillMaxWidth(),
                border = if (annotationOnly) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Text(stringResource(R.string.highlights_filter_annotation_only), style = MaterialTheme.typography.labelSmall)
            }
            // Highlights list
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.highlights_panel_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filtered, key = { it.id }) { h ->
                        HighlightPanelItem(
                            highlight = h,
                            onClick = onHighlightClick,
                            onDelete = onDeleteHighlight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun colourLabel(colour: HighlightColour) = when (colour) {
    HighlightColour.YELLOW -> stringResource(R.string.highlight_colour_yellow)
    HighlightColour.GREEN -> stringResource(R.string.highlight_colour_green)
    HighlightColour.BLUE -> stringResource(R.string.highlight_colour_blue)
    HighlightColour.PINK -> stringResource(R.string.highlight_colour_pink)
}

@Composable
private fun HighlightPanelItem(
    highlight: Highlight,
    onClick: (Highlight) -> Unit,
    onDelete: (Highlight) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Tappable text content — navigates to highlight in reader
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(highlight) }
        ) {
            Text(
                text = "\"${highlight.text.take(100)}${if (highlight.text.length > 100) "…" else ""}\"",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = highlight.colour.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                highlight.emotionalTag?.let { tag ->
                    Text(
                        text = tag.name.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            highlight.annotation?.takeIf { it.isNotEmpty() }?.let { ann ->
                Text(
                    text = ann.take(80),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        // Visible delete button — always shown, no gesture required (e-ink safe)
        OutlinedButton(
            onClick = { onDelete(highlight) },
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.highlight_annotation_delete),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkedNotePanel(
    viewModel: ReaderViewModel,
    onDismiss: () -> Unit
) {
    val content by produceState<String?>(null) {
        value = withContext(Dispatchers.IO) { viewModel.getLinkedNoteContent() }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.linked_note_title),
                style = MaterialTheme.typography.titleSmall
            )
            when (content) {
                null -> CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.Black)
                "" -> Text(
                    text = stringResource(R.string.highlights_panel_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Text(
                    text = content!!.take(2000),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnotationBottomSheet(
    highlight: Highlight,
    onSave: (annotation: String, colour: HighlightColour, emotionalTag: EmotionalTag?) -> Unit,
    onDelete: () -> Unit,
    onAddToConcept: () -> Unit,
    onDismiss: () -> Unit
) {
    var annotationText by remember(highlight.id) { mutableStateOf(highlight.annotation ?: "") }
    var selectedColour by remember(highlight.id) { mutableStateOf(highlight.colour) }
    var selectedTag by remember(highlight.id) { mutableStateOf(highlight.emotionalTag) }
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

            // Emotional resonance tags — one row, tapping active tag deselects it
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EmotionTagButton(
                    tag = EmotionalTag.MOVED,
                    selected = selectedTag == EmotionalTag.MOVED,
                    label = stringResource(R.string.emotion_moved),
                    onToggle = { selectedTag = if (selectedTag == EmotionalTag.MOVED) null else EmotionalTag.MOVED },
                    modifier = Modifier.weight(1f)
                )
                EmotionTagButton(
                    tag = EmotionalTag.TROUBLED,
                    selected = selectedTag == EmotionalTag.TROUBLED,
                    label = stringResource(R.string.emotion_troubled),
                    onToggle = { selectedTag = if (selectedTag == EmotionalTag.TROUBLED) null else EmotionalTag.TROUBLED },
                    modifier = Modifier.weight(1f)
                )
                EmotionTagButton(
                    tag = EmotionalTag.SURPRISED,
                    selected = selectedTag == EmotionalTag.SURPRISED,
                    label = stringResource(R.string.emotion_surprised),
                    onToggle = { selectedTag = if (selectedTag == EmotionalTag.SURPRISED) null else EmotionalTag.SURPRISED },
                    modifier = Modifier.weight(1f)
                )
                EmotionTagButton(
                    tag = EmotionalTag.DELIGHTED,
                    selected = selectedTag == EmotionalTag.DELIGHTED,
                    label = stringResource(R.string.emotion_delighted),
                    onToggle = { selectedTag = if (selectedTag == EmotionalTag.DELIGHTED) null else EmotionalTag.DELIGHTED },
                    modifier = Modifier.weight(1f)
                )
                EmotionTagButton(
                    tag = EmotionalTag.RESISTANT,
                    selected = selectedTag == EmotionalTag.RESISTANT,
                    label = stringResource(R.string.emotion_resistant),
                    onToggle = { selectedTag = if (selectedTag == EmotionalTag.RESISTANT) null else EmotionalTag.RESISTANT },
                    modifier = Modifier.weight(1f)
                )
            }

            // Concept link button — "Add to concept" or "View concept [[Name]]"
            if (highlight.conceptLink.isNullOrEmpty()) {
                OutlinedButton(
                    onClick = onAddToConcept,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.concept_add_to), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                OutlinedButton(
                    onClick = onAddToConcept,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${stringResource(R.string.concept_view)} ${highlight.conceptLink}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Save
            Button(
                onClick = {
                    val annotation = annotationText
                    val colour = selectedColour
                    val tag = selectedTag
                    scope.launch {
                        sheetState.hide()
                        onSave(annotation, colour, tag)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConceptLinkSheet(
    highlight: Highlight,
    onSearchConcepts: suspend (String) -> List<ConceptNote>,
    onCreateNew: (String) -> Unit,
    onLinkExisting: (ConceptNote) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<ConceptNote>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(searchQuery) {
        searchResults = onSearchConcepts(searchQuery)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.concept_link_title),
                style = MaterialTheme.typography.titleSmall
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.concept_name_hint)) },
                singleLine = true
            )
            // "Create new" option — always shown when query is non-empty
            if (searchQuery.trim().isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val name = searchQuery.trim()
                        scope.launch { sheetState.hide(); onCreateNew(name) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.concept_create_new, searchQuery.trim()))
                }
            }
            // Existing concept matches
            if (searchResults.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(searchResults) { concept ->
                        OutlinedButton(
                            onClick = {
                                scope.launch { sheetState.hide(); onLinkExisting(concept) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${concept.name} (${concept.status.name.lowercase()})")
                        }
                    }
                }
            } else if (searchQuery.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.concept_no_matches),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmotionTagButton(
    tag: EmotionalTag,
    selected: Boolean,
    label: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onToggle, modifier = modifier) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        OutlinedButton(onClick = onToggle, modifier = modifier) {
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

// --- Pen and highlighter UI composables ---

@Composable
private fun colourDot(colour: HighlightColour): String = when (colour) {
    HighlightColour.YELLOW -> "○"
    HighlightColour.GREEN -> "◔"
    HighlightColour.BLUE -> "◑"
    HighlightColour.PINK -> "◕"
}

@Composable
private fun HighlighterColourSelector(
    currentColour: HighlightColour,
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
            text = stringResource(R.string.annotation_highlighter_button),
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColourButton(
                colour = HighlightColour.YELLOW,
                selected = currentColour == HighlightColour.YELLOW,
                label = stringResource(R.string.highlight_colour_yellow),
                onSelect = onColourSelected,
                modifier = Modifier.weight(1f)
            )
            ColourButton(
                colour = HighlightColour.GREEN,
                selected = currentColour == HighlightColour.GREEN,
                label = stringResource(R.string.highlight_colour_green),
                onSelect = onColourSelected,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColourButton(
                colour = HighlightColour.BLUE,
                selected = currentColour == HighlightColour.BLUE,
                label = stringResource(R.string.highlight_colour_blue),
                onSelect = onColourSelected,
                modifier = Modifier.weight(1f)
            )
            ColourButton(
                colour = HighlightColour.PINK,
                selected = currentColour == HighlightColour.PINK,
                label = stringResource(R.string.highlight_colour_pink),
                onSelect = onColourSelected,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text(stringResource(R.string.highlight_delete))
        }
    }
}

@Composable
private fun PenSettingsSheet(
    viewModel: ReaderViewModel,
    onDismiss: () -> Unit
) {
    val strokeWidth = settingsValue(viewModel, AppSettings.PEN_STROKE_WIDTH)
    val pressureSensitivity = settingsStringValue(viewModel, AppSettings.PEN_PRESSURE_SENSITIVITY)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.pen_settings_title),
            style = MaterialTheme.typography.titleSmall
        )

        // Stroke width selector
        Text(
            text = stringResource(R.string.pen_stroke_width_label),
            style = MaterialTheme.typography.labelMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 2, 4, 6, 8).forEach { width ->
                if (strokeWidth == width) {
                    Button(
                        onClick = { viewModel.setPenStrokeWidth(width) },
                        modifier = Modifier.weight(1f)
                    ) { Text("$width", style = MaterialTheme.typography.labelSmall) }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.setPenStrokeWidth(width) },
                        modifier = Modifier.weight(1f)
                    ) { Text("$width", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }

        // Pressure sensitivity selector
        Text(
            text = stringResource(R.string.pen_pressure_label),
            style = MaterialTheme.typography.labelMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "light" to stringResource(R.string.pen_pressure_light),
                "normal" to stringResource(R.string.pen_pressure_normal),
                "firm" to stringResource(R.string.pen_pressure_firm)
            ).forEach { (key, label) ->
                if (pressureSensitivity == key) {
                    Button(
                        onClick = { viewModel.setPressureSensitivity(key) },
                        modifier = Modifier.weight(1f)
                    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.setPressureSensitivity(key) },
                        modifier = Modifier.weight(1f)
                    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.highlight_annotation_save)) }
    }
}

// Helper: read an Int setting from ViewModel's SettingsRegistry (accessed via saved state).
// This avoids exposing the registry directly to the UI.
@Composable
private fun settingsValue(viewModel: ReaderViewModel, setting: com.marginalia.settings.Setting<Int>): Int =
    remember(setting.key) { viewModel.getIntSetting(setting) }

@Composable
private fun settingsStringValue(viewModel: ReaderViewModel, setting: com.marginalia.settings.Setting<String>): String {
    var value by remember { mutableStateOf(viewModel.getStringSetting(setting)) }
    // Observe changes via a produced state driven by ViewModel updates
    LaunchedEffect(Unit) {
        // Re-read on composition — settings changes are rare and don't need continuous observation
        value = viewModel.getStringSetting(setting)
    }
    return value
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
