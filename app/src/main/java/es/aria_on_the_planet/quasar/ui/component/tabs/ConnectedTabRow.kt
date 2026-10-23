package es.aria_on_the_planet.quasar.ui.component.tabs

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.ui.component.modifier.horizontalFadingEdges
import kotlinx.coroutines.launch

/**
 * [ConnectedTabRow] のタブ 1 つ。[icon] はラベルが収まらないときに代わりに出す (選択中は [selectedIcon])。
 * アイコンは `Symbols.Outlined.*`、[selectedIcon] は `Symbols.Filled.*` を渡す。
 */
@Immutable
data class ConnectedTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

/**
 * 画面内のタブ切り替え。Material 3 Expressive の連結ボタン (connected button group)。
 * 選択中のボタンは塗り (primary)、押下で内側の角が締まる。
 *
 * 幅は [layoutTabs] で決める: 全部のラベルが等幅に収まれば等幅でラベル。収まらなければ選択中だけラベルを出して
 * その分広げ、他はアイコンにして残りを等分する。それでも画面に収まらなければ横にスクロールでき、まだスクロール
 * できる側の端をフェードさせる。選択が変わったら幅を動かし、選んだボタンを見える位置へスクロールする。
 *
 * 一覧の上に常に出ているので目立ちすぎないよう、既定 (ピル型・40dp) より細く (ExtraSmall = 32dp)、
 * 角丸も小さめの角丸長方形にしている。
 *
 * タイムラインのカラム切り替え (`TimelineHeader`) はぼかし・幅の追従があるので別実装。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ConnectedTabRow(
    tabs: List<ConnectedTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val startFade by animateFloatAsState(if (scrollState.canScrollBackward) 1f else 0f, label = "tabsStartFade")
    val endFade by animateFloatAsState(if (scrollState.canScrollForward) 1f else 0f, label = "tabsEndFade")
    val reveals = remember(tabs.size) { List(tabs.size) { TabReveal() } }
    val widthSpec = MaterialTheme.motionScheme.fastSpatialSpec<Int>()
    val scope = rememberCoroutineScope()
    val currentSelected by rememberUpdatedState(selectedIndex)
    // 選んだボタンを、外側の余白ごと見える位置へ (端のフェードに掛からないように)
    val revealMargin = with(LocalDensity.current) { HorizontalPadding.toPx() }

    LaunchedEffect(selectedIndex, reveals) {
        reveals.getOrNull(selectedIndex)?.reveal(revealMargin)
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .horizontalFadingEdges(width = FadingEdgeWidth, start = { startFade }, end = { endFade }),
    ) {
        val viewportWidth = constraints.maxWidth
        SubcomposeLayout(
            Modifier
                // 収まっているときはスクロールを切る (横のドラッグを親の HorizontalPager に渡す)
                .horizontalScroll(scrollState, enabled = scrollState.maxValue > 0)
                .padding(horizontal = HorizontalPadding, vertical = 6.dp),
        ) { constraints ->
            val gap = ButtonGroupDefaults.ConnectedSpaceBetween.roundToPx()
            val contentWidth = viewportWidth - HorizontalPadding.roundToPx() * 2
            val available = (contentWidth - gap * (tabs.size - 1)).coerceAtLeast(0)
            // ラベル版・アイコン版それぞれの本来の幅 (表示はしない)
            val natural = subcompose(MeasureSlot) {
                tabs.forEach { tab ->
                    TabButton(tab, showLabel = true, checked = false, onClick = {}, isFirst = false, isLast = false)
                    TabButton(tab, showLabel = false, checked = false, onClick = {}, isFirst = false, isLast = false)
                }
            }.map { it.maxIntrinsicWidth(constraints.maxHeight) }
            val target = layoutTabs(
                labelWidths = natural.filterIndexed { i, _ -> i % 2 == 0 },
                iconWidths = natural.filterIndexed { i, _ -> i % 2 == 1 },
                selected = selectedIndex,
                available = available,
            )
            val placeables = subcompose(ContentSlot) {
                tabs.forEachIndexed { index, tab ->
                    val width = animateIntAsState(target.widths[index], widthSpec, label = "tabWidth") {
                        // 選んだときのスクロールは広がる前の幅で行われるので、広がり終えたらもう一度
                        if (index == currentSelected) scope.launch { reveals[index].reveal(revealMargin) }
                    }
                    TabButton(
                        tab = tab,
                        showLabel = target.showLabel[index],
                        checked = index == selectedIndex,
                        onClick = { onSelect(index) },
                        isFirst = index == 0,
                        isLast = index == tabs.lastIndex,
                        modifier = Modifier
                            .bringIntoViewRequester(reveals[index].requester)
                            // 幅はレイアウトの段階で読む (動いている間に再コンポーズしない)
                            .layout { measurable, c ->
                                val w = width.value
                                val placeable = measurable.measure(c.copy(minWidth = w, maxWidth = w))
                                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                            }
                            .onSizeChanged { reveals[index].size = it },
                    )
                }
            }.map { it.measure(Constraints(maxHeight = constraints.maxHeight)) }

            val total = placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)
            // 動いている途中の丸めで数 px はみ出すだけならスクロールさせない
            val width = if (total <= contentWidth + placeables.size) contentWidth.coerceAtLeast(0) else total
            layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
                var x = 0
                placeables.forEach {
                    it.placeRelative(x, 0)
                    x += it.width + gap
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TabButton(
    tab: ConnectedTab,
    showLabel: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = modifier.semantics { role = Role.Tab },
        buttonSize = ToggleButtonSize.ExtraSmall,
        shapes = tabShapes(isFirst = isFirst, isLast = isLast),
    ) {
        Crossfade(showLabel, label = "tabContent") { label ->
            if (label) {
                // 幅が動いている途中は切れて見えるだけにする (省略記号を出さない)
                Text(tab.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            } else {
                Icon(
                    if (checked) tab.selectedIcon else tab.icon,
                    contentDescription = tab.label,
                    modifier = Modifier.size(tabIconSize()),
                )
            }
        }
    }
}

/**
 * アイコンの大きさ。ExtraSmall のボタンの既定 ([ButtonDefaults.ExtraSmallIconSize]、ラベルの文字 14sp に対して 20dp) を、
 * 端末の文字サイズの設定でラベルの文字が拡大・縮小された分だけ合わせる (dp のままだと文字だけ大きさが変わって釣り合わない)。
 * アイコンの高さはラベルの行の高さ (labelLarge は 20sp) と同じ比で動くので、アイコンだけのボタンとラベルのボタンの高さも揃う。
 * 倍率は実際の文字の大きさから求めるので、Android 14 以降の非線形な拡大にも合う。
 */
