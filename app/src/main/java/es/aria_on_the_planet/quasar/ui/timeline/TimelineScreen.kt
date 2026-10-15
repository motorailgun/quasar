package es.aria_on_the_planet.quasar.ui.timeline

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.notification.NotificationList
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.post.rememberPostActionHandler
import es.aria_on_the_planet.quasar.ui.post.openPost
import es.aria_on_the_planet.quasar.ui.mediaviewer.openMedia
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.profile.openProfile
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    // カラムごとにスクロール位置を保持する
    val listStates = ColumnType.entries.associateWith { rememberLazyListState() }
    val pageSpring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val pagerState = rememberPagerState(initialPage = state.available.indexOf(state.selected)) { state.available.size }
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.logout))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                TimelineSelector(
                    available = state.available,
                    pagerState = pagerState,
                    onSelect = { column ->
                        viewModel.select(column)
                        scope.launch {
                            pagerState.animateScrollToPage(state.available.indexOf(column), animationSpec = pageSpring)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
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
            )
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
) {
    val context = LocalContext.current
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
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        val onVote: (suspend (Post, List<Int>) -> Boolean)? = if (viewModel.canInteract) viewModel::vote else null
        val onAction = rememberPostActionHandler(viewModel::performAction).takeIf { viewModel.canInteract }
        val onMediaClick: (MediaClick) -> Unit = context::openMedia
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
                isLoadingMore = feed.isLoadingMore,
                onLoadMore = { viewModel.loadMore(column) },
                onUserClick = context::openProfile,
                onPostClick = context::openPost,
                onMediaClick = onMediaClick,
                onVote = onVote,
                onAction = onAction,
            )

            else -> PostList(
                items = state.timelines.getValue(timelineType).items,
                modifier = Modifier.fillMaxSize(),
                state = listState,
                isLoadingMore = feed.isLoadingMore,
                onLoadMore = { viewModel.loadMore(column) },
                onUserClick = context::openProfile,
                onPostClick = context::openPost,
                onVote = onVote,
                onAction = onAction,
                onMediaClick = onMediaClick,
                onGapClick = { gap -> viewModel.fillGap(column, gap) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimelineSelector(
    available: List<ColumnType>,
    pagerState: PagerState,
    onSelect: (ColumnType) -> Unit,
    modifier: Modifier = Modifier,
) {
    // スワイプ中も指に合わせて幅が移るよう、ページの位置 (小数) から各ボタンの重みを決める
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val selectedIndex = pagerState.targetPage
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        available.forEachIndexed { index, type ->
            val checked = index == selectedIndex
            // 4 カラムでは等幅にラベルが収まらないので、選択中のみラベルを出して幅を広げる
            val closeness = (1f - abs(position - index)).coerceIn(0f, 1f)
            val weight = 1f + (SelectedWeight - 1f) * closeness
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(type) },
                modifier = Modifier.weight(weight).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    available.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                val label = stringResource(type.label())
                Icon(
                    type.icon(),
                    contentDescription = if (checked) null else label,
                    modifier = Modifier.size(18.dp),
                )
                if (checked) {
                    Box(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val SelectedWeight = 1.8f

private fun ColumnType.icon() = when (this) {
    ColumnType.Home -> Icons.Outlined.Home
    ColumnType.Notifications -> Icons.Outlined.Notifications
    ColumnType.Local -> Icons.Outlined.Groups
    ColumnType.Global -> Icons.Outlined.Public
}

private fun ColumnType.label() = when (this) {
    ColumnType.Home -> R.string.timeline_home
    ColumnType.Notifications -> R.string.column_notifications
    ColumnType.Local -> R.string.timeline_local
    ColumnType.Global -> R.string.timeline_global
}
