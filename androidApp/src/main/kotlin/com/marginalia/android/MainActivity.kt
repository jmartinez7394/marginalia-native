package com.marginalia.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.marginalia.android.ui.library.LibraryScreen
import com.marginalia.android.ui.reader.ReaderScreen
import com.marginalia.device.DeviceCapabilities
import com.marginalia.device.DisplayRefreshManager
import com.marginalia.device.DisplayType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var displayRefreshManager: DisplayRefreshManager
    @Inject lateinit var deviceCapabilities: DeviceCapabilities

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = Color.White) {
                    var openBookId by remember { mutableStateOf<String?>(null) }
                    val isEinkDevice = deviceCapabilities.displayType == DisplayType.EINK

                    AnimatedContent(
                        targetState = openBookId,
                        transitionSpec = {
                            if (isEinkDevice) {
                                ContentTransform(
                                    targetContentEnter = EnterTransition.None,
                                    initialContentExit = ExitTransition.None
                                )
                            } else {
                                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                            }
                        },
                        label = "screen_transition"
                    ) { bookId ->
                        if (bookId != null) {
                            ReaderScreen(
                                bookId = bookId,
                                onExit = {
                                    displayRefreshManager.refreshFull()
                                    openBookId = null
                                }
                            )
                        } else {
                            LibraryScreen(
                                territoryId = "library",
                                onBookClick = { id ->
                                    displayRefreshManager.refreshFull()
                                    openBookId = id
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
