package com.nibble.app.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The brand mark (§4). Phosphor's cookie isn't in the Material set, so we draw it:
 * a filled disc with chip holes punched out via an offscreen layer, so it reads on
 * any ground in a single tint.
 */
@Composable
fun CookieIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = tint, radius = r, center = c)
        val chip = r * 0.14f
        val chips = listOf(
            Offset(-0.32f, -0.30f),
            Offset(0.30f, -0.12f),
            Offset(-0.10f, 0.28f),
            Offset(0.34f, 0.34f),
            Offset(0.02f, -0.02f),
        )
        for (p in chips) {
            drawCircle(
                color = Color.Black,
                radius = chip,
                center = c + Offset(p.x * r, p.y * r),
                blendMode = BlendMode.Clear,
            )
        }
    }
}
