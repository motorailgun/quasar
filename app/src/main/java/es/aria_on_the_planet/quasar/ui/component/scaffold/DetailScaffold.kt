package es.aria_on_the_planet.quasar.ui.component.scaffold

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText

/**
 * 詳細画面 (プロフィール・ポスト等) 共通の骨組み。
 * 戻るボタン付きのトップバーと pull-to-refresh を持ち、[listState] の先頭から
 * [revealTitleAfterIndex] 番目のアイテムが画面外に出たら、タイトルを [revealedTitle] に切り替える。
 *
 * @param title 通常時のタイトル。
 * @param revealedTitle スクロール後のタイトル。`null` なら常に [title]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    onBack: () -> Unit,
    listState: LazyListState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    revealTitleAfterIndex: Int = 0,
    title: @Composable () -> Unit = {},
    revealedTitle: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val revealIndex by rememberUpdatedState(revealTitleAfterIndex)
    val scrolledPast by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > revealIndex }
    }
    val showRevealed = revealedTitle != null && scrolledPast

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = {
                    AnimatedContent(
                        targetState = showRevealed,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "detailTitle",
                    ) { revealed ->
                        if (revealed) revealedTitle?.invoke() else title()
                    }
                },
            )
        },
    ) { padding ->
        DetailRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
}

/** トップバー用の 2 段タイトル (表示名 + 補足)。 */
@Composable
fun DetailTitle(name: String, emojis: List<CustomEmoji>, subtitle: String) {
    Column {
        EmojiText(
            text = name,
            emojis = emojis,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 初回読み込み中の全面表示。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

/** 読み込み失敗などの全面メッセージ。 */
@Composable
fun DetailMessage(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
