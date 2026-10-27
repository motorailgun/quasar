package es.aria_on_the_planet.quasar.ui.hashtag

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.post.PostList
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
    val onVote: suspend (Post, List<Int>) -> Boolean = viewModel::vote
    val onAction = rememberPostActionHandler(viewModel::performAction)

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
            onUserClick = onUserClick,
            onPostClick = onPostClick,
            onMediaClick = onMediaClick,
            onVote = onVote,
            onAction = onAction,
            onReply = onReply,
            onHashtagClick = onHashtagClick,
            header = {
                if (feed.items.isEmpty()) {
                    item(key = "status", contentType = "status") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                !feed.loaded || feed.isRefreshing -> LoadingIndicator()
                                feed.error != null -> Text(stringResource(R.string.hashtag_error, feed.error.orEmpty()))
                                else -> Text(
                                    stringResource(R.string.hashtag_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}
