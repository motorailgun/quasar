package es.aria_on_the_planet.quasar.ui.reactions

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.NotificationType
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.post.OnPostAction
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.post.rememberPostActionHandler
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailTitle
import es.aria_on_the_planet.quasar.ui.component.user.UserSummary
import es.aria_on_the_planet.quasar.ui.feed.TimelineItem

/**
 * 通知のユーザー単位のまとめ (「〇〇 favourited 3 of your posts」) を開いた画面。
 * トップバー (ユーザー名 + 「あなたの投稿 N 件をお気に入り」) + 対象のポストの [PostList] + 末尾にそのユーザーの要約。
 */
@Composable
fun AccountReactionsScreen(
    viewModel: AccountReactionsViewModel,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
    onPostClick: (Post) -> Unit,
    onMediaClick: (MediaClick) -> Unit,
    onHashtagClick: (String) -> Unit,
    onReply: ((Post) -> Unit)?,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountReactionsContent(
        state = state,
        type = viewModel.type,
        postCount = viewModel.postCount,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onUserClick = onUserClick,
        onPostClick = onPostClick,
        onMediaClick = onMediaClick,
        onHashtagClick = onHashtagClick,
        onReply = onReply,
        onVote = viewModel::vote,
        onAction = rememberPostActionHandler(viewModel::performAction),
    )
}

/**
 * [AccountReactionsScreen] の中身 (状態を受けて描くだけ。デバッグ用ギャラリーからも使う)。
 *
 * @param postCount まとめられたポストの数 (読み込み前のタイトル用)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountReactionsContent(
    state: AccountReactionsState,
    type: NotificationType,
    postCount: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUserClick: (User) -> Unit,
    onPostClick: (Post) -> Unit,
    onMediaClick: (MediaClick) -> Unit,
    onHashtagClick: (String) -> Unit,
    onReply: ((Post) -> Unit)? = null,
    onVote: (suspend (Post, List<Int>) -> Boolean)? = null,
    onAction: OnPostAction? = null,
) {
    val listState = rememberLazyListState()
    val items = remember(state.posts) { state.posts.map(TimelineItem::PostItem) }

    DetailScaffold(
        onBack = onBack,
        listState = listState,
        // 初回の読み込みは一覧側に出すので、引っ張って更新したときだけ出す
        isRefreshing = state.isRefreshing && state.loaded,
        onRefresh = onRefresh,
        title = {
            val subtitle = type.postsTitle(if (state.loaded) state.posts.size else postCount)
            val account = state.account
            if (account != null) {
                DetailTitle(account.displayName.ifBlank { account.screenName }, account.emojis, subtitle)
            } else {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
    ) {
        PostList(
            items = items,
            modifier = Modifier.fillMaxSize(),
            state = listState,
            onUserClick = onUserClick,
            onPostClick = onPostClick,
            onMediaClick = onMediaClick,
            onVote = onVote,
            onAction = onAction,
            onReply = onReply,
            onHashtagClick = onHashtagClick,
            header = {
                if (state.posts.isEmpty()) {
                    item(key = "status", contentType = "status") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                !state.loaded || state.isRefreshing -> LoadingIndicator()
                                state.error != null -> Text(stringResource(R.string.hashtag_error, state.error.orEmpty()))
                                else -> Text(
                                    stringResource(R.string.account_reactions_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            footer = {
                state.account?.let { account ->
                    item(key = "account", contentType = "account") {
                        UserSummary(user = account, onClick = { onUserClick(account) })
                    }
                }
            },
        )
    }
}

/** トップバーの補足 (「Favourited N of your posts」)。 */
@Composable
private fun NotificationType.postsTitle(posts: Int): String = when (this) {
    NotificationType.Reblog -> pluralStringResource(R.plurals.account_reactions_reblog_title, posts, posts)
    else -> pluralStringResource(R.plurals.account_reactions_favourite_title, posts, posts)
}
