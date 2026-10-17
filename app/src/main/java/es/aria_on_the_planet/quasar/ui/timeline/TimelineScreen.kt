package es.aria_on_the_planet.quasar.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.ui.navigation.LocalNavigator
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.list.scrollToTop
import es.aria_on_the_planet.quasar.ui.notification.NotificationList
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.composer.DraftStore
import es.aria_on_the_planet.quasar.ui.composer.QuickComposerHost
import es.aria_on_the_planet.quasar.ui.composer.rememberQuickPostBarState
import es.aria_on_the_planet.quasar.ui.component.post.rememberPostActionHandler
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    drafts: DraftStore,
    canPost: Boolean,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // カラムごとにスクロール位置を保持する
    val listStates = ColumnType.entries.associateWith { rememberLazyListState() }
    val pageSpring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val pagerState = rememberPagerState(initialPage = state.available.indexOf(state.selected)) { state.available.size }
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current

    // スワイプで切り替わったら選択に反映する (ボタンで選んだときは先に select 済み)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.select(state.available[it]) }
    }

    // 画面が前面にある間だけストリーミングで自動更新する
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.stream() }
    }

    val error = state.current.error
    LaunchedEffect(error) {
        if (error != null && state.current.items.isNotEmpty()) snackbarHostState.showSnackbar(error)
    }

    // クイック投稿バー: 下に浮かべ、後ろの一覧をぼかす。ピルを引き上げると同じ画面内でフルの投稿画面に広がる
    val hazeState = rememberHazeState()
    val barState = rememberQuickPostBarState()
    var headerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val draft by drafts.current.collectAsStateWithLifecycle()
    val composer = draft.takeIf { canPost }
    // ヘッダ左のアイコンに出すログイン中のアカウント (投稿用に取得しているものを使う)
    val account: User? = composer?.state?.collectAsStateWithLifecycle()?.value?.account
    // 返信ボタン: 下書き (返信と通常の投稿で共有) の返信先にしてバーで書き始める
    val onReply: ((Post) -> Unit)? = composer?.let { session ->
        { post ->
            session.startReply(post)
            barState.requestInput()
        }
    }
    val onHashtagClick: (String) -> Unit = navigator::openHashtag

    QuickComposerHost(
        drafts = drafts,
        hazeState = hazeState,
        snackbarHostState = snackbarHostState,
        enabled = canPost,
        barState = barState,
        onMediaClick = navigator::openMedia,
    ) { barSpace ->
        // 一覧の上端はヘッダ (ステータスバーを含む) の分だけ空ける。一覧自体は画面の最上端から描き、スクロールすると
        // ステータスバーやヘッダの裏に入る
        val topPadding = headerHeight
        // 一覧の下端はバー (と下の余白・ナビゲーションバー) の分だけ空け、最後の項目がバーに隠れないようにする
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + barSpace
        // トップバーは置かず、ヘッダ (アイコン + タブ) を一覧の上に浮かべる
        Scaffold { _ ->
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    // 上下ともヘッダ・バーの後ろまで描き (ぼかしの元)、余白は一覧の contentPadding で取る
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                    key = { state.available[it] },
                    // 指を離した後はテーマの spring で吸着させる (Expressive では少し行き過ぎて戻る)
                    flingBehavior = PagerDefaults.flingBehavior(state = pagerState, snapAnimationSpec = pageSpring),
                ) { page ->
                    val column = state.available[page]
                    // 隣のカラムは指で引き出し始めた時点で読み込む。ボタンで遠くへ飛ぶときに通過するカラムは読まない
                    val shouldLoad = dragging || pagerState.settledPage == page
                    LaunchedEffect(column, shouldLoad) {
                        if (shouldLoad) viewModel.ensureLoaded(column)
                    }
                    ColumnPage(
                        column = column,
                        state = state,
                        listState = listStates.getValue(column),
                        viewModel = viewModel,
                        topPadding = topPadding,
                        bottomPadding = bottomPadding,
                        onReply = onReply,
                        onHashtagClick = onHashtagClick,
                    )
                }
                StatusBarScrim(Modifier.align(Alignment.TopCenter))
                TimelineHeader(
                    account = account,
                    available = state.available,
                    pagerState = pagerState,
                    hazeState = hazeState,
                    onSelect = { column ->
                        val index = state.available.indexOf(column)
                        if (index == pagerState.currentPage && !pagerState.isScrollInProgress) {
                            // 表示中のカラムのボタンをもう一度押したら先頭へ
                            scope.launch { listStates.getValue(column).scrollToTop() }
                        } else {
                            viewModel.select(column)
                            scope.launch { pagerState.animateScrollToPage(index, animationSpec = pageSpring) }
                        }
                    },
                    onOpenSettings = navigator::openSettings,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnPage(
    column: ColumnType,
    state: TimelineScreenState,
    listState: LazyListState,
    viewModel: TimelineViewModel,
    topPadding: Dp,
    bottomPadding: Dp,
    onReply: ((Post) -> Unit)?,
    onHashtagClick: ((String) -> Unit)?,
) {
    val navigator = LocalNavigator.current
    val feed = state.feedOf(column)
    val pullState = rememberPullToRefreshState()
    val isRefreshing = feed.isRefreshing && feed.items.isNotEmpty()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh(column) },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = isRefreshing,
                // ヘッダの下から出す
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding),
            )
        },
    ) {
        val onVote: (suspend (Post, List<Int>) -> Boolean)? = if (viewModel.canInteract) viewModel::vote else null
        val onAction = rememberPostActionHandler(viewModel::performAction).takeIf { viewModel.canInteract }
        val onMediaClick: (MediaClick) -> Unit = navigator::openMedia
        val timelineType = column.timeline
        when {
            feed.items.isEmpty() && (feed.isRefreshing || !feed.loaded) ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }

            feed.items.isEmpty() && feed.error != null ->
                CenteredMessage(stringResource(R.string.timeline_error, feed.error!!))

            feed.items.isEmpty() ->
                CenteredMessage(stringResource(R.string.column_empty))

            timelineType == null -> NotificationList(
                entries = state.notifications.items,
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                isLoadingMore = feed.isLoadingMore,
                onLoadMore = { viewModel.loadMore(column) },
                onUserClick = navigator::openProfile,
                onPostClick = navigator::openPost,
                onMediaClick = onMediaClick,
                onVote = onVote,
                onAction = onAction,
                onReply = onReply,
                onHashtagClick = onHashtagClick,
            )

            else -> PostList(
                items = state.timelines.getValue(timelineType).items,
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                isLoadingMore = feed.isLoadingMore,
                onLoadMore = { viewModel.loadMore(column) },
                onUserClick = navigator::openProfile,
                onPostClick = navigator::openPost,
                onVote = onVote,
                onAction = onAction,
                onReply = onReply,
                onHashtagClick = onHashtagClick,
                onMediaClick = onMediaClick,
                onGapClick = { gap -> viewModel.fillGap(column, gap) },
            )
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}


internal fun ColumnType.icon() = when (this) {
    ColumnType.Home -> Icons.Outlined.Home
    ColumnType.Notifications -> Icons.Outlined.Notifications
    ColumnType.Local -> Icons.Outlined.Groups
    ColumnType.Global -> Icons.Outlined.Public
}

internal fun ColumnType.label() = when (this) {
    ColumnType.Home -> R.string.timeline_home
    ColumnType.Notifications -> R.string.column_notifications
    ColumnType.Local -> R.string.timeline_local
    ColumnType.Global -> R.string.timeline_global
}
