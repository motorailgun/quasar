package es.aria_on_the_planet.quasar.ui.component.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * 下端をフェードアウトさせ、内容が続いていることを示す。
 *
 * @param strength フェード強度 0〜1。描画フェーズでのみ読まれる。
 */
fun Modifier.bottomFadingEdge(
    height: Dp,
    strength: () -> Float,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val amount = strength()
        if (amount <= 0f) return@drawWithContent
        val edge = height.toPx().coerceAtMost(size.height / 2)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black,
                1f to Color.Black.copy(alpha = 1f - amount),
                startY = size.height - edge,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - edge),
            size = Size(size.width, edge),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * 左右の端をフェードアウトさせ、その方向にまだスクロールできることを示す。
 * スクロール系 Modifier より前 (外側) に付けること。
 *
 * @param start 開始側 (LTR では左) のフェード強度 0〜1。描画フェーズでのみ読まれる。
 * @param end 終了側のフェード強度 0〜1。
 */
fun Modifier.horizontalFadingEdges(
    width: Dp,
    start: () -> Float,
    end: () -> Float,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = width.toPx().coerceAtMost(size.width / 2)
        val ltr = layoutDirection == LayoutDirection.Ltr
        val left = if (ltr) start() else end()
        val right = if (ltr) end() else start()
        if (left > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 1f - left),
                    1f to Color.Black,
                    startX = 0f,
                    endX = edge,
                ),
                size = Size(edge, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (right > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    1f to Color.Black.copy(alpha = 1f - right),
                    startX = size.width - edge,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - edge, 0f),
                size = Size(edge, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }
