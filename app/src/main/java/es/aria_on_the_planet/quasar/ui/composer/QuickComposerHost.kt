package es.aria_on_the_planet.quasar.ui.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick

/**
 * 画面の下にクイック投稿バーを浮かべ、ピルを引き上げたら同じ画面内でフルの投稿画面 ([ComposerScreen]) に広げる。
 * タイムラインとポスト画面で共通。下書きは [drafts] の今のもの (アプリ全体で 1 つ、両画面で共有)。
 *
 * [content] (後ろの一覧) は画面いっぱいに描き、[hazeState] の source にする (バーがぼかす)。最後の項目がバーに
 * 隠れないよう、一覧の下端の余白に [content] の引数 (バーの高さ + 上下の余白。ナビゲーションバーは含まない) を足す。
 *
 * 投稿できたら ([DraftStore.published]) フル画面を閉じる。送信エラーはフル画面を開いていなければ
 * [snackbarHostState] (バーの上) に出す。
 */
@Composable
fun QuickComposerHost(
    drafts: DraftStore,
    hazeState: HazeState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    barState: QuickPostBarState = rememberQuickPostBarState(),
    onMediaClick: (MediaClick) -> Unit = {},
    content: @Composable (barSpace: Dp) -> Unit,
) {
    val session = drafts.current.collectAsStateWithLifecycle().value
    var expanded by rememberSaveable { mutableStateOf(false) }
    var barHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    LaunchedEffect(drafts) {
        drafts.published.collect { expanded = false }
    }
    if (session != null) {
        val draftState by session.state.collectAsStateWithLifecycle()
        // フル画面は自分のスナックバーに出す
        val postError = draftState.error?.takeUnless { expanded }
            ?.let { stringResource(R.string.composer_post_failed, it) }
        LaunchedEffect(postError) {
            if (postError != null) {
                snackbarHostState.showSnackbar(postError)
                session.errorShown()
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        content(if (session != null) barHeight + BarMargin * 2 else 0.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // キーボードが出ているときはその上に乗せる
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = BarMargin, vertical = BarMargin),
        ) {
            SnackbarHost(snackbarHostState)
            if (session != null) {
                QuickPostBar(
                    session = session,
                    hazeState = hazeState,
                    onExpand = { expanded = true },
                    barState = barState,
                    onMediaClick = onMediaClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                )
            }
        }

        // バーが下から伸びるようにフルの投稿画面を出す (下書きは共有、閉じるとバーに戻る)
        AnimatedVisibility(
            visible = expanded && session != null,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            session?.let {
                ComposerScreen(
                    session = it,
                    onClose = { expanded = false },
                    onFinish = {
                        expanded = false
                        drafts.finish()
                    },
                    keepDraftOnClose = true,
                )
            }
        }
    }
}

private val BarMargin = 12.dp
