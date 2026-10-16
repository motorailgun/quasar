package es.aria_on_the_planet.quasar.ui.component.list

import androidx.compose.foundation.lazy.LazyListState

/**
 * 一覧の先頭へスクロールする (選択中のタブをもう一度押したとき等)。遠くまで読み進めているときは、全部を
 * アニメーションさせると長くかかるので、先頭の近くまで飛んでから残りを動かす。
 */
suspend fun LazyListState.scrollToTop() {
    if (firstVisibleItemIndex > ScrollToTopJumpIndex) scrollToItem(ScrollToTopJumpIndex)
    animateScrollToItem(0)
}

private const val ScrollToTopJumpIndex = 10
