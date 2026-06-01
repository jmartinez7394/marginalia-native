package com.marginalia.android.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.marginalia.device.RefreshMode

// Debug-only overlay — visible in debug builds only.
// Shows last e-ink refresh mode as a coloured circle (top-right corner):
//   Red = GC16, Blue = REGAL, Green = A2, Yellow = DU
@Composable
fun RefreshDebugOverlay(
    lastMode: RefreshMode?,
    modifier: Modifier = Modifier
) {
    if (lastMode == null) return
    val color = when (lastMode) {
        RefreshMode.GC16 -> Color.Red
        RefreshMode.REGAL -> Color.Blue
        RefreshMode.A2 -> Color.Green
        RefreshMode.DU -> Color.Yellow
    }
    Box(
        modifier = modifier
            .padding(8.dp)
            .size(16.dp)
            .background(color, CircleShape)
    )
}
