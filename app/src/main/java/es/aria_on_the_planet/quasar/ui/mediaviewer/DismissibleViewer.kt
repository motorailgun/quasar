package es.aria_on_the_planet.quasar.ui.mediaviewer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import es.aria_on_the_planet.quasar.ui.component.player.MediaOverlayColors
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/**
 * メディアビューアの出入りの状態。開く (フェードイン)・閉じる (一覧の元の位置へ縮んで戻る) のアニメーションと、
 * 上下スワイプ / 予測型「戻る」の途中の見た目を持つ。`NavDisplay` の遷移は付けず、すべてここで描く。
 */
@Stable
class DismissibleViewerState internal constructor(
    private val target: () -> DismissTarget?,
    private val onDismissed: () -> Unit,
) {
    /** 指が動いた距離 (px)。表示上の移動量はここからゴムのように縮めて求める ([rubberBand])。 */
    internal var dragOffset by mutableFloatStateOf(0f)

    /** 予測型「戻る」のジェスチャの進み具合 (0〜1)。 */
    internal var backProgress by mutableFloatStateOf(0f)

    /** 開くアニメーションの進み具合 (0〜1)。最初のフレームから透明にしておく (一瞬見えてしまわないよう)。 */
    internal var enterFraction by mutableFloatStateOf(0f)

    /** 閉じるアニメーションの進み具合 (0〜1)。`null` なら閉じていない。 */
    internal var exitFraction by mutableStateOf<Float?>(null)

    internal var containerBounds = Rect.Zero

    /** 閉じ始めた時点の見た目と、閉じる先の変形。 */
    internal var exitFrom = LayerTransform(1f, 0f, 0f)
    internal var exitTo = LayerTransform(1f, 0f, 0f)
    internal var exitFromProgress = 0f
    internal var exitToTarget = false

    /** ドラッグ中・戻り中・「戻る」の途中・閉じる途中。上部バーや再生操作を隠すのに使う。 */
    val isActive: Boolean by derivedStateOf { dragOffset != 0f || backProgress != 0f || exitFraction != null }

    internal suspend fun enter() {
        enterFraction = 0f
        animate(0f, 1f, animationSpec = tween(EnterMillis, easing = LinearOutSlowInEasing)) { value, _ ->
            enterFraction = value
        }
    }

    /**
     * 閉じる。今の見た目 (ドラッグ・「戻る」の途中) から、一覧の元の位置 ([target]) へ縮みながら移動して消える。
     * 戻り先が分からなければその場で縮みながら消える。終わったら [onDismissed]。
     */
    suspend fun dismiss() {
        if (exitFraction != null) return
        val maxOffset = containerBounds.height * MaxOffsetFraction
        val offset = rubberBand(dragOffset, maxOffset)
        val progress = maxOf(dismissProgress(offset, maxOffset), backProgress)
        exitFrom = LayerTransform(scale = dragScale(progress), translationX = 0f, translationY = offset)
        exitFromProgress = progress
        val toTarget = target()?.let { exitTransformTo(it, containerBounds) }
        exitToTarget = toTarget != null
        exitTo = toTarget ?: exitFrom.copy(scale = exitFrom.scale * 0.6f)
        exitFraction = 0f
        animate(0f, 1f, animationSpec = tween(ExitMillis, easing = FastOutSlowInEasing)) { value, _ ->
            exitFraction = value
        }
        onDismissed()
    }

    /** 背景の黒の濃さ。 */
    internal fun backgroundAlpha(): Float {
        val exit = exitFraction
        val alpha = if (exit == null) {
            1f - BackgroundFadeOnDrag * currentProgress()
        } else {
            (1f - BackgroundFadeOnDrag * exitFromProgress) * (1f - exit)
        }
        return (alpha * enterFraction).coerceIn(0f, 1f)
    }

    /** 中身の変形と濃さを graphicsLayer に当てる (描画フェーズで読むので再コンポーズしない)。 */
    internal fun GraphicsLayerScope.applyContentTransform() {
        val exit = exitFraction
        val transform: LayerTransform
        if (exit == null) {
            val maxOffset = containerBounds.height * MaxOffsetFraction
            transform = LayerTransform(dragScale(currentProgress()), 0f, rubberBand(dragOffset, maxOffset))
            alpha = enterFraction
        } else {
            transform = lerp(exitFrom, exitTo, exit)
            alpha = if (exitToTarget) {
                // 戻り先に着く直前に消し、下のサムネイルに引き継ぐ
                1f - ((exit - ExitFadeStart) / (1f - ExitFadeStart)).coerceIn(0f, 1f)
            } else {
                1f - exit
            }
        }
        translationX = transform.translationX
        translationY = transform.translationY
        scaleX = transform.scale
        scaleY = transform.scale
    }

    private fun currentProgress(): Float {
        val maxOffset = containerBounds.height * MaxOffsetFraction
        return maxOf(dismissProgress(rubberBand(dragOffset, maxOffset), maxOffset), backProgress)
    }
}

