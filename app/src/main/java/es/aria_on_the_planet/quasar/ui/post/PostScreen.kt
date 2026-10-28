package es.aria_on_the_planet.quasar.ui.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.rememberHazeState
import es.aria_on_the_planet.quasar.ui.component.blur.LocalSurfaceBlurEnabled
import es.aria_on_the_planet.quasar.ui.component.blur.surfaceBlurSource
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.ui.component.dialog.LocalActionConfirmer
import es.aria_on_the_planet.quasar.ui.composer.ComposerScreen
import es.aria_on_the_planet.quasar.ui.component.post.PostCard
import es.aria_on_the_planet.quasar.ui.component.post.PostDetail
import es.aria_on_the_planet.quasar.ui.component.post.PostMenu
import es.aria_on_the_planet.quasar.ui.component.post.OwnPostMenuActions
import es.aria_on_the_planet.quasar.ui.composer.ComposerSession
import es.aria_on_the_planet.quasar.ui.composer.DraftStore
import es.aria_on_the_planet.quasar.ui.composer.QuickComposerHost
import es.aria_on_the_planet.quasar.ui.composer.rememberQuickPostBarState
import es.aria_on_the_planet.quasar.ui.component.post.ThreadLineDefaults
import es.aria_on_the_planet.quasar.ui.component.post.threadLines
import es.aria_on_the_planet.quasar.ui.component.post.rememberPostActionHandler
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailLoading
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailMessage
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailTitle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private val AvatarCenterX = ThreadLineDefaults.AvatarCenterX
private val AvatarTopY = ThreadLineDefaults.AvatarTopY

/**
 * ポスト詳細 (祖先 / 本体 / リプライ)。ログイン中は下にクイック投稿バーを出す (下書きはタイムラインと共有)。
 *
 * X と同じく、何も書いていない状態で開くと本体のポストへの返信になる ([ComposerSession.startAutoReply])。
 * 前面に来るたびに付け、離れるときに何も書いていなければ外す (下書きを共有しているので、外さないと
 * タイムラインのバーまで返信になる)。重ねて開いたポスト画面の間でも、前面の画面の本体が返信先になる。
 *
 * 本体の投稿者の行に︙メニュー ([PostMenu]。ブラウザで開く・ブロック等) を出し、自分のポストなら編集・削除も。編集はフルの投稿画面をこの画面に重ねて
 * 開き (下書きとは別の投稿欄 [PostViewModel.editor])、保存したら本体を差し替える。削除したら画面を閉じる。
 * どちらも設定に従って確認する (保存の確認は投稿画面の送信ボタンが行う)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostScreen(
    viewModel: PostViewModel,
    drafts: DraftStore,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
    onPostClick: (Post) -> Unit,
    onMediaClick: (MediaClick) -> Unit,
    onHashtagClick: (String) -> Unit,
    onReactionCountClick: (Post, PostActionType) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val composer = drafts.current.collectAsStateWithLifecycle().value
    val barState = rememberQuickPostBarState()
    val hazeState = rememberHazeState()
    val snackbarHostState = remember { SnackbarHostState() }
    val mainPost = state.thread?.post
    LifecycleResumeEffect(composer, mainPost?.statusId) {
        if (composer != null && mainPost != null) composer.startAutoReply(mainPost)
        onPauseOrDispose { composer?.releaseAutoReply() }
    }
    // 自分の投稿はストリーミングで届かないので、このスレッドへの返信なら手元で足す
    LaunchedEffect(drafts) {
        drafts.published.collect(viewModel::addPublishedReply)
    }
    val onReply: ((Post) -> Unit)? = composer?.let { session ->
        { post ->
            session.startReply(post)
            barState.requestInput()
        }
    }
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PostScreenEvent.Deleted -> onBack()
                is PostScreenEvent.EditLoadFailed ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.post_edit_load_failed, event.message))
                is PostScreenEvent.DeleteFailed ->
                    snackbarHostState.showSnackbar(resources.getString(R.string.post_delete_failed, event.message))
            }
        }
    }
    val confirmer = LocalActionConfirmer.current
    val scope = rememberCoroutineScope()
    val postMenu: @Composable (Post) -> Unit = { post ->
        PostMenu(
            post = post,
            own = if (state.isMine) {
                OwnPostMenuActions(
                    onEdit = viewModel::startEdit,
                    onDelete = {
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            if (confirmer.confirm(ConfirmTarget.DeletePost)) viewModel.delete()
                        }
                    },
                )
            } else {
                null
            },
            busy = state.ownPostBusy,
            // ボタンの余白の分だけ右へ出し、︙を本文の右端に揃える
            modifier = Modifier.offset(x = 12.dp),
        )
    }

    val listState = rememberLazyListState()
    val thread = state.thread
    val onVote: suspend (Post, List<Int>) -> Boolean = viewModel::vote
    val onAction = rememberPostActionHandler(viewModel::performAction)
    val mainIndex = thread?.ancestors?.size ?: 0

    // 初回表示時は祖先ではなく本体のポストを先頭に出す
    var scrolledToMain by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(thread != null) {
        if (thread != null && !scrolledToMain) {
            listState.scrollToItem(mainIndex)
            scrolledToMain = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        QuickComposerHost(
            drafts = drafts,
            hazeState = hazeState,
            snackbarHostState = snackbarHostState,
            barState = barState,
            onMediaClick = onMediaClick,
        ) { barSpace ->
            DetailScaffold(
                onBack = onBack,
                listState = listState,
                isRefreshing = state.isRefreshing && thread != null,
                onRefresh = viewModel::refresh,
                revealTitleAfterIndex = mainIndex,
                title = { Text(stringResource(R.string.post_title)) },
                revealedTitle = thread?.post?.let { post ->
                    {
                        DetailTitle(
                            name = post.author.nameOrAcct,
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
                        LazyColumn(
                            // バーの後ろまで描き (ぼかしの元)、最後のリプライがバーに隠れないよう下端を空ける
                            Modifier
                                .fillMaxSize()
                                .surfaceBlurSource(hazeState, LocalSurfaceBlurEnabled.current),
                            state = listState,
                            contentPadding = PaddingValues(bottom = barSpace),
                        ) {
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
                                    onAction = onAction,
                                    onReply = onReply,
                                    onHashtagClick = onHashtagClick,
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
                                    onAction = onAction,
                                    onReply = onReply,
                                    onHashtagClick = onHashtagClick,
                                    menu = { postMenu(thread.post) },
                                    reactionDetail = true,
                                    onReactionCountClick = onReactionCountClick,
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
                                        // キャッシュの本体を出してスレッドを取っている間
                                        if (state.isLoading) {
                                            LoadingIndicator()
                                        } else {
                                            Text(
                                                stringResource(R.string.post_no_replies),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
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
                                    onAction = onAction,
                                    onReply = onReply,
                                    onHashtagClick = onHashtagClick,
                                    showReplyIndicator = false,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 編集: フルの投稿画面を重ねる (閉じるアニメーションの間も最後の投稿欄を出しておく)
        var lastEditor by remember { mutableStateOf(editor) }
        if (editor != null) lastEditor = editor
        AnimatedVisibility(
            visible = editor != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            lastEditor?.let { session ->
                ComposerScreen(
                    session = session,
                    onClose = viewModel::closeEditor,
                    onFinish = viewModel::closeEditor,
                    keepDraftOnClose = false,
                )
            }
        }
    }
}
