package es.aria_on_the_planet.quasar.ui.component.modifier

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 中身 (文字・アイコン) の上を [color] の帯が文の流れの向き (LTR では左から右) に通り過ぎる。処理中の表示。帯は中身の形で
 * 切り抜く (中身の無い所には描かない)。
 *
 * @param progress 帯の位置 0〜1 ([rememberShimmerProgress])。描画フェーズでのみ読まれる。
 */
fun Modifier.shimmer(
    color: Color,
    progress: () -> Float,
    bandWidth: Dp = 48.dp,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val band = bandWidth.toPx()
        // 帯が中身の外 (前の端の手前) から入り、後ろの端の外へ抜ける
        val travelled = -band + (size.width + 2 * band) * progress()
        val center = if (layoutDirection == LayoutDirection.Ltr) travelled else size.width - travelled
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to color,
                1f to Color.Transparent,
                startX = center - band / 2,
                endX = center + band / 2,
            ),
            blendMode = BlendMode.SrcAtop,
        )
    }

/** [shimmer] の帯の位置。0 → 1 を繰り返す。 */
@Composable
fun rememberShimmerProgress(durationMillis: Int = 1400): State<Float> =
    rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    )
