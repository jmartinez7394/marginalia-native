package com.marginalia.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarginaliaNativePlaceholder()
        }
    }
}

@Composable
fun MarginaliaNativePlaceholder() {
    Text("Marginalia Native — Session 1 scaffold complete")
}
