package es.aria_on_the_planet.quasar.ui.reactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.tabs.ConnectedTab
import es.aria_on_the_planet.quasar.ui.component.tabs.ConnectedTabRow
import es.aria_on_the_planet.quasar.ui.component.user.UserFeedList
import kotlinx.coroutines.launch

/**
 * ポストをブースト / お気に入りにしたユーザーの一覧。上端の連結ボタンか左右スワイプ (HorizontalPager) でタブを
 * 切り替える。各タブは開いたときに初めて読み込む。
 */
@Composable
fun PostReactionsScreen(
    viewModel: PostReactionsViewModel,
    initialTab: PostReactionsTab,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PostReactionsContent(
        state = state,
        initialTab = initialTab,
        onTabShown = viewModel::ensureLoaded,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onBack = onBack,
        onUserClick = onUserClick,
    )
}

/** [PostReactionsScreen] の中身 (ViewModel を持たない。デバッグ用ギャラリーからも使う)。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostReactionsContent(
    state: PostReactionsState,
    initialTab: PostReactionsTab,
    onTabShown: (PostReactionsTab) -> Unit,
    onRefresh: (PostReactionsTab) -> Unit,
    onLoadMore: (PostReactionsTab) -> Unit,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
) {
    val tabs = PostReactionsTab.entries
    val pagerState = rememberPagerState(initialPage = initialTab.ordinal) { tabs.size }
    val scope = rememberCoroutineScope()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(pagerState.targetPage) {
        onTabShown(tabs[pagerState.targetPage])
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Symbols.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.post_reactions_title)) },
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
                UserFeedList(
                    feed = state.feed(tab),
                    onRefresh = { onRefresh(tab) },
                    onLoadMore = { onLoadMore(tab) },
                    onUserClick = onUserClick,
                )
            }
        }
    }
}

private fun PostReactionsTab.label(): Int = when (this) {
    PostReactionsTab.Boosts -> R.string.post_boosts
    PostReactionsTab.Favourites -> R.string.post_reactions
}

private fun PostReactionsTab.icon(): ImageVector = when (this) {
    PostReactionsTab.Boosts -> Symbols.Outlined.Repeat
    PostReactionsTab.Favourites -> Symbols.Outlined.Star
}

private fun PostReactionsTab.selectedIcon(): ImageVector = when (this) {
    PostReactionsTab.Boosts -> Symbols.Filled.Repeat
    PostReactionsTab.Favourites -> Symbols.Filled.Star
}
