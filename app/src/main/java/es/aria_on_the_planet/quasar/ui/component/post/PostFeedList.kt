package es.aria_on_the_planet.quasar.ui.component.post

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import es.aria_on_the_planet.quasar.ui.component.list.ListStatus
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState

/**
 * ページングするポストの一覧 ([PostList]) に、空のときの表示 (読み込み中・失敗・空) を付けたもの。
 * ハッシュタグ・お気に入り等の一覧、プロフィール・検索のタブ 1 つ分。
 * [UserFeedList][es.aria_on_the_planet.quasar.ui.component.user.UserFeedList] と違い pull-to-refresh は含まない
 * (多くの画面は `DetailScaffold` が持つ)。
 *
 * @param empty 空のときの文言。
 * @param errorFormat 失敗のときの文言 (`%1$s` に失敗の内容)。
 * @param header 空のときの表示より上に置くアイテム (ピン留め等)。
 */
@Composable
fun PostFeedList(
    feed: PostFeedState,
    empty: String,
    @StringRes errorFormat: Int,
    onLoadMore: () -> Unit,
    callbacks: PostCallbacks,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    header: (LazyListScope.() -> Unit)? = null,
) {
    PostList(
        items = feed.items,
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = contentPadding,
        isLoadingMore = feed.isLoadingMore,
        onLoadMore = onLoadMore,
        callbacks = callbacks,
        header = {
            header?.invoke(this)
            if (feed.items.isEmpty()) {
                item(key = "feed_status", contentType = "status") {
                    ListStatus(
                        loading = !feed.loaded || feed.isRefreshing,
                        error = feed.error?.let { stringResource(errorFormat, it) },
                        empty = empty,
                    )
                }
            }
        },
    )
}
