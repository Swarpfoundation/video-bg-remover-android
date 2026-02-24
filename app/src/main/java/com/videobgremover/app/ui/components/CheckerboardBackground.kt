package com.videobgremover.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.videobgremover.app.ui.theme.TransparentCheckerDark
import com.videobgremover.app.ui.theme.TransparentCheckerLight

/**
 * A checkerboard pattern background to visualize transparency.
 */
@Composable
fun CheckerboardBackground(
    modifier: Modifier = Modifier,
    squareSize: Int = 16
) {
    val squareSizeDp = with(LocalDensity.current) { squareSize.dp.toPx() }
    val lightColor = TransparentCheckerLight
    val darkColor = TransparentCheckerDark

    Canvas(modifier = modifier.fillMaxSize()) {
        val rows = (size.height / squareSizeDp).toInt() + 1
        val cols = (size.width / squareSizeDp).toInt() + 1

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val color = if ((row + col) % 2 == 0) lightColor else darkColor
                drawRect(
                    color = color,
                    topLeft = Offset(
                        x = col * squareSizeDp,
                        y = row * squareSizeDp
                    ),
                    size = Size(squareSizeDp, squareSizeDp)
                )
            }
        }
    }
}
