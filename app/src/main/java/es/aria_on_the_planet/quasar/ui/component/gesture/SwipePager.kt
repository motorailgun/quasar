package es.aria_on_the_planet.quasar.ui.component.gesture

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 左右スワイプでページを切り替える pager の状態。スワイプにはゴムの抵抗が付く。
 *
 * * ドラッグ中は指に 1:1 では付かず、[rubberBand] で引っかかる (遠くへ引くほど重く、ページ幅の [MaxOffsetFraction]
 *   までしか動かない)。隣のページはその分だけ見える。
 * * 離したとき、見た目の移動量が [SwitchDistanceFraction] 以上か、その向きに速く弾いていれば隣のページへ、
 *   そうでなければ元のページへ [animationSpec] (spring) で戻る。弾いた速さは spring の初速に引き継ぐ。
 * * 端 (隣のページが無い向き) ではページ全体を少しだけずらし、離すと戻す。spring が端を行き過ぎた分も同様。
 *
 * foundation の HorizontalPager は使わない。Pager は内部の nested scroll 接続で、ページが半端な位置にあるとき子
 * (メディアの横並び) から届く移動を親より先に横取りして 1:1 で動かすため、ゴムの抵抗を掛けられない。
 */
@Stable
class SwipePagerState internal constructor(
    initialPage: Int,
    val pageCount: Int,
    private val animationSpec: AnimationSpec<Float>,
    private val flingThreshold: Float,
) : ScrollableState {
    /** 表示位置 (ページ単位、0〜pageCount-1)。 */
    var position by mutableFloatStateOf(initialPage.toFloat())
        private set

    /** 落ち着いているページ。スワイプ・アニメーションが終わったときに更新する。 */
    var settledPage by mutableIntStateOf(initialPage)
        private set

    /** 向かっているページ (アニメーション中はその行き先、それ以外は表示位置に最も近いページ)。 */
    val targetPage: Int get() = animationTarget ?: position.roundToInt()

    /**
     * 指でスワイプしている途中か (離した後の spring も含む。子の横スクロールから届いたものも含む)。
     * ボタンで選んだときのアニメーションは含まない。
     */
    var isSwiping by mutableStateOf(false)
        private set

    /** 端でのずれ (px、前のページ側が正)。 */
    internal var edgeOffset by mutableFloatStateOf(0f)
        private set

    internal var pageWidth = 0f

    private var animationTarget: Int? by mutableStateOf(null)
    private var scrolling by mutableStateOf(false)
    private val mutex = MutatorMutex()

    /** 指の距離 (px、前のページ側が正)。見た目の移動量はここから [rubberBand] で求める。 */
    private var finger = 0f
    private var basePage = initialPage

    private val maxOffset: Float get() = pageWidth * MaxOffsetFraction
    private val lastPage: Int get() = (pageCount - 1).coerceAtLeast(0)

    /** [basePage] からの見た目の移動量 (px、前のページ側が正)。端のずれは含まない。 */
    private fun pageOffset(): Float = -(position - basePage) * pageWidth

    private fun hasPageToward(visual: Float): Boolean = if (visual > 0f) basePage > 0 else basePage < lastPage

    /** 今の見た目から、基準のページと指の距離を求め直す (アニメーション中に掴み直した場合も続きから動かす)。 */
    private fun begin() {
        isSwiping = true
        basePage = position.roundToInt()
        finger = rubberBandInverse(pageOffset() + edgeOffset / EdgeFraction, maxOffset)
    }

    /** 指の距離に合わせて表示位置と端のずれを動かす。 */
    private fun follow() {
        if (pageWidth <= 0f) return
        val visual = rubberBand(finger, maxOffset)
        val toPage = if (hasPageToward(visual)) visual else 0f
        edgeOffset = (visual - toPage) * EdgeFraction
        position = (basePage - toPage / pageWidth).coerceIn(0f, lastPage.toFloat())
    }

    override val isScrollInProgress: Boolean get() = scrolling

    override fun dispatchRawDelta(delta: Float): Float {
        // 子の横スクロールから nested scroll で届く分は scroll() を通らないので、最初の 1 回で基準を求める
        if (!isSwiping) begin()
        finger += delta
        follow()
        return delta
    }

    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
        mutex.mutate(scrollPriority) {
            scrolling = true
            try {
                begin()
                object : ScrollScope {
                    override fun scrollBy(pixels: Float): Float {
                        finger += pixels
                        follow()
                        return pixels
                    }
                }.block()
            } finally {
                scrolling = false
            }
        }
    }

    internal val flingBehavior = object : FlingBehavior {
        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
            val target = swipeTargetPage(
                basePage = basePage,
                pageCount = pageCount,
                offset = rubberBand(finger, maxOffset),
                velocity = initialVelocity,
                switchDistance = pageWidth * SwitchDistanceFraction,
                flingThreshold = flingThreshold,
            )
            // 指の速さを見た目の速さに直して spring の初速にする (ページ単位、行き先側が正)
            val velocity = -initialVelocity * rubberBandSlope(finger, maxOffset) / pageWidth.coerceAtLeast(1f)
            settleTo(target, velocity)
            return 0f
        }
    }

    /** [page] へ spring で動かす (ボタンで選んだとき)。指で掴まれたら止まる。 */
    suspend fun animateScrollToPage(page: Int) {
        mutex.mutate(MutatePriority.Default) {
            scrolling = true
            try {
                settleTo(page, 0f)
            } finally {
                scrolling = false
            }
        }
    }

    /**
     * 表示位置を [page] へ動かす。端のずれも含めた見た目の位置から始め、spring が端のページを行き過ぎた分は
     * 端のずれとして描く。
     */
    private suspend fun settleTo(page: Int, velocity: Float) {
        val target = page.coerceIn(0, lastPage)
        animationTarget = target
        try {
            val start = position - edgeOffset / pageWidth.coerceAtLeast(1f)
            animate(start, target.toFloat(), velocity, animationSpec) { value, _ ->
                position = value.coerceIn(0f, lastPage.toFloat())
                edgeOffset = (position - value) * pageWidth
            }
            edgeOffset = 0f
            settledPage = target
        } finally {
            // 途中で掴み直された場合も、次の操作は今の見た目から基準を求め直す
            animationTarget = null
            finger = 0f
            isSwiping = false
        }
    }
}