@Composable
private fun tabIconSize(): Dp {
    val fontSize = LocalTextStyle.current.fontSize
    if (!fontSize.isSp) return ButtonDefaults.ExtraSmallIconSize
    val scale = with(LocalDensity.current) { fontSize.toDp() / fontSize.value.dp }
    return ButtonDefaults.ExtraSmallIconSize * scale
}

/** ボタンを見える位置へスクロールする。[size] はボタンの大きさ (レイアウトのたびに上書きする)。 */
@OptIn(ExperimentalFoundationApi::class)
private class TabReveal {
    val requester = BringIntoViewRequester()
    var size = IntSize.Zero

    /** 左右に [margin] (px) の余白を付けて見える位置へ。 */
    suspend fun reveal(margin: Float) {
        requester.bringIntoView(Rect(-margin, 0f, size.width + margin, size.height.toFloat()))
    }
}

/** 各タブの幅 (px) と、ラベルを出すか (`false` ならアイコン)。 */
internal data class TabLayout(val widths: List<Int>, val showLabel: List<Boolean>)

/**
 * タブの幅を決める。[labelWidths] / [iconWidths] はラベル版・アイコン版のボタンの本来の幅、[available] は
 * ボタンに使える幅 (間の隙間を除く)。
 *
 * 1. 全部のラベルが等幅に収まれば、等幅でラベル。
 * 2. 収まらなければ、[selected] だけラベルを出してその幅 (等分より狭ければ等分) を取り、他はアイコンで残りを等分する
 *    (等分では足りないアイコンが無いとき。割り切れない分は選択中に足す)。
 * 3. それでも収まらなければ、本来の幅のまま (合計が [available] を超える = 横スクロール)。他のアイコンは同じ幅に揃える。
 */
internal fun layoutTabs(labelWidths: List<Int>, iconWidths: List<Int>, selected: Int, available: Int): TabLayout {
    val n = labelWidths.size
    if (n == 0) return TabLayout(emptyList(), emptyList())
    val equal = available / n
    if (labelWidths.max() <= equal) {
        return TabLayout(List(n) { if (it == n - 1) available - equal * (n - 1) else equal }, List(n) { true })
    }
    val sel = selected.coerceIn(0, n - 1)
    val showLabel = List(n) { it == sel }
    if (n == 1) return TabLayout(listOf(maxOf(labelWidths[0], available)), showLabel)
    val labelWidth = labelWidths[sel]
    val iconWidth = iconWidths.filterIndexed { i, _ -> i != sel }.max()
    val share = (available - maxOf(labelWidth, equal)) / (n - 1)
    return if (share >= iconWidth) {
        TabLayout(List(n) { if (it == sel) available - share * (n - 1) else share }, showLabel)
    } else {
        TabLayout(List(n) { if (it == sel) labelWidth else iconWidth }, showLabel)
    }
}

/** 外側の角は [OuterCorner]、隣と接する内側の角は [InnerCorner] (押下中は [PressedInnerCorner])。選択中は全部 [OuterCorner]。 */
private fun tabShapes(isFirst: Boolean, isLast: Boolean): ToggleButtonShapes {
    fun shape(inner: Dp) = RoundedCornerShape(
        topStart = if (isFirst) OuterCorner else inner,
        bottomStart = if (isFirst) OuterCorner else inner,
        topEnd = if (isLast) OuterCorner else inner,
        bottomEnd = if (isLast) OuterCorner else inner,
    )
    return ToggleButtonShapes(
        shape = shape(InnerCorner),
        pressedShape = shape(PressedInnerCorner),
        checkedShape = RoundedCornerShape(OuterCorner),
    )
}

private const val MeasureSlot = "measure"
private const val ContentSlot = "content"

private val HorizontalPadding = 16.dp
private val FadingEdgeWidth = 12.dp
private val OuterCorner = 10.dp
private val InnerCorner = 4.dp
private val PressedInnerCorner = 2.dp
