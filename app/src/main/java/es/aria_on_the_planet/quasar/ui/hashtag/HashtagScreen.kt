package es.aria_on_the_planet.quasar.ui.hashtag

import es.aria_on_the_planet.quasar.ui.component.list.ListStatus
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.post.PostCallbacks
import es.aria_on_the_planet.quasar.ui.component.post.rememberPostActionHandler
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold

/** ハッシュタグのタイムライン。トップバー (`#名前`) + [PostList]。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HashtagScreen(
    viewModel: HashtagViewModel,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
    onPostClick: (Post) -> Unit,
    onMediaClick: (MediaClick) -> Unit,
    onHashtagClick: (String) -> Unit,
    onReply: ((Post) -> Unit)?,
) {
    val feed by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val callbacks = PostCallbacks(
        onUserClick = onUserClick,
        onPostClick = onPostClick,
        onMediaClick = onMediaClick,
        onVote = viewModel::vote,
        onAction = rememberPostActionHandler(viewModel::performAction),
        onReply = onReply,
        onHashtagClick = onHashtagClick,
    )

    DetailScaffold(
        onBack = onBack,
        listState = listState,
        // 初回の読み込みは一覧側に出すので、引っ張って更新したときだけ出す
        isRefreshing = feed.isRefreshing && feed.loaded,
        onRefresh = viewModel::refresh,
        title = {
            Text(
                stringResource(R.string.hashtag_title, viewModel.hashtag),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        PostList(
            items = feed.items,
            modifier = Modifier.fillMaxSize(),
            state = listState,
            isLoadingMore = feed.isLoadingMore,
            onLoadMore = viewModel::loadMore,
            callbacks = callbacks,
            header = {
                if (feed.items.isEmpty()) {
                    item(key = "status", contentType = "status") {
                        ListStatus(
                            loading = !feed.loaded || feed.isRefreshing,
                            error = feed.error?.let { stringResource(R.string.hashtag_error, it) },
                            empty = stringResource(R.string.hashtag_empty),
                        )
                    }
                }
            },
        )
    }
}
