package com.marginalia.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.marginalia.android.ui.library.LibraryScreen
import com.marginalia.android.ui.reader.ReaderScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = Color.White) {
                    var openBookId by remember { mutableStateOf<String?>(null) }

                    val currentBookId = openBookId
                    if (currentBookId != null) {
                        ReaderScreen(
                            bookId = currentBookId,
                            onExit = { openBookId = null }
                        )
                    } else {
                        LibraryScreen(
                            territoryId = "library",
                            onBookClick = { bookId -> openBookId = bookId }
                        )
                    }
                }
            }
        }
    }
}