@Composable
fun rememberSwipePagerState(
    initialPage: Int,
    pageCount: Int,
    animationSpec: AnimationSpec<Float>,
): SwipePagerState {
    val flingThreshold = with(LocalDensity.current) { FlingVelocity.toPx() }
    return remember(pageCount) {
        SwipePagerState(initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)), pageCount, animationSpec, flingThreshold)
    }
}

/**
 * ページを横に並べ、[SwipePagerState] のスワイプで切り替える。表示中 (半分でも見えている) のページだけを
 * コンポーズする。子の横スクロールが使い切らなかった分も nested scroll でここに届く。
 */
@Composable
fun SwipePager(
    state: SwipePagerState,
    modifier: Modifier = Modifier,
    key: (Int) -> Any = { it },
    content: @Composable (page: Int) -> Unit,
) {
    // 指を右へ動かすと正 (前のページ側)。RTL では前のページが右にあるので逆にする
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val direction = if (rtl) -1f else 1f
    val visiblePages by remember(state) {
        derivedStateOf {
            val first = floor(state.position).toInt().coerceAtLeast(0)
            val last = ceil(state.position).toInt().coerceAtMost(state.pageCount - 1)
            first..last
        }
    }
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { state.pageWidth = it.width.toFloat() }
            .scrollable(
                state = state,
                orientation = Orientation.Horizontal,
                reverseDirection = rtl,
                flingBehavior = state.flingBehavior,
            ),
    ) {
        for (page in visiblePages) {
            key(key(page)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = direction * ((page - state.position) * size.width + state.edgeOffset)
                        },
                ) {
                    content(page)
                }
            }
        }
    }
}

/**
 * 指を離したときに落ち着くページ。見た目の移動量 [offset] (前のページ側が正) が [switchDistance] 以上か、
 * 同じ向きに [flingThreshold] 以上の速さ [velocity] で弾いていれば隣へ。逆向きに弾いたら元のページに戻す。
 * 隣のページが無ければ元のページ。
 */
fun swipeTargetPage(
    basePage: Int,
    pageCount: Int,
    offset: Float,
    velocity: Float,
    switchDistance: Float,
    flingThreshold: Float,
): Int {
    val direction = when {
        abs(velocity) >= flingThreshold -> if (offset == 0f || sign(offset) == sign(velocity)) sign(velocity) else 0f
        abs(offset) >= switchDistance -> sign(offset)
        else -> 0f
    }
    // 前のページ側 (正) はページ番号が小さい側
    return (basePage - direction.toInt()).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
}

/** 見た目の移動量の上限 (ページ幅に対する割合)。 */
private const val MaxOffsetFraction = 0.5f
/** 隣のページへ切り替わる見た目の移動量 (ページ幅に対する割合)。 */
private const val SwitchDistanceFraction = 0.15f
/** 端 (隣のページが無い向き) でのずれの大きさ。ページを動かす場合に対する割合。 */
private const val EdgeFraction = 0.3f
private val FlingVelocity = 400.dp
