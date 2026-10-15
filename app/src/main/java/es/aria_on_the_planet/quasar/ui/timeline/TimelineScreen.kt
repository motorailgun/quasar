package es.aria_on_the_planet.quasar.ui.timeline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val translations by viewModel.translations.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    // カラムごとにスクロール位置を保持する
    val listStates = ColumnType.entries.associateWith { rememberLazyListState() }
    val context = LocalContext.current

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
                    selected = state.selected,
                    onSelect = viewModel::select,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val current = state.current
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = current.isRefreshing && current.items.isNotEmpty(),
            onRefresh = { viewModel.refresh() },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(padding),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = current.isRefreshing && current.items.isNotEmpty(),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            val onVote: (suspend (Post, List<Int>) -> Boolean)? = if (viewModel.canInteract) viewModel::vote else null
            val onAction = rememberPostActionHandler(viewModel::performAction).takeIf { viewModel.canInteract }
            val onMediaClick: (MediaClick) -> Unit = context::openMedia
            val timelineType = state.selected.timeline
            when {
                current.items.isEmpty() && (current.isRefreshing || !current.loaded) ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }

                current.items.isEmpty() && current.error != null ->
                    CenteredMessage(stringResource(R.string.timeline_error, current.error!!))

                current.items.isEmpty() ->
                    CenteredMessage(stringResource(R.string.column_empty))

                timelineType == null -> NotificationList(
                    entries = state.notifications.items,
                    modifier = Modifier.fillMaxSize(),
                    state = listStates.getValue(state.selected),
                    isLoadingMore = current.isLoadingMore,
                    onLoadMore = { viewModel.loadMore(state.selected) },
                    onUserClick = context::openProfile,
                    onPostClick = context::openPost,
                    onMediaClick = onMediaClick,
                    onVote = onVote,
                    onAction = onAction,
                )

                else -> PostList(
                    items = state.timelines.getValue(timelineType).items,
                    modifier = Modifier.fillMaxSize(),
                    state = listStates.getValue(state.selected),
                    isLoadingMore = current.isLoadingMore,
                    onLoadMore = { viewModel.loadMore(state.selected) },
                    onUserClick = context::openProfile,
                    onPostClick = context::openPost,
                    onVote = onVote,
                    onAction = onAction,
                    onMediaClick = onMediaClick,
                    onGapClick = { gap -> viewModel.fillGap(state.selected, gap) },
                    translationOf = translations::of,
                    onTranslationToggle = viewModel::toggleTranslation,
                    onPostShown = viewModel::onPostShown,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimelineSelector(
    available: List<ColumnType>,
    selected: ColumnType,
    onSelect: (ColumnType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        available.forEachIndexed { index, type ->
            val checked = type == selected
            // 4 カラムでは等幅にラベルが収まらないので、選択中のみラベルを出して幅を広げる
            val weight by animateFloatAsState(if (checked) SelectedWeight else 1f, label = "columnWeight")
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
