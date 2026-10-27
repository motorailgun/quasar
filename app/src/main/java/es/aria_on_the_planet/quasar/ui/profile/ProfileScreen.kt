package es.aria_on_the_planet.quasar.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.ui.component.dialog.LocalActionConfirmer
import kotlinx.coroutines.CoroutineStart
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
import es.aria_on_the_planet.quasar.ui.component.blur.surfaceBlur
import es.aria_on_the_planet.quasar.ui.component.blur.rememberSurfaceBlurStyle
import dev.chrisbanes.haze.rememberHazeState
import es.aria_on_the_planet.quasar.ui.component.blur.LocalSurfaceBlurEnabled
import es.aria_on_the_planet.quasar.ui.component.blur.surfaceBlurSource
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import es.aria_on_the_planet.quasar.ui.component.list.scrollToTop
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.post.OnPostAction
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.post.rememberPostActionHandler
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailLoading
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailMessage
import es.aria_on_the_planet.quasar.ui.component.scaffold.OverlayDetailScaffold
import androidx.compose.runtime.mutableIntStateOf
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailTitle
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.tabs.ConnectedTab
import es.aria_on_the_planet.quasar.ui.component.tabs.ConnectedTabRow
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import es.aria_on_the_planet.quasar.ui.mediaviewer.MediaViewerArgs
import kotlinx.coroutines.launch

