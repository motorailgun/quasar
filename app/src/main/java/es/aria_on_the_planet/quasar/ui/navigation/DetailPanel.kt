package es.aria_on_the_planet.quasar.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import es.aria_on_the_planet.quasar.domain.model.settings.PredictiveBackSettings
import es.aria_on_the_planet.quasar.ui.component.layout.LocalFloatingPanelInset
import es.aria_on_the_planet.quasar.ui.component.scaffold.LocalBackClosesPanel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/*
 * 詳細のパネル (docs/tablet-layout.md の「詳細のパネル」)。タイムラインを 2 カラムで出している間は、ポスト・プロフィール等を
 * タイムラインの上に浮かぶカード (右半分) で開く。出入りと中の切り替えはここで描く (NavDisplay の遷移は付かない)。
 */

/** 詳細のパネルの状態。[QuasarNavHost] が 1 つ持つ。[pop] はバックスタックの末尾を外す (アニメーションは付けない)。 */
@Stable
class DetailPanelState(private val pop: () -> Unit) {
    /** 予測型「戻る」の動きの設定 (全画面の「戻る」と同じもの)。 */
    var backSettings by mutableStateOf(PredictiveBackSettings())

    /** パネルが覆っている右側の幅 (出入りに合わせて動く)。[LocalFloatingPanelInset] で下の画面に渡す。 */
    var coveredWidth by mutableStateOf(0.dp)
        internal set

    /** 表示中のパネルの「戻る」(アニメーションしてから外す)。パネルが無ければ null。 */
    internal var back: (() -> Unit)? = null

    /** パネルの一番上の画面を閉じる。パネルが表示されていなければ `false` (呼び出し側が普通に外す)。 */
    fun requestBack(): Boolean {
        val back = back ?: return false
        back()
        return true
    }

    internal fun popNow() = pop()
}

/**
 * パネルで開く画面か。エントリの metadata ([DetailPanelSceneStrategy.panel]) と同じ画面を並べる
 * ([Navigator.back] はキーしか持たないのでこちらで見る)。
 */
internal fun NavKey.opensInPanel(): Boolean = when (this) {
    is Route.Post, is Route.Profile, is Route.Hashtag, is Route.FollowList,
    is Route.AccountReactions, is Route.PostReactions, is Route.OpenLink,
    is Route.PersonalPosts, is Route.FollowRequests, is Route.Search -> true
    else -> false
}

/**
 * 詳細のパネルを出す Scene。[enabled] (タイムラインが 2 カラムになる幅・設定のとき) の間は、どの画面もこの Scene で描く:
 * パネルの画面 ([panel] の印) でない最後の画面を下地にし、その上に続くパネルの画面をカードで重ねる。
 *
 * Scene のキーは下地の画面のキー。パネルの画面を積んでも外しても Scene が変わらないので、NavDisplay の遷移は起きず
 * (`AnimatedContent` は同じキーの中身を差し替えるだけ)、出入りと中の切り替えはカードが自分で描く。下地が替わるとき
 * (設定を開く等) は今までどおり NavDisplay の遷移。`OverlayScene` にしないのは、NavDisplay が重ねる Scene をキーで
 * 1 つにまとめ、最初のものを描き続けるため (中身を差し替えられない)。
 *
 * 一番下からパネルの画面のとき (共有から開いた等) は null (今までどおり全画面)。
 */
class DetailPanelSceneStrategy<T : Any>(private val state: DetailPanelState, private val enabled: Boolean) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!enabled) return null
        val base = entries.indexOfLast { it.metadata[PanelKey] != true }
        if (base < 0) return null
        return DetailPanelScene(
            base = entries[base],
            panelEntries = entries.subList(base + 1, entries.size).toList(),
            previousEntries = entries.dropLast(1),
            state = state,
        )
    }

    companion object {
        /** このエントリを詳細のパネルで開く (幅の広い画面のとき)。 */
        fun panel() = metadata { put(PanelKey, true) }

        private object PanelKey : NavMetadataKey<Boolean>
    }
}

private class DetailPanelScene<T : Any>(
    private val base: NavEntry<T>,
    private val panelEntries: List<NavEntry<T>>,
    override val previousEntries: List<NavEntry<T>>,
    private val state: DetailPanelState,
) : Scene<T> {
    override val key: Any = base.contentKey
    override val entries: List<NavEntry<T>> = listOf(base) + panelEntries
    override val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            base.Content()
            if (panelEntries.isNotEmpty()) DetailPanel(panelEntries, state)
        }
    }
}

/**
 * パネルの本体。右半分に、周りに [Margin] の余白を取った角丸のカード。カードの外のタッチは下の画面に届き、カードの上は
 * カード ([Surface]) が受ける。
 */
