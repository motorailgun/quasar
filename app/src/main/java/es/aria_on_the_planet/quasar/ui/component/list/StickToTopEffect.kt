package es.aria_on_the_planet.quasar.ui.component.list

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * 一覧の先頭に新しい項目が入ったとき、直前まで先頭にいたなら先頭に留まって新着を見せる。
 * 読み進めている途中 (先頭以外) なら何もしない (LazyColumn がキーで位置を保つので表示はずれない)。
 *
 * LazyColumn は先頭に足された項目があっても元の先頭の項目 (キー) の位置を保つので、そのままだと一覧が 1 件分
 * ずれる。レイアウトの後でスクロールして戻すと「ずれる → 戻る」の 2 段の動きになるため、次のレイアウトの前
 * ([SideEffect] はコンポジションの反映直後・レイアウト前に走る) に [LazyListState.requestScrollToItem] で
 * 先頭を指定し、ずれ自体を起こさない。新着は `animateItem` で現れ、下の項目は押し下げられる。
 *
 * @param firstKey 一覧の先頭項目のキー。変わったら新着が入ったとみなす。
 * @param enabled `false` なら、その変化では先頭に留まらない (まとめて取った新着を、読んでいた位置を保って上に入れる)。
 *   [firstKey] と同じ状態から読む (別々に変わると、どの変化に当てるかがずれる)。
 */
@Composable
fun StickToTopEffect(state: LazyListState, firstKey: Any?, enabled: Boolean = true) {
    val last = remember(state) { LastKey(firstKey) }
    SideEffect {
        if (last.key == firstKey) return@SideEffect
        last.key = firstKey
        if (!enabled) return@SideEffect
        // まだ前のレイアウトのままなので、直前に先頭にいたかが読める。指で動かしている最中は邪魔しない
        val atTop = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
        if (atTop && !state.isScrollInProgress) state.requestScrollToItem(0)
    }
}

private class LastKey(var key: Any?)
