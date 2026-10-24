package es.aria_on_the_planet.quasar.ui.component.user

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailRefreshBox
import es.aria_on_the_planet.quasar.ui.feed.FeedState

/**
 * ページングするユーザーの一覧 ([UserList]) に、pull-to-refresh と、空のときの表示 (読み込み中・失敗・空) を付けたもの。
 * フォロー / フォロワー一覧、ポストのブースト / お気に入りの一覧のタブ 1 つ分。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserFeedList(
    feed: FeedState<User>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onUserClick: (User) -> Unit,
    modifier: Modifier = Modifier,
) {
    DetailRefreshBox(
        // 初回の読み込みは一覧側に出すので、引っ張って更新したときだけ出す
        isRefreshing = feed.isRefreshing && feed.loaded,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        UserList(
            users = feed.items,
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            isLoadingMore = feed.isLoadingMore,
            onLoadMore = onLoadMore,
            onUserClick = onUserClick,
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
                                feed.error != null -> Text(stringResource(R.string.user_list_error, feed.error))
                                // 一覧を隠しているアカウントも空で返る
                                else -> Text(
                                    stringResource(R.string.user_list_empty),
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
