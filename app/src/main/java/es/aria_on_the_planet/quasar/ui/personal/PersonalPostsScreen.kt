package es.aria_on_the_planet.quasar.ui.personal

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.post.PostCallbacks
import es.aria_on_the_planet.quasar.ui.component.post.PostFeedList
import es.aria_on_the_planet.quasar.ui.component.post.rememberPostActionHandler
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold

/** お気に入り / ブックマーク / 非公開の返信の一覧。トップバー + [PostFeedList]。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PersonalPostsScreen(
    viewModel: PersonalPostsViewModel,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
    onPostClick: (statusId: String) -> Unit,
    onMediaClick: (MediaClick) -> Unit,
    onHashtagClick: (String) -> Unit,
    onReply: ((Post) -> Unit)?,
    onQuote: ((Post) -> Unit)?,
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
        onQuote = onQuote,
        onHashtagClick = onHashtagClick,
    )

    DetailScaffold(
        onBack = onBack,
        listState = listState,
        // 初回の読み込みは一覧側に出すので、引っ張って更新したときだけ出す
        isRefreshing = feed.isRefreshing && feed.loaded,
        onRefresh = viewModel::refresh,
        title = {
            Text(stringResource(viewModel.list.title()), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    ) {
        PostFeedList(
            feed = feed,
            empty = stringResource(viewModel.list.empty()),
            errorFormat = R.string.personal_posts_error,
            onLoadMore = viewModel::loadMore,
            callbacks = callbacks,
            state = listState,
        )
    }
}

fun PersonalPostList.title(): Int = when (this) {
    PersonalPostList.Favourites -> R.string.profile_tab_favourites
    PersonalPostList.Bookmarks -> R.string.profile_tab_bookmarks
    PersonalPostList.PrivateMentions -> R.string.private_mentions
}

private fun PersonalPostList.empty(): Int = when (this) {
    PersonalPostList.Favourites -> R.string.profile_no_favourites
    PersonalPostList.Bookmarks -> R.string.profile_no_bookmarks
    PersonalPostList.PrivateMentions -> R.string.private_mentions_empty
}
