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
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import es.aria_on_the_planet.quasar.ui.component.player.MediaOverlayColors
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
    DetailScaffold(
        onBack = onBack,
        titleRevealed = scrolledPast,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        title = title,
        revealedTitle = revealedTitle,
        content = content,
    )
}

/**
 * [DetailScaffold] の、タイトルを切り替えるかを呼び出し側が決める版 (一覧が 1 本ではない画面向け)。
 *
 * @param titleRevealed `true` なら [revealedTitle] を出す。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    onBack: () -> Unit,
    titleRevealed: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit = {},
    revealedTitle: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val showRevealed = revealedTitle != null && titleRevealed

    Scaffold(
        modifier = modifier,
        topBar = {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Symbols.Outlined.ArrowBack, stringResource(R.string.back))
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

/**
 * [DetailScaffold] の、トップバーを中身の上に重ねる版 (プロフィールのヘッダ画像をバーの裏まで描く用)。
 * 中身は画面の上端 (ステータスバーの裏) から描き、バーの面の濃さは [barAlpha] (0 = 透明、1 = 不透明。引数はバーの高さ (px)。
 * 描画時に読む) で決める。
 * 透明な間は戻るボタンに丸い下地 ([MediaOverlayColors]) を敷き、画像の上でも読めるようにする。
 *
 * @param content 引数はバーの高さ (ステータスバー込み)。中身はこの分を上端の余白・畳み切ったときの位置に使う。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayDetailScaffold(
    onBack: () -> Unit,
    titleRevealed: Boolean,
    barAlpha: (barHeightPx: Float) -> Float,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    revealedTitle: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.(barHeight: Dp) -> Unit,
) {
    // バーの高さ (px)。面・戻るボタンの濃さを描画時に決めるのに使う (レイアウトには Scaffold の余白を使う)
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    val alpha = { barAlpha(barHeightPx) }
    val surface = MaterialTheme.colorScheme.surface
    val showRevealed = revealedTitle != null && titleRevealed
    // 中身は Scaffold の余白を当てずに全面へ描き、トップバーはその上に重なる
    Scaffold(
        modifier = modifier,
        topBar = {
                TopAppBar(
                    modifier = Modifier
                        .onSizeChanged { barHeightPx = it.height.toFloat() }
                        .drawBehind { drawRect(surface.copy(alpha = alpha())) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val onSurface = MaterialTheme.colorScheme.onSurface
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .drawBehind {
                                        drawCircle(MediaOverlayColors.Scrim.copy(alpha = MediaOverlayColors.Scrim.alpha * (1f - alpha())))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                // 色は描画時に混ぜる (スクロールのたびに組み直さない)
                                Icon(
                                    Symbols.Outlined.ArrowBack,
                                    stringResource(R.string.back),
                                    modifier = Modifier
                                        // SrcIn をアイコンの形だけに効かせるため、先に別のレイヤに描く
                                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                        .drawWithContent {
                                            drawContent()
                                            drawRect(
                                                lerp(MediaOverlayColors.Content, onSurface, alpha()),
                                                blendMode = BlendMode.SrcIn,
                                            )
                                        },
                                )
                            }
                        }
                    },
                    title = {
                        AnimatedContent(
                            targetState = showRevealed,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "detailTitle",
                        ) { revealed ->
                            if (revealed) revealedTitle?.invoke()
                        }
                    },
                )
        },
    ) { padding ->
        val barHeight = padding.calculateTopPadding()
        DetailRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTopPadding = barHeight,
        ) {
            content(barHeight)
        }
    }
}

/** 詳細画面の pull-to-refresh (Expressive の LoadingIndicator)。トップバーの下にタブ等を挟む画面でも使う。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorTopPadding: Dp = 0.dp,
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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = indicatorTopPadding),
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
