package es.aria_on_the_planet.quasar.ui.component.layout

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * [CollapsingHeaderLayout] の折りたたみ量。[offset] は見出しが上に隠れている量 (px、0 = 全部見えている)。
 */
@Stable
class CollapsingHeaderState(initialOffset: Float = 0f) {
    var offset by mutableFloatStateOf(initialOffset)
        private set

    /** 最大の折りたたみ量 (見出しの高さ - 畳み切ったときに残す上端の分)。レイアウトで決まる。 */
    var maxOffset by mutableFloatStateOf(0f)
        private set

    /**
     * 見出しの高さが変わったとき。畳み切っていたら畳んだままにする (補助情報が後から届いて見出しが伸びても、
     * 一覧を読んでいる途中で見出しの下端が出てきて一覧ごと下へずれないように)。
     */
    internal fun updateMaxOffset(value: Float) {
        if (maxOffset == value) return
        if (isCollapsed) offset = value
        maxOffset = value
    }

    /** 見出しが完全に隠れたか (トップバーにタイトルを出す判定などに使う)。 */
    val isCollapsed: Boolean by derivedStateOf { maxOffset > 0f && offset >= maxOffset }

    /** 指の移動量 [delta] (下向きが正) で見出しを動かし、使った分を返す。 */
    internal fun dispatch(delta: Float): Float {
        val new = (offset - delta).coerceIn(0f, maxOffset)
        val consumed = offset - new
        offset = new
        return consumed
    }

    /**
     * 上へのスクロールはまず見出しを畳み (一覧より先)、下へのスクロールは一覧が先頭に着いてから見出しを開く (一覧の後)。
     * 余りはさらに外側 (pull-to-refresh) へ回る。
     */
    internal val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f) Offset(0f, dispatch(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f) Offset(0f, dispatch(available.y)) else Offset.Zero
    }

    companion object {
        val Saver = androidx.compose.runtime.saveable.Saver<CollapsingHeaderState, Float>(
            save = { it.offset },
            restore = { CollapsingHeaderState(it) },
        )
    }
}

@Composable
fun rememberCollapsingHeaderState(): CollapsingHeaderState =
    rememberSaveable(saver = CollapsingHeaderState.Saver) { CollapsingHeaderState() }

/**
 * 上から [header] / [pinned] / [content] を並べ、縦スクロールで [header] だけを上へ畳むレイアウト (X のプロフィール風)。
 * [pinned] (タブ等) は畳まれた後も上端に残り、[content] (HorizontalPager + 一覧等) は残りの高さいっぱいを使う。
 *
 * [content] 内の一覧のスクロールは nested scroll で受け取る。[header] の上でのドラッグも同じように畳む / 開く
 * ([header] 自体はスクロールしないので、ここで拾う)。
 *
 * @param pinnedOverlapsContent `true` なら [content] を [pinned] の裏から始める ([pinned] を半透明・ぼかしにして、
 *   スクロールした一覧を透かす用)。その場合 [content] の一覧は先頭に [pinned] の高さ分の余白を取る。
 *   一覧が [header] の裏に入ることは無い (上へのスクロールではまず [header] が畳まれる) ので、重なるのは [pinned] だけ。
 * @param collapsedTop 畳み切ったときに [pinned] を置く上端からの位置 (上に重ねたトップバーの高さ)。[header] はこの分だけ
 *   畳まれずに残り、その裏に隠れる。
 */
@Composable
fun CollapsingHeaderLayout(
    state: CollapsingHeaderState,
    header: @Composable () -> Unit,
    pinned: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    pinnedOverlapsContent: Boolean = false,
    collapsedTop: Dp = 0.dp,
) {
    // 見出しの上でのドラッグ。自分では何も消費せず、nested scroll (state.connection とその外側) に任せる
    val headerDrag = rememberScrollableState { 0f }
    Layout(
        contents = listOf(header, pinned, content),
        modifier = modifier
            .clipToBounds()
            .nestedScroll(state.connection)
            .scrollable(headerDrag, Orientation.Vertical),
    ) { (headerMeasurables, pinnedMeasurables, contentMeasurables), constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val headerPlaceable = headerMeasurables.firstOrNull()?.measure(loose)
        val pinnedPlaceable = pinnedMeasurables.firstOrNull()?.measure(loose)
        val headerHeight = headerPlaceable?.height ?: 0
        val pinnedHeight = pinnedPlaceable?.height ?: 0
        val height = constraints.maxHeight
        val contentTop = if (pinnedOverlapsContent) 0 else pinnedHeight
        // 畳み切ったとき本体の上端は collapsedTop まで上がるので、その分を引いて下端を画面に収める
        val contentHeight = (height - contentTop - collapsedTop.roundToPx()).coerceAtLeast(0)
        val contentPlaceable = contentMeasurables.firstOrNull()
            ?.measure(constraints.copy(minHeight = contentHeight, maxHeight = contentHeight))
        val maxOffset = (headerHeight - collapsedTop.roundToPx()).coerceAtLeast(0).toFloat()
        state.updateMaxOffset(maxOffset)
        layout(constraints.maxWidth, height) {
            // 見出しの高さが縮んだ (補助情報の取得失敗等) 直後でも、はみ出さないよう丸める
            val offset = state.offset.coerceIn(0f, maxOffset).roundToInt()
            // 重なり順は後に置いたものが上: 本体 → 見出し → 残る部分
            contentPlaceable?.place(0, headerHeight + contentTop - offset)
            headerPlaceable?.place(0, -offset)
            pinnedPlaceable?.place(0, headerHeight - offset)
        }
    }
}