@Composable
private fun <T : Any> DetailPanel(entries: List<NavEntry<T>>, state: DetailPanelState) {
    val scope = rememberCoroutineScope()
    val spec = slideSpec<Float>()
    val top = entries.last()
    val below = entries.getOrNull(entries.lastIndex - 1)

    // カードの出入り (0 = 定位置、1 = 右の画面外)。回転等の作り直しでは開くアニメーションを繰り返さない
    var entered by rememberSaveable { mutableStateOf(false) }
    val cardOffset = remember { Animatable(if (entered) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!entered) {
            entered = true
            cardOffset.animateTo(0f, spec)
        }
    }

    // 進む: 一番上が替わって画面が増えたら、新しい画面を右から入れる (0 → 1)。最初のコンポジションから右に置くため
    // 一番上の画面ごとに作る (戻ったときは 1 = 動かさない。戻るアニメーションは外す前に済んでいる)
    val lastSize = remember { intArrayOf(entries.size) }
    val push = remember(top.contentKey) { Animatable(if (entries.size > lastSize[0]) 0f else 1f) }
    SideEffect { lastSize[0] = entries.size }
    LaunchedEffect(push) { push.animateTo(1f, spec) }

    // 戻る: 一番上の画面が右へ抜ける (0 → 1)。最後の 1 つならカードごと抜ける。抜けきったら外すが、NavDisplay が新しい
    // バックスタックを描くのは次のフレームなので、ここで 0 に戻すと抜けた画面が 1 フレーム出てしまう。一番上の画面ごとに作る
    val pop = remember(top.contentKey) { Animatable(0f) }
    var popping by remember { mutableStateOf(false) }
    fun backTarget() = if (entries.size == 1) cardOffset else pop
    fun finishBack() {
        if (popping) return
        popping = true
        scope.launch {
            try {
                backTarget().animateTo(1f, spec)
                state.popNow()
            } finally {
                popping = false
            }
        }
    }
    SideEffect { state.back = ::finishBack }
    DisposableEffect(state) {
        onDispose {
            state.back = null
            state.coveredWidth = 0.dp
        }
    }

    // 予測型「戻る」: 引いた量を全画面の「戻る」と同じく抑えて動かし、離したら残りを抜ける。取り消しなら戻す
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    LaunchedEffect(backState, pop) {
        var tracker: BackDragTracker? = null // ジェスチャごと
        snapshotFlow { backState.transitionState }.collectLatest { transition ->
            if (popping) return@collectLatest
            if (transition is NavigationEventTransitionState.InProgress) {
                val event = transition.latestEvent
                val t = tracker ?: BackDragTracker(state.backSettings).also { tracker = it }
                backTarget().snapTo(t.offset(event.progress, event.touchX))
            } else {
                tracker = null
                backTarget().animateTo(0f, spec)
            }
        }
    }
    NavigationBackHandler(state = backState, isBackEnabled = !popping, onBackCompleted = ::finishBack)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
        val direction = LocalLayoutDirection.current
        // 右から入って右へ抜ける (右から左に書く言語では左右が逆)
        val sign = if (direction == LayoutDirection.Ltr) 1f else -1f
        val endInset = bars.calculateEndPadding(direction)
        val cardWidth = (maxWidth - endInset) / 2 - Margin * 2
        // パネルが覆う幅 (カードと両側の余白・端のインセット)。カードが抜けるときもこの分動かす
        val covered = cardWidth + Margin * 2 + endInset
        // カードの外側 (画面の端から)。中の画面のインセットはこの分を消費する (ステータスバーの分を中で空けない。
        // キーボードはこれを超えた分だけ中に残る)
        val outer = PaddingValues(
            start = maxWidth - covered + Margin,
            top = bars.calculateTopPadding() + Margin,
            end = endInset + Margin,
            bottom = bars.calculateBottomPadding() + Margin,
        )
        LaunchedEffect(covered) {
            snapshotFlow { covered * (1f - cardOffset.value) }.collect { state.coveredWidth = it }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(
                    top = outer.calculateTopPadding(),
                    bottom = outer.calculateBottomPadding(),
                    end = outer.calculateEndPadding(direction),
                )
                .width(cardWidth)
                .fillMaxHeight()
                .graphicsLayer { translationX = sign * cardOffset.value * covered.toPx() },
            shape = MaterialTheme.shapes.extraLarge,
            color = panelSurface(MaterialTheme.colorScheme),
            shadowElevation = 6.dp,
        ) {
            PanelColors {
            CompositionLocalProvider(LocalFloatingPanelInset provides 0.dp) {
                Box(Modifier.fillMaxSize().consumeWindowInsets(outer)) {
                    val showBelow by remember(below, push, pop) {
                        derivedStateOf { below != null && (push.value < 1f || pop.value > 0f) }
                    }
                    if (showBelow && below != null) {
                        PanelEntry(below, isFirst = entries.size == 2) {
                            // 進むときは左へ少しずれて薄くなり、戻るときはその逆
                            val shift = if (pop.value > 0f) 1f - pop.value else push.value
                            translationX = -sign * shift * size.width / UnderlapFraction
                            alpha = 1f - shift
                        }
                    }
                    PanelEntry(top, isFirst = entries.size == 1) {
                        translationX = sign * ((1f - push.value) + pop.value) * size.width
                    }
                }
            }
            }
        }
    }
}

/** 詳細のパネルの中の画面か。パネルの中のクイック投稿バー・返信はパネルの下書き (`AppContainer.panelDraftStore`) を使う。 */
val LocalInDetailPanel = compositionLocalOf { false }

@Composable
private fun <T : Any> PanelEntry(
    entry: NavEntry<T>,
    isFirst: Boolean,
    layer: GraphicsLayerScope.() -> Unit,
) {
    key(entry.contentKey) {
        Box(Modifier.fillMaxSize().graphicsLayer(layer)) {
            CompositionLocalProvider(LocalBackClosesPanel provides isFirst, LocalInDetailPanel provides true) {
                entry.Content()
            }
        }
    }
}

/**
 * パネルの中の画面の面の色。中の画面は自分の背景 (Scaffold = background、トップバー = surface) で全体を塗るので、
 * カードの色を変えるには中の配色ごと差し替える。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PanelColors(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val surface = panelSurface(colors)
    MaterialExpressiveTheme(
        colorScheme = colors.copy(background = surface, surface = surface),
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

// 試し: 1 = surface (今まで) / 2 = surfaceContainerLow / 3 = surfaceContainer
private fun panelSurface(colors: ColorScheme): Color = colors.surface

private val Margin = 12.dp
