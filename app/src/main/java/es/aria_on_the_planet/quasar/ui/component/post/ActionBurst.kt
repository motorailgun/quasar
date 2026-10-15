package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** バースト全体の長さ (ms)。 */
internal const val ActionBurstDurationMillis = 700

/** 放射状に飛ぶ方向の数。各方向に大小 2 つの粒を飛ばす。 */
private const val BurstDirections = 7

/** 輪が広がりきるまでの割合 (全体を 1 として)。粒はその少し前から飛び始める。 */
private const val RingEnd = 0.35f
private const val ParticleStart = 0.2f

private val RingMaxRadius = 14.dp
private val ParticleStartRadius = 8.dp
private val ParticleEndRadius = 22.dp
private val ParticleSize = 2.5.dp

/**
 * お気に入り等をオンにしたときの、アイコンの後ろから粒が散る演出 (X の like を参考)。
 * 輪が広がりながら細くなって消え、続いて粒が放射状に飛んで縮みながら消える。
 *
 * 描画は要素の範囲外にはみ出すので、clip より前 (外側) に付ける。
 *
 * @param progress 0〜1。0 と 1 では何も描かない。
 * @param centerOf 要素のサイズから、要素の左上から見たアイコンの中心を求める。
 */
internal fun Modifier.actionBurst(
    progress: () -> Float,
    centerOf: (Size) -> Offset,
    colors: List<Color>,
): Modifier = drawBehind {
    val t = progress()
    if (t <= 0f || t >= 1f) return@drawBehind
    val center = centerOf(size)
    drawRing(t, center, colors.first())
    drawParticles(t, center, colors)
}

private fun DrawScope.drawRing(t: Float, center: Offset, color: Color) {
    if (t >= RingEnd) return
    val p = FastOutSlowInEasing.transform(t / RingEnd)
    val radius = RingMaxRadius.toPx() * p
    // 広がるほど細くなって消える
    val stroke = RingMaxRadius.toPx() * (1f - p)
    if (stroke <= 0f) return
    drawCircle(color, radius = radius, center = center, style = Stroke(width = stroke), alpha = 1f - p * 0.3f)
}

private fun DrawScope.drawParticles(t: Float, center: Offset, colors: List<Color>) {
    if (t < ParticleStart) return
    val p = (t - ParticleStart) / (1f - ParticleStart)
    val travel = LinearOutSlowInEasing.transform(p)
    val distance = lerp(ParticleStartRadius, ParticleEndRadius, travel).toPx()
    // 後半で縮みながら消える
    val shrink = 1f - FastOutSlowInEasing.transform(((p - 0.4f) / 0.6f).coerceIn(0f, 1f))
    val size = ParticleSize.toPx() * shrink
    if (size <= 0f) return
    for (i in 0 until BurstDirections) {
        val angle = 2 * PI * i / BurstDirections - PI / 2
        // 大きい粒と、少し角度をずらして遅れる小さい粒の 2 つ
        drawParticle(center, angle, distance, size, colors[i % colors.size])
        drawParticle(center, angle + 0.3, distance * 0.8f, size * 0.7f, colors[(i + 1) % colors.size])
    }
}

private fun DrawScope.drawParticle(center: Offset, angle: Double, distance: Float, radius: Float, color: Color) {
    val offset = Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat())
    drawCircle(color, radius = radius, center = center + offset)
}

private fun lerp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction
