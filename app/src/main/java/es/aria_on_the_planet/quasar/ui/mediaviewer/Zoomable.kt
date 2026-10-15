package es.aria_on_the_planet.quasar.ui.mediaviewer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ピンチでの拡大縮小の状態。[transform] は中身 (画面に Fit で収めた画像) の拡大率と中心からの移動量。
 *
 * @param contentAspectRatio 中身の幅 / 高さ。移動できる範囲 (画像の端が画面の内側に入らない範囲) を決めるのに使う。
 *   分からない間は画面全体を中身とみなす。
 */
@Stable
class ZoomState(contentAspectRatio: Float?) {
    var transform by mutableStateOf(ZoomTransform.Identity)
        private set

    var contentAspectRatio by mutableStateOf(contentAspectRatio)

    internal var containerSize = Size.Zero

    /** 等倍より大きく拡大している。拡大中はページ切り替え・上下スワイプで閉じる操作を止め、1 本指で画像を動かす。 */
    val isZoomed: Boolean by derivedStateOf { transform.scale > 1f }

    private var animationJob: Job? = null

    private val contentSize: Size
        get() = contentAspectRatio?.let { fitSize(it, containerSize) } ?: containerSize

    internal fun onGesture(centroid: Offset, pan: Offset, zoomChange: Float) {
        transform = transform.applyGesture(centroid, pan, zoomChange, containerSize, contentSize)
    }

    /**
     * ダブルタップ。等倍なら [centroid] (領域内の座標) を中心に [DoubleTapZoomScale] まで拡大し、拡大中なら等倍に戻す。
     * アニメーションの途中も、タップした点が同じ位置に留まるように拡大率から移動量を求める。
     */
    fun toggleZoom(centroid: Offset, scope: CoroutineScope) {
        val start = transform
        val target = if (start.scale > 1f) 1f else DoubleTapZoomScale
        animationJob?.cancel()
        animationJob = scope.launch {
            animate(start.scale, target, animationSpec = tween(DoubleTapZoomMillis, easing = FastOutSlowInEasing)) { scale, _ ->
                transform = start.applyGesture(centroid, Offset.Zero, scale / start.scale, containerSize, contentSize)
            }
        }
    }

    /** 指が触れたらダブルタップのアニメーションを止め、指の操作を優先する。 */
    internal fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    fun reset() {
        stopAnimation()
        transform = ZoomTransform.Identity
    }
}

@Composable
fun rememberZoomState(key: Any?, contentAspectRatio: Float?): ZoomState =
    remember(key) { ZoomState(contentAspectRatio) }

/**
 * ピンチで拡大縮小、拡大中は 1 本指で移動する。変形は graphicsLayer で当てる。ダブルタップは呼び出し側の
 * タップ検出から [ZoomState.toggleZoom] を呼ぶ (1 回タップと同じ検出器で区別するため)。
 *
 * 2 本指、または拡大中の 1 本指の動きだけを受け取って消費する。拡大していない時の 1 本指は消費しないので、
 * 親の HorizontalPager (ページ切り替え) や上下スワイプで閉じる操作にそのまま渡る。
 * タップ (detectTapGestures) は指が動いて消費された時点で取り消されるので、ピンチがタップにはならない。
 */
fun Modifier.zoomable(state: ZoomState): Modifier = this
    .onSizeChanged { state.containerSize = it.toSize() }
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            state.stopAnimation()
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2 || state.isZoomed) {
                    val zoomChange = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (zoomChange != 1f || pan != Offset.Zero) {
                        state.onGesture(event.calculateCentroid(useCurrent = true), pan, zoomChange)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .graphicsLayer {
        val t = state.transform
        scaleX = t.scale
        scaleY = t.scale
        translationX = t.offset.x
        translationY = t.offset.y
    }

/** 中心基準の拡大率 [scale] と移動量 [offset] (px)。 */
data class ZoomTransform(val scale: Float, val offset: Offset) {
    /**
     * ピンチ 1 回分の変化を当てる。[centroid] (領域内の座標) の下にある点が指に付いてくるように拡大し、[pan] だけ動かす。
     * 拡大率は 1〜[maxScale]、移動は拡大した中身 ([contentSize] × 拡大率) の端が領域の内側に入らない範囲に収める。
     */
    fun applyGesture(
        centroid: Offset,
        pan: Offset,
        zoomChange: Float,
        containerSize: Size,
        contentSize: Size,
        maxScale: Float = MaxZoomScale,
    ): ZoomTransform {
        val newScale = (scale * zoomChange).coerceIn(1f, maxScale)
        val actualZoom = newScale / scale
        // 中心からの位置で考える (graphicsLayer の変形の基準が中心のため)
        val c = centroid - Offset(containerSize.width / 2f, containerSize.height / 2f)
        val newOffset = (offset - c) * actualZoom + c + pan
        return ZoomTransform(newScale, clampZoomOffset(newOffset, newScale, containerSize, contentSize))
    }

    companion object {
        val Identity = ZoomTransform(1f, Offset.Zero)
    }
}

/** 拡大した中身の端が領域の内側に入らないよう移動量を制限する。中身が領域より小さい向きは中央に固定。 */
fun clampZoomOffset(offset: Offset, scale: Float, containerSize: Size, contentSize: Size): Offset {
    val maxX = ((contentSize.width * scale - containerSize.width) / 2f).coerceAtLeast(0f)
    val maxY = ((contentSize.height * scale - containerSize.height) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/** 最大の拡大率。 */
const val MaxZoomScale = 5f

/** ダブルタップで拡大するときの拡大率。 */
const val DoubleTapZoomScale = 2f

private const val DoubleTapZoomMillis = 250
