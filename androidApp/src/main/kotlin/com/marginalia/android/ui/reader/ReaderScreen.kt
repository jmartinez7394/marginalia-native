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

    var showColourPicker by remember { mutableStateOf(false) }
    var pendingSelectionCfi by remember { mutableStateOf("") }
    var pendingSelectionHref by remember { mutableStateOf("") }
    var pendingSelectionLocatorJson by remember { mutableStateOf("") }
    var pendingSelectionText by remember { mutableStateOf("") }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // Full-screen FragmentContainerView — no Compose overlay intercepts touch events,
    // allowing the WebView to receive long-press for native text selection.
    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply { id = containerId }
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
            val fragmentFactory = navigatorFactory.createFragmentFactory(initialLocator = null)
            fragmentManager.fragmentFactory = fragmentFactory
            fragmentManager.beginTransaction()
                .replace(containerId, EpubNavigatorFragment::class.java, null)
                .commitNow()
            val fragment = fragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
            navigatorFragment = fragment
            Log.d(TAG, "EpubNavigatorFragment added: $fragment")

            if (fragment != null) {
                // Use Readium's InputListener for tap-based page navigation.
                // This fires after the WebView processes the tap, so long-press
                // (which the WebView uses for text selection) is never intercepted.
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
                    override fun onDrag(event: DragEvent): Boolean = false
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

    // Apply highlight decorations whenever the list changes.
    LaunchedEffect(navigatorFragment, highlights) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        val decorable = fragment as? DecorableNavigator ?: return@LaunchedEffect
        val supportsHighlight = decorable.supportsDecorationStyle(Decoration.Style.Highlight::class)
        val supportsUnderline = decorable.supportsDecorationStyle(Decoration.Style.Underline::class)
        Log.d(TAG, "decoration support: Highlight=$supportsHighlight Underline=$supportsUnderline")
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

    if (showColourPicker) {
        ModalBottomSheet(
            onDismissRequest = { showColourPicker = false },
            sheetState = bottomSheetState
        ) {
            HighlightColourPicker(
                onColourSelected = { colour ->
                    viewModel.createHighlight(pendingSelectionCfi, pendingSelectionHref, pendingSelectionLocatorJson, pendingSelectionText, colour)
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
    // Alpha ~100/255 gives visible-but-not-oppressive overlay on e-ink white background.
    val tint = when (colour) {
        HighlightColour.YELLOW -> Color.argb(100, 200, 200, 200)
        HighlightColour.GREEN -> Color.argb(120, 160, 160, 160)
        HighlightColour.BLUE -> Color.argb(140, 120, 120, 120)
        HighlightColour.PINK -> Color.argb(160, 80, 80, 80)
    }
    // Prefer the complete stored locator (preserves CFI, text context, progression).
    // Fall back to rebuilding from href+cfi for highlights created before locatorJson was added.
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
    android.util.Log.d("ReaderScreen", "toDecoration: href=${locator.href} cfi=${locator.locations.fragments.firstOrNull()} text='${text.take(20)}'")
    return Decoration(
        id = id,
        locator = locator,
        style = Decoration.Style.Highlight(tint = tint)
    )
}
