package es.aria_on_the_planet.quasar.ui.follows

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailRefreshBox
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailTitle
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.tabs.ConnectedTab
import es.aria_on_the_planet.quasar.ui.component.tabs.ConnectedTabRow
import es.aria_on_the_planet.quasar.ui.component.user.UserList
import es.aria_on_the_planet.quasar.ui.feed.FeedState
import kotlinx.coroutines.launch

/**
 * フォロー / フォロワー一覧。上端の連結ボタンか左右スワイプ (HorizontalPager) でタブを切り替える。
 * 各タブは開いたときに初めて読み込む。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FollowListScreen(
    viewModel: FollowListViewModel,
    initialTab: FollowListTab,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = FollowListTab.entries
    val pagerState = rememberPagerState(initialPage = initialTab.ordinal) { tabs.size }
    val scope = rememberCoroutineScope()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(pagerState.targetPage) {
        viewModel.ensureLoaded(tabs[pagerState.targetPage])
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Symbols.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = {
                    state.profile?.let { profile ->
                        DetailTitle(
                            name = profile.user.displayName.ifBlank { profile.user.screenName },
                            emojis = profile.user.emojis,
                            subtitle = "@${profile.user.screenName}",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ConnectedTabRow(
                tabs = tabs.map { ConnectedTab(stringResource(it.label()), it.icon(), it.selectedIcon()) },
                selectedIndex = pagerState.targetPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index, animationSpec = spatialSpec) } },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                key = { tabs[it] },
            ) { page ->
                val tab = tabs[page]
                FollowListPage(
                    feed = state.feed(tab),
                    onRefresh = { viewModel.refresh(tab) },
                    onLoadMore = { viewModel.loadMore(tab) },
                    onUserClick = onUserClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FollowListPage(
    feed: FeedState<User>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onUserClick: (User) -> Unit,
) {
    DetailRefreshBox(
        // 初回の読み込みは一覧側に出すので、引っ張って更新したときだけ出す
        isRefreshing = feed.isRefreshing && feed.loaded,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
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
                                feed.error != null -> Text(stringResource(R.string.follow_list_error, feed.error))
                                // 一覧を隠しているアカウントも空で返る
                                else -> Text(
                                    stringResource(R.string.follow_list_empty),
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

private fun FollowListTab.label(): Int = when (this) {
    FollowListTab.Following -> R.string.follow_list_following
    FollowListTab.Followers -> R.string.follow_list_followers
}

private fun FollowListTab.icon(): ImageVector = when (this) {
    FollowListTab.Following -> Symbols.Outlined.PersonCheck
    FollowListTab.Followers -> Symbols.Outlined.Groups
}

private fun FollowListTab.selectedIcon(): ImageVector = when (this) {
    FollowListTab.Following -> Symbols.Filled.PersonCheck
    FollowListTab.Followers -> Symbols.Filled.Groups
}
