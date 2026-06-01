package com.marginalia.android.ui.reader

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.hilt.navigation.compose.hiltViewModel
import com.marginalia.android.R
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication

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
                onPageTurn = viewModel::onPageTurn,
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
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.Black)
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
        modifier = Modifier.fillMaxSize().background(Color.White),
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

@Composable
private fun ReadyReader(
    publication: Publication,
    onPageTurn: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    val fragmentManager = activity.supportFragmentManager
    val containerId = remember { View.generateViewId() }
    var navigatorFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    // Full-screen FragmentContainerView for the Readium WebView
    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply { id = containerId }
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(publication) {
        val navigatorFactory = EpubNavigatorFactory(publication)
        val fragmentFactory = navigatorFactory.createFragmentFactory(initialLocator = null)

        // Install the factory so the fragment manager can instantiate the navigator
        fragmentManager.fragmentFactory = fragmentFactory
        fragmentManager.beginTransaction()
            .replace(containerId, EpubNavigatorFragment::class.java, null)
            .commitNow()

        navigatorFragment = fragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment

        onDispose {
            navigatorFragment = null
            if (!fragmentManager.isStateSaved) {
                fragmentManager.findFragmentById(containerId)?.let {
                    fragmentManager.beginTransaction().remove(it).commitNow()
                }
            }
        }
    }

    // Gesture overlay for tap zones and swipe-to-exit
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
                            onPageTurn()
                        }
                        offset.x > widthPx * 0.70f -> {
                            fragment.goForward(animated = false)
                            onPageTurn()
                        }
                        // Centre zone (40%): reader chrome — deferred to navigation session
                    }
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> swipeStartY = offset.y },
                    onDragEnd = {},
                    onDragCancel = {}
                ) { _, dragAmount ->
                    // Swipe down from the top 15% of screen exits the reader
                    if (swipeStartY < size.height * 0.15f && dragAmount > 40f) {
                        onExit()
                    }
                }
            }
    )
}
