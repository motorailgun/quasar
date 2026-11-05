package es.aria_on_the_planet.quasar.ui.personal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.ui.component.dialog.LocalActionConfirmer
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.list.ListStatus
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.component.user.UserList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * 自分へのフォローリクエストの一覧。行の右端に承認 (塗り) / 拒否 (枠線) のボタン。送信中は押した方を
 * [LoadingIndicator] にし、もう片方を無効にする。拒否は設定 ([ConfirmTarget.RejectFollowRequest]) に従って確認する。
 * 行のタップでプロフィール。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FollowRequestsScreen(
    viewModel: FollowRequestsViewModel,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feed = state.requests
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val confirmer = LocalActionConfirmer.current

    DetailScaffold(
        onBack = onBack,
        listState = listState,
        // 初回の読み込みは一覧側に出すので、引っ張って更新したときだけ出す
        isRefreshing = feed.isRefreshing && feed.loaded,
        onRefresh = viewModel::refresh,
        title = {
            Text(stringResource(R.string.settings_follow_requests), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    ) {
        UserList(
            users = feed.items,
            modifier = Modifier.fillMaxSize(),
            state = listState,
            isLoadingMore = feed.isLoadingMore,
            onLoadMore = viewModel::loadMore,
            onUserClick = onUserClick,
            header = {
                if (feed.items.isEmpty()) {
                    item(key = "status", contentType = "status") {
                        ListStatus(
                            loading = !feed.loaded || feed.isRefreshing,
                            error = feed.error?.let { stringResource(R.string.user_list_error, it) },
                            empty = stringResource(R.string.follow_requests_empty),
                        )
                    }
                }
            },
            trailing = { user ->
                val answering = state.answering[user.inServerId]
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledIconButton(
                        onClick = { if (answering == null) viewModel.answer(user, accept = true) },
                        enabled = answering != false,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        AnswerIcon(
                            Symbols.Filled.Check,
                            stringResource(R.string.profile_follow_request_accept),
                            loading = answering == true,
                        )
                    }
                    OutlinedIconButton(
                        onClick = {
                            if (answering != null) return@OutlinedIconButton
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                // 拒否は取り消せない (相手に申請し直してもらうしかない) ので、設定に従って確認する
                                if (confirmer.confirm(ConfirmTarget.RejectFollowRequest)) {
                                    viewModel.answer(user, accept = false)
                                }
                            }
                        },
                        enabled = answering != true,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        AnswerIcon(
                            Symbols.Outlined.Close,
                            stringResource(R.string.profile_follow_request_reject),
                            loading = answering == false,
                        )
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnswerIcon(icon: ImageVector, label: String, loading: Boolean) {
    if (loading) {
        LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current)
    } else {
        Icon(icon, contentDescription = label)
    }
}