/**
 * プロフィール画面。上からヘッダ / タブ (Posts・Media、自分なら Favourites・Bookmarks も) / タブごとの投稿一覧 (HorizontalPager)。
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
    onOpenImage: (MediaViewerArgs) -> Unit,
    onReply: ((Post) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSelectTab = viewModel::selectTab,
        onLoadMore = viewModel::loadMore,
        onSetFollowing = viewModel::setFollowing,
        onAnswerFollowRequest = viewModel::answerFollowRequest,
        onUserClick = onUserClick,
        onPostClick = onPostClick,
        onMediaClick = onMediaClick,
        onFollowingClick = onFollowingClick,
        onFollowersClick = onFollowersClick,
        onOpenImage = onOpenImage,
        onReply = onReply,
        onHashtagClick = onHashtagClick,
        onVote = if (viewModel.canInteract) viewModel::vote else null,
        onAction = rememberPostActionHandler(viewModel::performAction).takeIf { viewModel.canInteract },
    )
}

/** プロフィール画面の中身。ViewModel を通さずに状態とコールバックだけで描く (デバッグ用ギャラリーからも使う)。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileContent(
    state: ProfileScreenState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (ProfileTab) -> Unit,
    onLoadMore: (ProfileTab) -> Unit,
    onSetFollowing: (Boolean) -> Unit,
    onAnswerFollowRequest: (Boolean) -> Unit,
    onUserClick: (User) -> Unit,
    onPostClick: (Post) -> Unit,
    onMediaClick: (MediaClick) -> Unit,
    onFollowingClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onOpenImage: (MediaViewerArgs) -> Unit,
    onReply: ((Post) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    onVote: (suspend (Post, List<Int>) -> Boolean)? = null,
    onAction: OnPostAction? = null,
) {
    val profile = state.profile.profile
    val feed = state.selectedFeed
    val tabs = state.tabs
    val pagerState = rememberPagerState { tabs.size }
    val headerState = rememberCollapsingHeaderState()
    val scope = rememberCoroutineScope()
    val confirmer = LocalActionConfirmer.current
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val tabItems = tabs.map { ConnectedTab(stringResource(it.label()), it.icon(), it.selectedIcon()) }
    // ページごとの一覧の位置。タブの背景 (スクロールしたらぼかし) の判定にも使うのでここで持つ
    // タブの数は自分かどうかが分かると変わるので、出しうる全タブ分を持つ
    val listStates = ProfileTab.entries.associateWith { rememberLazyListState() }
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
        onSelectTab(tabs[pagerState.targetPage])
    }

    // ヘッダ画像の下端 (ヘッダの上端から、px)。ここがトップバーの下端を過ぎたらバーを不透明にする
    var imageBottom by remember { mutableIntStateOf(0) }
    val barFadePx = with(density) { BarFadeDistance.toPx() }

    // トップバーはヘッダ画像の上に透明で重ね、画像が隠れるにつれて不透明にする
    OverlayDetailScaffold(
        onBack = onBack,
        // ヘッダが畳まれたらトップバーに名前を出す
        titleRevealed = headerState.isCollapsed,
        barAlpha = { barBottom ->
            if (profile == null) {
                1f
            } else {
                // 画像の下端がバーの下端より barFadePx 上に来た時点で不透明
                val remaining = imageBottom - headerState.offset - barBottom
                (1f - remaining / barFadePx).coerceIn(0f, 1f)
            }
        },
        // タブを初めて開いたときの読み込みは一覧側に出すので、引っ張って更新したときだけ出す
        isRefreshing = profile != null && (state.profile.isRefreshing || (feed.isRefreshing && feed.loaded)),
        onRefresh = onRefresh,
        revealedTitle = profile?.let { { ProfileTitle(it) } },
    ) { scaffoldBarHeight ->
        when {
            profile == null && state.profile.error != null ->
                Box(Modifier.padding(top = scaffoldBarHeight)) {
                    DetailMessage(stringResource(R.string.profile_error, state.profile.error!!))
                }

            profile == null -> Box(Modifier.padding(top = scaffoldBarHeight)) { DetailLoading() }

            else -> CollapsingHeaderLayout(
                state = headerState,
                modifier = Modifier.fillMaxSize(),
                // 一覧をタブの裏まで描き、タブの背景でぼかす
                pinnedOverlapsContent = true,
                // 畳み切ってもタブはトップバーの下に残す
                collapsedTop = scaffoldBarHeight,
                header = {
                    ProfileHeader(
                        profile = profile,
                        followersYouFollow = state.extras.followersYouFollow,
                        relationship = (state.button as? ProfileButtonState.Follow)?.relationship,
                        onFollowingClick = onFollowingClick,
                        onFollowersClick = onFollowersClick,
                        onOpenImage = onOpenImage,
                        onUserClick = onUserClick,
                        topBarHeight = scaffoldBarHeight,
                        onImageBottomChanged = { imageBottom = it },
                        followRequest = {
                            val follow = state.button as? ProfileButtonState.Follow
                            val motion = MaterialTheme.motionScheme
                            AnimatedVisibility(
                                visible = follow?.relationship?.requestedBy == true,
                                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
                            ) {
                                FollowRequestCard(
                                    user = profile.user,
                                    answering = follow?.answering,
                                    onAnswer = { accept ->
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            // 拒否は取り消せない (相手に申請し直してもらうしかない) ので、設定に従って確認する
                                            if (accept || confirmer.confirm(ConfirmTarget.RejectFollowRequest)) {
                                                onAnswerFollowRequest(accept)
                                            }
                                        }
                                    },
                                )
                            }
                        },
                        actions = {
                            ProfileActionButton(
                                state = state.button,
                                profile = profile,
                                onSetFollowing = onSetFollowing,
                                // プロフィール編集は未実装
                                onEditProfile = {},
                            )
                        },
                    )
                },
                pinned = {
                    ConnectedTabRow(
                        tabs = tabItems,
                        selectedIndex = pagerState.targetPage,
                        onSelect = { index ->
                            scope.launch {
                                if (index == pagerState.currentPage && !pagerState.isScrollInProgress) {
                                    // 表示中のタブをもう一度押したら一覧を先頭へ (ヘッダの畳み具合はそのまま)
                                    listStates.getValue(tabs[index]).scrollToTop()
                                } else {
                                    pagerState.animateScrollToPage(index, animationSpec = spatialSpec)
                                }
                            }
                        },
                        modifier = Modifier
                            .onSizeChanged { tabsHeight = with(density) { it.height.toDp() } }
                            .surfaceBlur(hazeState, tabsBlurStyle)
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
                            .surfaceBlurSource(hazeState, LocalSurfaceBlurEnabled.current),
                        key = { tabs[it] },
                    ) { page ->
                        val tab = tabs[page]
                        ProfileTabPage(
                            tab = tab,
                            listState = listStates.getValue(tab),
                            topPadding = tabsHeight,
                            feed = state.feedOf(tab),
                            pinnedPosts = if (tab == ProfileTab.Posts) state.extras.pinnedPosts else emptyList(),
                            onLoadMore = { onLoadMore(tab) },
                            onUserClick = onUserClick,
                            onPostClick = onPostClick,
                            onMediaClick = onMediaClick,
                            onVote = onVote,
                            onAction = onAction,
                            onReply = onReply,
                            onHashtagClick = onHashtagClick,
                        )
                    }
                },
            )
        }
    }
}

/**
 * タブ 1 つ分の投稿一覧。Media は `only_media` で絞り込んだポスト、Favourites / Bookmarks は自分のお気に入り・
 * ブックマークを同じ形で出すだけ。
 */
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
    onReply: ((Post) -> Unit)?,
    onHashtagClick: ((String) -> Unit)?,
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
        onReply = onReply,
        onHashtagClick = onHashtagClick,
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
                        onReply = onReply,
                        onHashtagClick = onHashtagClick,
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
                                stringResource(tab.emptyMessage()),
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
    ProfileTab.Favourites -> R.string.profile_tab_favourites
    ProfileTab.Bookmarks -> R.string.profile_tab_bookmarks
}

private fun ProfileTab.icon(): ImageVector = when (this) {
    ProfileTab.Posts -> Symbols.Outlined.Article
    ProfileTab.Media -> Symbols.Outlined.Image
    ProfileTab.Favourites -> Symbols.Outlined.Star
    ProfileTab.Bookmarks -> Symbols.Outlined.Bookmark
}

private fun ProfileTab.selectedIcon(): ImageVector = when (this) {
    ProfileTab.Posts -> Symbols.Filled.Article
    ProfileTab.Media -> Symbols.Filled.Image
    ProfileTab.Favourites -> Symbols.Filled.Star
    ProfileTab.Bookmarks -> Symbols.Filled.Bookmark
}

private fun ProfileTab.emptyMessage(): Int = when (this) {
    ProfileTab.Posts -> R.string.profile_no_posts
    ProfileTab.Media -> R.string.profile_no_media
    ProfileTab.Favourites -> R.string.profile_no_favourites
    ProfileTab.Bookmarks -> R.string.profile_no_bookmarks
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

/** ヘッダ画像が隠れていくとき、トップバーが透明から不透明に変わるまでのスクロール量。 */
private val BarFadeDistance = 32.dp
