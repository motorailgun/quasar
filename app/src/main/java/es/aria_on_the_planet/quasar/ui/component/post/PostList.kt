package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.ui.feed.TimelineItem
import es.aria_on_the_planet.quasar.ui.component.list.LoadMoreEffect
import es.aria_on_the_planet.quasar.ui.component.list.StickToTopEffect

/**
 * ポストの集合。タイムライン・プロフィールの投稿一覧などで再利用する。
 * 要素は [TimelineItem] (ポストか、未取得の区間の読み込みボタン)。
 * ポストへの操作は [callbacks] ([PostCallbacks]) でまとめて受け、各 [PostCard] に渡す。
 * データ取得は行わず、末尾付近までスクロールされたら [onLoadMore]、読み込みボタンで [onGapClick] を呼ぶ。
 * [header] / [footer] は一覧の先頭 / 末尾 (追加読み込みの表示より後) に置くアイテム。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostList(
    items: List<TimelineItem>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    mediaHeight: Dp = PostCardDefaults.MediaHeight,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    callbacks: PostCallbacks = PostCallbacks.None,
    onGapClick: (TimelineItem.Gap) -> Unit = {},
    stickToTop: Boolean = true,
    header: (LazyListScope.() -> Unit)? = null,
    footer: (LazyListScope.() -> Unit)? = null,
) {
    LoadMoreEffect(state, onLoadMore, LoadMoreThreshold)
    // 先頭にいるときにストリーミングの新着が入ったら見せる (stickToTop = false なら位置を保つ)
    StickToTopEffect(state, items.firstOrNull()?.key, enabled = stickToTop)

    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
    ) {
        header?.invoke(this)
        items(items, key = { it.key }, contentType = { it::class }) { item ->
            Column(Modifier.animateItem()) {
                when (item) {
                    is TimelineItem.PostItem -> PostCard(
                        post = item.post,
                        callbacks = callbacks,
                        mediaHeight = mediaHeight,
                    )
                    is TimelineItem.Gap -> GapRow(item, onClick = { onGapClick(item) })
                }
                HorizontalDivider()
            }
        }
        if (isLoadingMore) {
            item(key = "loading_more", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
        footer?.invoke(this)
    }
}

private const val LoadMoreThreshold = 5

/** 未取得の区間の読み込みボタン。押すたびに上側から 1 ページ分、下方向へ読み込む (なので矢印は下向き)。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GapRow(gap: TimelineItem.Gap, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (gap.isLoading) {
            LoadingIndicator(Modifier.size(40.dp))
        } else {
            FilledTonalButton(onClick = onClick) {
                Icon(Symbols.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.timeline_load_gap))
            }
        }
    }
}
