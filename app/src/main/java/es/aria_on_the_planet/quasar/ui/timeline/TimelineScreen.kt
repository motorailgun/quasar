package es.aria_on_the_planet.quasar.ui.timeline

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.TimelineType
import es.aria_on_the_planet.quasar.ui.component.post.PostList

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    // タイムラインごとにスクロール位置を保持する
    val listStates = TimelineType.entries.associateWith { rememberLazyListState() }

    val error = state.current.error
    LaunchedEffect(error) {
        if (error != null && state.current.posts.isNotEmpty()) snackbarHostState.showSnackbar(error)
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
        val timeline = state.current
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = timeline.isRefreshing && timeline.posts.isNotEmpty(),
            onRefresh = { viewModel.refresh() },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(padding),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = timeline.isRefreshing && timeline.posts.isNotEmpty(),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            when {
                timeline.posts.isEmpty() && (timeline.isRefreshing || !timeline.loaded) ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }

                timeline.posts.isEmpty() && timeline.error != null ->
                    CenteredMessage(stringResource(R.string.timeline_error, timeline.error))

                else -> PostList(
                    posts = timeline.posts,
                    modifier = Modifier.fillMaxSize(),
                    state = listStates.getValue(state.selected),
                    isLoadingMore = timeline.isLoadingMore,
                    onLoadMore = { viewModel.loadMore(state.selected) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimelineSelector(
    available: List<TimelineType>,
    selected: TimelineType,
    onSelect: (TimelineType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        available.forEachIndexed { index, type ->
            ToggleButton(
                checked = type == selected,
                onCheckedChange = { onSelect(type) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    available.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Icon(type.icon(), contentDescription = null, modifier = Modifier.size(18.dp))
                Box(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(stringResource(type.label()))
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

private fun TimelineType.icon() = when (this) {
    TimelineType.Home -> Icons.Outlined.Home
    TimelineType.Local -> Icons.Outlined.Groups
    TimelineType.Global -> Icons.Outlined.Public
}

private fun TimelineType.label() = when (this) {
    TimelineType.Home -> R.string.timeline_home
    TimelineType.Local -> R.string.timeline_local
    TimelineType.Global -> R.string.timeline_global
}
