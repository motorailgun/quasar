package es.aria_on_the_planet.quasar.ui.post

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.post.PostCard
import es.aria_on_the_planet.quasar.ui.component.post.PostDetail
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailLoading
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailMessage
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailTitle

/** PostCard のアイコン中心の x 座標 (左余白 16dp + アイコン半径 20dp)。 */
private val AvatarCenterX = 36.dp
/** PostCard のアイコン上端の y 座標。 */
private val AvatarTopY = 12.dp
private val ThreadLineWidth = 2.dp

@Composable
fun PostScreen(
    viewModel: PostViewModel,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
    onPostClick: (Post) -> Unit,
    onMediaClick: (MediaClick) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val thread = state.thread
    val onVote: (suspend (Post, List<Int>) -> Boolean)? = if (viewModel.canVote) viewModel::vote else null
    val mainIndex = thread?.ancestors?.size ?: 0

    // 初回表示時は祖先ではなく本体のポストを先頭に出す
    var scrolledToMain by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(thread != null) {
        if (thread != null && !scrolledToMain) {
            listState.scrollToItem(mainIndex)
            scrolledToMain = true
        }
    }

    DetailScaffold(
        onBack = onBack,
        listState = listState,
        isRefreshing = state.isLoading && thread != null,
        onRefresh = viewModel::refresh,
        revealTitleAfterIndex = mainIndex,
        title = { Text(stringResource(R.string.post_title)) },
        revealedTitle = thread?.post?.let { post ->
            {
                DetailTitle(
                    name = post.author.displayName.ifBlank { post.author.screenName },
                    emojis = post.author.emojis,
                    subtitle = "@${post.author.fullScreenName}",
                )
            }
        },
    ) {
        when {
            thread == null && state.error != null -> DetailMessage(stringResource(R.string.post_error, state.error!!))
            thread == null -> DetailLoading()
            else -> {
                val lineColor = MaterialTheme.colorScheme.outlineVariant
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    // 祖先: アイコン同士を縦線で繋ぐ
                    items(thread.ancestors, key = { "ancestor:${it.statusId}" }, contentType = { "post" }) { post ->
                        val isFirst = post === thread.ancestors.first()
                        PostCard(
                            post = post,
                            modifier = Modifier.threadLines(
                                xs = listOf(AvatarCenterX),
                                color = lineColor,
                                startY = if (isFirst) AvatarTopY else 0.dp,
                            ),
                            onUserClick = onUserClick,
                            onPostClick = onPostClick,
                            onMediaClick = onMediaClick,
                            onVote = onVote,
                            showReplyIndicator = false,
                        )
                    }
                    item(key = "main", contentType = "main") {
                        PostDetail(
                            post = thread.post,
                            modifier = if (thread.ancestors.isNotEmpty()) {
                                Modifier.threadLines(listOf(AvatarCenterX), lineColor, endY = AvatarTopY)
                            } else {
                                Modifier
                            },
                            onUserClick = onUserClick,
                            onPostClick = onPostClick,
                            onMediaClick = onMediaClick,
                            onVote = onVote,
                        )
                        HorizontalDivider()
                    }
                    if (state.replies.isEmpty()) {
                        item(key = "no_replies", contentType = "status") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.post_no_replies),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    itemsIndexed(state.replies, key = { _, row -> "reply:${row.post.statusId}" }, contentType = { _, _ -> "post" }) { index, row ->
                        // 返信関係で繋がらない行の間は区切り線
                        if (index > 0 && !row.connectedToPrevious) HorizontalDivider()
                        PostCard(
                            post = row.post,
                            modifier = Modifier.threadLines(
                                xs = if (row.connectedToPrevious || row.connectedToNext) listOf(AvatarCenterX) else emptyList(),
                                color = lineColor,
                                startY = if (row.connectedToPrevious) 0.dp else AvatarTopY,
                                endY = if (row.connectedToNext) null else AvatarTopY,
                            ),
                            onUserClick = onUserClick,
                            onPostClick = onPostClick,
                            onMediaClick = onMediaClick,
                            onVote = onVote,
                            showReplyIndicator = false,
                        )
                    }
                }
            }
        }
    }
}

/** スレッドの縦線を [xs] の各位置に描く。[endY] が `null` なら下端まで。 */
private fun Modifier.threadLines(
    xs: List<Dp>,
    color: Color,
    startY: Dp = 0.dp,
    endY: Dp? = null,
): Modifier = if (xs.isEmpty()) this else drawBehind {
    val top = startY.toPx()
    val bottom = endY?.toPx() ?: size.height
    xs.forEach { x ->
        val px = x.toPx()
        drawLine(color, Offset(px, top), Offset(px, bottom), strokeWidth = ThreadLineWidth.toPx())
    }
}
