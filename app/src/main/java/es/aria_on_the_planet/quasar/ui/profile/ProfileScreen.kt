package es.aria_on_the_planet.quasar.ui.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import es.aria_on_the_planet.quasar.ui.component.blur.rememberSurfaceBlurStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.layout.CollapsingHeaderLayout
import es.aria_on_the_planet.quasar.ui.component.layout.rememberCollapsingHeaderState
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.post.OnPostAction
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.post.rememberPostActionHandler
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailLoading
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailMessage
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailTitle
import es.aria_on_the_planet.quasar.ui.component.tabs.ConnectedTabRow
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import kotlinx.coroutines.launch

/**
 * プロフィール画面。上からヘッダ / タブ (Posts・Media) / タブごとの投稿一覧 (HorizontalPager)。
 * 一覧を上へスクロールするとヘッダが畳まれ、タブは上端に残る ([CollapsingHeaderLayout])。
 * タブはボタンでも左右スワイプでも切り替わり、各タブは一覧とスクロール位置を別々に持つ。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
    onPostClick: (Post) -> Unit,
    onMediaClick: (MediaClick) -> Unit,
    onFollowingClick: () -> Unit,
    onFollowersClick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = state.profile.profile
    val feed = state.selectedFeed
    val tabs = ProfileTab.entries
    val pagerState = rememberPagerState { tabs.size }
    val headerState = rememberCollapsingHeaderState()
    val scope = rememberCoroutineScope()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val onVote: (suspend (Post, List<Int>) -> Boolean)? = if (viewModel.canInteract) viewModel::vote else null
    val onAction = rememberPostActionHandler(viewModel::performAction).takeIf { viewModel.canInteract }
    val tabLabels = tabs.map { stringResource(it.label()) }
    // ページごとの一覧の位置。タブの背景 (スクロールしたらぼかし) の判定にも使うのでここで持つ
    val listStates = tabs.associateWith { rememberLazyListState() }
    val hazeState = rememberHazeState()
    val tabsBlurStyle = rememberSurfaceBlurStyle()
    val density = LocalDensity.current
    var tabsHeight by remember { mutableStateOf(0.dp) }
    val currentList = listStates.getValue(tabs[pagerState.currentPage])
    val listScrolled by remember(currentList) {
        derivedStateOf { currentList.firstVisibleItemIndex > 0 || currentList.firstVisibleItemScrollOffset > 0 }
    }
    // 一覧がタブの裏に入っていない間は不透明 (今までどおり)、入ったらぼかしに切り替える
    val tabsSolidAlpha by animateFloatAsState(if (listScrolled) 0f else 1f, label = "tabsSolidAlpha")
    val surface = MaterialTheme.colorScheme.surface

    // スワイプでもボタンでも、行き先のタブを選択として扱う (初めて開くタブはここで読み込む)
    LaunchedEffect(pagerState.targetPage) {
        viewModel.selectTab(tabs[pagerState.targetPage])
    }

    DetailScaffold(
        onBack = onBack,
        // ヘッダが畳まれたらトップバーに名前を出す
        titleRevealed = headerState.isCollapsed,
        // タブを初めて開いたときの読み込みは一覧側に出すので、引っ張って更新したときだけ出す
        isRefreshing = profile != null && (state.profile.isLoading || (feed.isRefreshing && feed.loaded)),
        onRefresh = viewModel::refresh,
        revealedTitle = profile?.let { { ProfileTitle(it) } },
    ) {
        when {
            profile == null && state.profile.error != null ->
                DetailMessage(stringResource(R.string.profile_error, state.profile.error!!))

            profile == null -> DetailLoading()

            else -> CollapsingHeaderLayout(
                state = headerState,
                modifier = Modifier.fillMaxSize(),
                // 一覧をタブの裏まで描き、タブの背景でぼかす
                pinnedOverlapsContent = true,
                header = {
                    ProfileHeader(
                        profile = profile,
                        followersYouFollow = state.extras.followersYouFollow,
                        relationship = (state.button as? ProfileButtonState.Follow)?.relationship,
                        onFollowingClick = onFollowingClick,
                        onFollowersClick = onFollowersClick,
                        actions = {
                            ProfileActionButton(
                                state = state.button,
                                profile = profile,
                                onSetFollowing = viewModel::setFollowing,
                                // プロフィール編集は未実装
                                onEditProfile = {},
                            )
                        },
                    )
                },
                pinned = {
                    ConnectedTabRow(
                        labels = tabLabels,
                        selectedIndex = pagerState.targetPage,
                        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index, animationSpec = spatialSpec) } },
                        modifier = Modifier
                            .onSizeChanged { tabsHeight = with(density) { it.height.toDp() } }
                            .hazeBlur(input = HazeInput.Sources(hazeState), style = tabsBlurStyle)
                            .drawBehind {
                                // ぼかしの上、ボタンの下に不透明な面を重ね、スクロール量で消す (alpha は描画時に読む)
                                drawRect(surface.copy(alpha = tabsSolidAlpha))
                                // 上端は不透明なトップバーの色、高さの半分で透明になるようにして、トップバーとの境目を馴染ませる
                                drawRect(Brush.verticalGradient(0f to surface, 0.5f to Color.Transparent))
                            },
                    )
                },
                content = {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState),
                        key = { tabs[it] },
                    ) { page ->
                        val tab = tabs[page]
                        ProfileTabPage(
                            tab = tab,
                            listState = listStates.getValue(tab),
                            topPadding = tabsHeight,
                            feed = if (tab == ProfileTab.Media) state.media else state.posts,
                            pinnedPosts = if (tab == ProfileTab.Posts) state.extras.pinnedPosts else emptyList(),
                            onLoadMore = { viewModel.loadMore(tab) },
                            onUserClick = onUserClick,
                            onPostClick = onPostClick,
                            onMediaClick = onMediaClick,
                            onVote = onVote,
                            onAction = onAction,
                        )
                    }
                },
            )
        }
    }
}

/** タブ 1 つ分の投稿一覧。Media は `only_media` で絞り込んだポストを同じ形で出すだけ。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileTabPage(
    tab: ProfileTab,
    listState: LazyListState,
    topPadding: Dp,
    feed: PostFeedState,
    pinnedPosts: List<Post>,
    onLoadMore: () -> Unit,
    onUserClick: (User) -> Unit,
    onPostClick: (Post) -> Unit,
    onMediaClick: (MediaClick) -> Unit,
    onVote: (suspend (Post, List<Int>) -> Boolean)?,
    onAction: OnPostAction?,
) {
    PostList(
        items = feed.items,
        modifier = Modifier.fillMaxSize(),
        state = listState,
        // 先頭はタブの裏に隠れないようタブの高さ分あける
        contentPadding = PaddingValues(top = topPadding),
        isLoadingMore = feed.isLoadingMore,
        onLoadMore = onLoadMore,
        onUserClick = onUserClick,
        onPostClick = onPostClick,
        onMediaClick = onMediaClick,
        onVote = onVote,
        onAction = onAction,
        header = {
            // 通常の一覧にも同じポストが含まれうるので、ピン留めは 1 アイテムにまとめてキー衝突を避ける
            if (pinnedPosts.isNotEmpty()) {
                item(key = "pinned_posts", contentType = "pinned_posts") {
                    PinnedPosts(
                        posts = pinnedPosts,
                        onUserClick = onUserClick,
                        onPostClick = onPostClick,
                        onMediaClick = onMediaClick,
                        onVote = onVote,
                        onAction = onAction,
                    )
                }
            }
            if (feed.items.isEmpty()) {
                item(key = "feed_status", contentType = "status") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            !feed.loaded || feed.isRefreshing -> LoadingIndicator()
                            feed.error != null -> Text(stringResource(R.string.timeline_error, feed.error))
                            else -> Text(
                                stringResource(if (tab == ProfileTab.Media) R.string.profile_no_media else R.string.profile_no_posts),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}

private fun ProfileTab.label(): Int = when (this) {
    ProfileTab.Posts -> R.string.profile_tab_posts
    ProfileTab.Media -> R.string.profile_tab_media
}

@Composable
private fun ProfileTitle(profile: Profile) {
    val locale = LocalConfiguration.current.locales[0]
    DetailTitle(
        name = profile.user.displayName.ifBlank { profile.user.screenName },
        emojis = profile.user.emojis,
        subtitle = stringResource(R.string.profile_posts_count, formatCount(profile.postsCount, locale)),
    )
}
