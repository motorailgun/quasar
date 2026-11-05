package es.aria_on_the_planet.quasar.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Hashtag
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.list.ListStatus
import es.aria_on_the_planet.quasar.ui.component.list.LoadMoreEffect
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.post.PostCallbacks
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.post.rememberPostActionHandler
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailRefreshBox
import es.aria_on_the_planet.quasar.ui.component.scaffold.backIcon
import es.aria_on_the_planet.quasar.ui.component.scaffold.backLabel
import es.aria_on_the_planet.quasar.ui.component.tabs.ConnectedTab
import es.aria_on_the_planet.quasar.ui.component.tabs.ConnectedTabRow
import es.aria_on_the_planet.quasar.ui.component.user.UserFeedList
import es.aria_on_the_planet.quasar.ui.feed.FeedState
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import kotlinx.coroutines.launch

/**
 * 検索。トップバーが検索欄で、確定 (キーボードの検索) で検索する。結果はアカウント / 投稿 / ハッシュタグのタブ
 * ([ConnectedTabRow] + [HorizontalPager]、左右スワイプでも切り替え)。各タブは開いたときに読む。
 * 初めて開いたとき (まだ検索していない) は検索欄にフォーカスしてキーボードを出す。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
    onPostClick: (statusId: String) -> Unit,
    onMediaClick: (MediaClick) -> Unit,
    onHashtagClick: (String) -> Unit,
    onReply: ((Post) -> Unit)?,
    onQuote: ((Post) -> Unit)?,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = SearchTab.entries
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val field = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (state.query == null) focusRequester.requestFocus()
    }
    // 検索し直したら、見ているタブを読む
    LaunchedEffect(state.query, pagerState.targetPage) {
        viewModel.ensureLoaded(tabs[pagerState.targetPage])
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(backIcon(), backLabel())
                    }
                },
                title = {
                    BasicTextField(
                        state = field,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = {
                            viewModel.search(field.text.toString())
                            focusManager.clearFocus()
                        },
                        decorator = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (field.text.isEmpty()) {
                                    Text(
                                        stringResource(R.string.search_hint),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                },
                actions = {
                    if (field.text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                field.clearText()
                                focusRequester.requestFocus()
                            },
                        ) {
                            Icon(Symbols.Outlined.Close, stringResource(R.string.search_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.query == null) return@Scaffold
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
                when (val tab = tabs[page]) {
                    SearchTab.Accounts -> UserFeedList(
                        feed = state.accounts,
                        onRefresh = { viewModel.refresh(tab) },
                        onLoadMore = { viewModel.loadMore(tab) },
                        onUserClick = onUserClick,
                        empty = stringResource(R.string.search_empty),
                    )
                    SearchTab.Posts -> PostResults(
                        viewModel = viewModel,
                        feed = state.posts,
                        callbacks = PostCallbacks(
                            onUserClick = onUserClick,
                            onPostClick = onPostClick,
                            onMediaClick = onMediaClick,
                            onVote = viewModel::vote,
                            onAction = rememberPostActionHandler(viewModel::performAction),
                            onReply = onReply,
                            onQuote = onQuote,
                            onHashtagClick = onHashtagClick,
                        ),
                    )
                    SearchTab.Hashtags -> HashtagResults(
                        feed = state.hashtags,
                        onRefresh = { viewModel.refresh(tab) },
                        onLoadMore = { viewModel.loadMore(tab) },
                        onHashtagClick = onHashtagClick,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PostResults(viewModel: SearchViewModel, feed: PostFeedState, callbacks: PostCallbacks) {
    DetailRefreshBox(
        isRefreshing = feed.isRefreshing && feed.loaded,
        onRefresh = { viewModel.refresh(SearchTab.Posts) },
        modifier = Modifier.fillMaxSize(),
    ) {
        PostList(
            items = feed.items,
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            isLoadingMore = feed.isLoadingMore,
            onLoadMore = { viewModel.loadMore(SearchTab.Posts) },
            callbacks = callbacks,
            header = {
                if (feed.items.isEmpty()) {
                    item(key = "status", contentType = "status") { SearchStatus(feed) }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HashtagResults(
    feed: FeedState<Hashtag>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onHashtagClick: (String) -> Unit,
) {
    DetailRefreshBox(
        isRefreshing = feed.isRefreshing && feed.loaded,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        val listState = rememberLazyListState()
        LoadMoreEffect(listState, onLoadMore)
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            if (feed.items.isEmpty()) {
                item(key = "status", contentType = "status") { SearchStatus(feed) }
            }
            items(feed.items, key = { it.name.lowercase() }, contentType = { "hashtag" }) { tag ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .clickable { onHashtagClick(tag.name) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Symbols.Outlined.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text("#${tag.name}", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (feed.isLoadingMore) {
                item(key = "loading", contentType = "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
            }
        }
    }
}

@Composable
private fun SearchStatus(feed: FeedState<*>) {
    ListStatus(
        loading = !feed.loaded || feed.isRefreshing,
        error = feed.error?.let { stringResource(R.string.search_error, it) },
        empty = stringResource(R.string.search_empty),
    )
}

private fun SearchTab.label(): Int = when (this) {
    SearchTab.Accounts -> R.string.search_tab_accounts
    SearchTab.Posts -> R.string.search_tab_posts
    SearchTab.Hashtags -> R.string.search_tab_hashtags
}

private fun SearchTab.icon(): ImageVector = when (this) {
    SearchTab.Accounts -> Symbols.Outlined.Person
    SearchTab.Posts -> Symbols.Outlined.Article
    SearchTab.Hashtags -> Symbols.Outlined.Tag
}

private fun SearchTab.selectedIcon(): ImageVector = when (this) {
    SearchTab.Accounts -> Symbols.Filled.Person
    SearchTab.Posts -> Symbols.Filled.Article
    SearchTab.Hashtags -> Symbols.Filled.Tag
}
