package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.list.LoadMoreEffect

/**
 * ポストの集合。タイムライン・プロフィールの投稿一覧などで再利用する。
 * データ取得は行わず、末尾付近までスクロールされたら [onLoadMore] を呼ぶ。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostList(
    posts: List<Post>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    mediaHeight: Dp = PostCardDefaults.MediaHeight,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onUserClick: (User) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onMediaClick: (MediaAttachment) -> Unit = {},
    onVote: (suspend (Post, List<Int>) -> Boolean)? = null,
    header: (LazyListScope.() -> Unit)? = null,
) {
    LoadMoreEffect(state, onLoadMore, LoadMoreThreshold)

    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
    ) {
        header?.invoke(this)
        items(posts, key = { it.id }, contentType = { "post" }) { post ->
            PostCard(
                post = post,
                mediaHeight = mediaHeight,
                onUserClick = onUserClick,
                onPostClick = onPostClick,
                onMediaClick = onMediaClick,
                onVote = onVote,
            )
            HorizontalDivider()
        }
        if (isLoadingMore) {
            item(key = "loading_more", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
    }
}

private const val LoadMoreThreshold = 5