/**
 * @param target 閉じるときに縮んで戻る先。閉じ始めた時点で呼ぶ。
 * @param onDismissed 閉じるアニメーションが終わった後 (画面をバックスタックから外す)。
 */
@Composable
fun rememberDismissibleViewerState(
    target: () -> DismissTarget?,
    onDismissed: () -> Unit,
): DismissibleViewerState {
    val currentTarget by rememberUpdatedState(target)
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    return remember { DismissibleViewerState({ currentTarget() }, { currentOnDismissed() }) }
}

/** 閉じるときに縮んで戻る先。[bounds] はウィンドウ上の位置 (px)、[aspectRatio] は表示中のメディアの幅 / 高さ。 */
data class DismissTarget(val bounds: Rect, val aspectRatio: Float)

/**
 * メディアビューアの外枠。背景の黒を自前で描き (元の画面の上に重ねているので、黒が薄くなると透けて見える)、出入りを担う。
 *
 * * 開く: 最初の表示でフェードイン ([EnterMillis])。回転等の作り直しでは繰り返さない。
 * * 上下スワイプ: 中身は指に付いて動くが、ゴムのように引っかかり、どれだけ動かしても画面高の 30% までしか進まない。
 *   動かすにつれて少し縮み、背景の黒が薄くなる。離したとき十分動いていたか速く弾いていれば ([shouldDismiss]) 閉じ、
 *   それ以外はばねで元に戻る。
 * * 予測型「戻る」([NavigationBackHandler]): ジェスチャの進み具合に合わせて縮み、確定したら閉じる。
 * * 閉じる: [DismissibleViewerState.dismiss] (閉じるボタンもこれを呼ぶ)。
 *
 * @param swipeEnabled 上下スワイプで閉じる操作を受け付けるか (画像を拡大している間は `false`)。
 *
 * 横方向のジェスチャ (HorizontalPager、シークバー) とはぶつからない。
 */
@Composable
fun DismissibleViewer(
    state: DismissibleViewerState,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val flingThreshold = with(LocalDensity.current) { FlingVelocity.toPx() }

    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (entered) {
            state.enterFraction = 1f
        } else {
            entered = true
            state.enter()
        }
    }

    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    // ジェスチャの進み具合は描画でだけ使うので、再コンポーズせずに状態へ写す
    LaunchedEffect(backState) {
        snapshotFlow {
            (backState.transitionState as? NavigationEventTransitionState.InProgress)?.latestEvent?.progress ?: 0f
        }.collect { state.backProgress = it }
    }
    NavigationBackHandler(
        state = backState,
        isBackEnabled = state.exitFraction == null,
        onBackCompleted = { scope.launch { state.dismiss() } },
    )

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { state.containerBounds = Rect(it.positionInWindow(), it.size.toSize()) }
            .drawBehind { drawRect(MediaOverlayColors.Background.copy(alpha = state.backgroundAlpha())) }
            .draggable(
                state = rememberDraggableState { delta -> state.dragOffset += delta },
                orientation = Orientation.Vertical,
                enabled = swipeEnabled && state.exitFraction == null,
                onDragStopped = { velocity ->
                    val height = state.containerBounds.height
                    val offset = rubberBand(state.dragOffset, height * MaxOffsetFraction)
                    if (shouldDismiss(offset, velocity, height, flingThreshold)) {
                        state.dismiss()
                    } else {
                        // 指の距離を 0 へ戻す (見た目はゴムの分だけ縮めて追従する)
                        animate(
                            state.dragOffset, 0f, velocity,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        ) { value, _ -> state.dragOffset = value }
                    }
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { with(state) { applyContentTransform() } },
            content = content,
        )
    }
}

