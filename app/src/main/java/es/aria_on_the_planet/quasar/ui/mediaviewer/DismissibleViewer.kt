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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * メディアビューアの出入りの状態。開く (一覧の元の位置から広がる)・閉じる (一覧の元の位置へ縮んで戻る) のアニメーションと、
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

    /** 開くときに広がり始める変形 (一覧の元の位置)。`null` ならその場でフェードインする。 */
    private var enterFrom: LayerTransform? = null

    /** 閉じるアニメーションの進み具合 (0〜1)。`null` なら閉じていない。 */
    internal var exitFraction by mutableStateOf<Float?>(null)

    /** 開くときに大きさが決まるのを待つので state にしている。 */
    internal var containerBounds by mutableStateOf(Rect.Zero)

    /** 閉じ始めた時点の見た目と、閉じる先の変形。 */
    private var exitFrom = Identity
    private var exitTo = Identity
    private var exitFromAlpha = 1f
    private var exitFromBackgroundAlpha = 1f
    private var exitToTarget = false

    /** 開く途中・ドラッグ中・戻り中・「戻る」の途中・閉じる途中。上部バーや再生操作を隠すのに使う。 */
    val isActive: Boolean by derivedStateOf {
        enterFraction < 1f || dragOffset != 0f || backProgress != 0f || exitFraction != null
    }

    /**
     * 開く。一覧の元の位置 ([target]) から広がる (閉じるときの逆回し)。戻り先が分からなければフェードイン。
     */
    internal suspend fun enter() {
        enterFraction = 0f
        // 最初のレイアウトで大きさが決まるまで待つ (それまでは透明)
        val container = snapshotFlow { containerBounds }.first { !it.isEmpty }
        val from = target()?.let { exitTransformTo(it, container) }
        enterFrom = from
        val spec = if (from != null) {
            tween<Float>(EnterMillis, easing = FastOutSlowInEasing)
        } else {
            tween(EnterFadeMillis, easing = LinearOutSlowInEasing)
        }
        animate(0f, 1f, animationSpec = spec) { value, _ -> enterFraction = value }
    }

    /**
     * 閉じる。今の見た目 (開く途中・ドラッグ・「戻る」の途中) から、一覧の元の位置 ([target]) へ縮みながら移動して消える。
     * 戻り先が分からなければその場で縮みながら消える。終わったら [onDismissed]。
     */
    suspend fun dismiss() {
        if (exitFraction != null) return
        exitFrom = currentTransform()
        exitFromAlpha = enterAlpha()
        exitFromBackgroundAlpha = backgroundAlpha()
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
            (1f - BackgroundFadeOnDrag * currentProgress()) * enterFraction
        } else {
            exitFromBackgroundAlpha * (1f - exit)
        }
        return alpha.coerceIn(0f, 1f)
    }

    /** 中身の変形と濃さを graphicsLayer に当てる (描画フェーズで読むので再コンポーズしない)。 */
    internal fun GraphicsLayerScope.applyContentTransform() {
        val exit = exitFraction
        val transform: LayerTransform
        if (exit == null) {
            transform = currentTransform()
            alpha = enterAlpha()
        } else {
            transform = lerp(exitFrom, exitTo, exit)
            alpha = exitFromAlpha * if (exitToTarget) {
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

    /** 閉じていない間の変形 (開く途中の広がりに、ドラッグ・「戻る」の縮みと移動を重ねる)。 */
    private fun currentTransform(): LayerTransform {
        val base = enterFrom?.let { lerp(it, Identity, enterFraction) } ?: Identity
        val maxOffset = containerBounds.height * MaxOffsetFraction
        return LayerTransform(
            scale = base.scale * dragScale(currentProgress()),
            translationX = base.translationX,
            translationY = base.translationY + rubberBand(dragOffset, maxOffset),
        )
    }

    /** 開く途中の中身の濃さ。広がるときは最初の少しで現れ、下のサムネイルから引き継ぐ (閉じるときの逆)。 */
    private fun enterAlpha(): Float =
        if (enterFrom != null) (enterFraction / (1f - ExitFadeStart)).coerceIn(0f, 1f) else enterFraction

    private fun currentProgress(): Float {
        val maxOffset = containerBounds.height * MaxOffsetFraction
        return maxOf(dismissProgress(rubberBand(dragOffset, maxOffset), maxOffset), backProgress)
    }
}

/**
 * @param target 開くときに広がり始める元・閉じるときに縮んで戻る先。開き始め・閉じ始めの時点で呼ぶ。
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

/** 開くときに広がり始める元・閉じるときに縮んで戻る先。[bounds] はウィンドウ上の位置 (px)、[aspectRatio] は表示中のメディアの幅 / 高さ。 */
data class DismissTarget(val bounds: Rect, val aspectRatio: Float)

/**
 * メディアビューアの外枠。背景の黒を自前で描き (元の画面の上に重ねているので、黒が薄くなると透けて見える)、出入りを担う。
 *
 * * 開く: 最初の表示で一覧の元の位置から広がる ([EnterMillis]、閉じるときの逆回し)。元の位置が分からなければ
 *   フェードイン ([EnterFadeMillis])。回転等の作り直しでは繰り返さない。
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

private val Identity = LayerTransform(1f, 0f, 0f)

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
/** 開くときに元の位置から広がる長さ (閉じるときと揃える)。 */
private const val EnterMillis = ExitMillis
/** 元の位置が分からないときのフェード (OS 標準のフェードイン 500ms の 0.7 倍)。 */
private const val EnterFadeMillis = 350
/** 閉じるアニメーションのうち、ここから先で中身を消す (開くときは 1 からこれを引いた分で現れる)。 */
private const val ExitFadeStart = 0.75f
private val FlingVelocity = 1000.dp
