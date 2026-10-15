package es.aria_on_the_planet.quasar.ui.component.list

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * [state] の末尾から [threshold] 件以内が表示されたら [onLoadMore] を呼ぶ。
 * 末尾付近にいる間は項目数が増えるたびに再判定する (1 ページが画面に収まる場合も続きを読む)。
 */
@Composable
fun LoadMoreEffect(state: LazyListState, onLoadMore: () -> Unit, threshold: Int = 5) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, threshold) {
        snapshotFlow {
            val info = state.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearEnd = info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - threshold
            if (nearEnd) info.totalItemsCount else -1
        }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { currentOnLoadMore() }
    }
}