/** graphicsLayer の変形 (中心基準の拡大率と移動量)。 */
data class LayerTransform(val scale: Float, val translationX: Float, val translationY: Float)

internal fun lerp(from: LayerTransform, to: LayerTransform, fraction: Float) = LayerTransform(
    scale = from.scale + (to.scale - from.scale) * fraction,
    translationX = from.translationX + (to.translationX - from.translationX) * fraction,
    translationY = from.translationY + (to.translationY - from.translationY) * fraction,
)

/**
 * ゴムのような引っかかり。指の距離 [offset] に対して、動き始めは半分ほど動き、遠くなるほど動かなくなり、
 * どれだけ動かしても [maxOffset] を超えない。
 */
fun rubberBand(offset: Float, maxOffset: Float): Float {
    if (maxOffset <= 0f) return 0f
    val distance = abs(offset)
    return sign(offset) * maxOffset * (1f - 1f / (distance * RubberBandCoefficient / maxOffset + 1f))
}

/** 閉じる操作の進み具合 (0〜1)。見た目の移動量 [offset] が [maxOffset] に近づくと 1。 */
fun dismissProgress(offset: Float, maxOffset: Float): Float =
    if (maxOffset <= 0f) 0f else (abs(offset) / maxOffset).coerceIn(0f, 1f)

/** ドラッグ中の縮み具合。最大で [DragShrink] だけ小さくなる。 */
internal fun dragScale(progress: Float): Float = 1f - DragShrink * progress

/**
 * 指を離したときに閉じるか。見た目の移動量 [offset] が画面高の [DismissDistanceFraction] 以上か、
 * 動かした向きに [flingThreshold] (px/s) 以上の速さで弾いたら閉じる。
 */
fun shouldDismiss(offset: Float, velocity: Float, height: Float, flingThreshold: Float): Boolean {
    if (height <= 0f) return false
    if (abs(offset) >= height * DismissDistanceFraction) return true
    // 戻す向きに弾いた場合は閉じない
    return abs(velocity) >= flingThreshold && (offset == 0f || sign(offset) == sign(velocity))
}

/**
 * [container] 全体に Fit で表示しているメディア (幅 / 高さ = [DismissTarget.aspectRatio]) を、[DismissTarget.bounds] に
 * 重ねるための変形。一覧のサムネイルは Crop なので縦横比が少し違うことがあり、拡大率は幅・高さの比の相乗平均で決める。
 */
fun exitTransformTo(target: DismissTarget, container: Rect): LayerTransform? {
    if (container.width <= 0f || container.height <= 0f || target.aspectRatio <= 0f || target.bounds.isEmpty) return null
    val fitted = fitSize(target.aspectRatio, container.size)
    val scale = sqrt((target.bounds.width / fitted.width) * (target.bounds.height / fitted.height))
    val delta: Offset = target.bounds.center - container.center
    return LayerTransform(scale = scale, translationX = delta.x, translationY = delta.y)
}

/** 縦横比 [aspectRatio] のものを [container] に Fit で収めたときの大きさ。 */
fun fitSize(aspectRatio: Float, container: Size): Size =
    if (container.width / container.height > aspectRatio) {
        Size(container.height * aspectRatio, container.height)
    } else {
        Size(container.width, container.width / aspectRatio)
    }

/** 見た目の移動量の上限 (画面高に対する割合)。 */
private const val MaxOffsetFraction = 0.3f
/** 指の距離に対する、動き始めの見た目の移動量の割合。小さいほど強く引っかかる。 */
private const val RubberBandCoefficient = 0.55f
/** 閉じるのに必要な見た目の移動量 (画面高に対する割合)。指で 1/4 画面ほど動かすと届く。 */
private const val DismissDistanceFraction = 0.08f
private const val DragShrink = 0.15f
private const val BackgroundFadeOnDrag = 0.6f
private const val ExitMillis = 300
/** 開くときのフェード (OS 標準のフェードイン 500ms の 0.7 倍)。 */
private const val EnterMillis = 350
/** 閉じるアニメーションのうち、ここから先で中身を消す。 */
private const val ExitFadeStart = 0.75f
private val FlingVelocity = 1000.dp
