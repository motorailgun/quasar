package es.aria_on_the_planet.quasar.ui.component.list

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * 一覧の先頭に新しい項目が入ったとき、直前まで先頭にいたなら先頭へスクロールして新着を見せる。
 * 読み進めている途中 (先頭以外) なら何もしない (LazyColumn がキーで位置を保つので表示はずれない)。
 *
 * @param firstKey 一覧の先頭項目のキー。変わったら新着が入ったとみなす。
 */
@Composable
fun StickToTopEffect(state: LazyListState, firstKey: Any?) {
    val atTop by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0 }
    }
    // 先頭キーが変わったコンポジションの時点では、まだ新しい一覧でのレイアウト前なので、直前の位置が読める
    val wasAtTop = remember(firstKey) { atTop }
    LaunchedEffect(state, firstKey) {
        if (wasAtTop && state.firstVisibleItemIndex != 0) state.animateScrollToItem(0)
    }
}
