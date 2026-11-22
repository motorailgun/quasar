package es.aria_on_the_planet.quasar.ui.post

import android.text.format.DateFormat
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.PostVersion
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.asPost
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.list.LoadErrorMessage
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.post.PostBody
import es.aria_on_the_planet.quasar.ui.component.post.PostCallbacks
import es.aria_on_the_planet.quasar.ui.component.post.PostDetailAuthor
import es.aria_on_the_planet.quasar.ui.component.post.PostDetailDefaults
import es.aria_on_the_planet.quasar.ui.component.post.formatDetailedTime
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailLoading
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailTitle
import es.aria_on_the_planet.quasar.ui.component.scaffold.backIcon
import es.aria_on_the_planet.quasar.ui.component.scaffold.backLabel
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import kotlinx.coroutines.launch

/**
 * 編集履歴の版 (ポスト詳細の「Edited」→ 版の一覧のシートから)。版は左右のスワイプかトップバーの ‹ › で前後に送る
 * (左が古い)。各版は投稿者・本文 (CW は開いたまま)・投票の選択肢・メディアと、その版になった日時。
 *
 * @param initialIndex 最初に出す版 (古い順の 0 始まり)。
 */
@Composable
fun PostHistoryScreen(
    viewModel: PostHistoryViewModel,
    statusId: String,
    initialIndex: Int,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
    onMediaClick: (MediaClick) -> Unit,
    onHashtagClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PostHistoryContent(
        state = state,
        statusId = statusId,
        initialIndex = initialIndex,
        onBack = onBack,
        onRetry = viewModel::retry,
        callbacks = PostCallbacks(onUserClick = onUserClick, onMediaClick = onMediaClick, onHashtagClick = onHashtagClick),
    )
}

/** [PostHistoryScreen] の中身 (ViewModel を持たない。デバッグ用ギャラリーからも使う)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostHistoryContent(
    state: PostHistoryState,
    statusId: String,
    initialIndex: Int,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    callbacks: PostCallbacks,
) {
    val versions = state.versions
    val pagerState = rememberPagerState(initialPage = initialIndex) { versions?.size ?: 0 }
    val scope = rememberCoroutineScope()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    fun go(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page, animationSpec = spatialSpec) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(backIcon(), backLabel())
                    }
                },
                title = {
                    val title = stringResource(R.string.post_history_title)
                    if (versions.isNullOrEmpty()) {
                        Text(title)
                    } else {
                        DetailTitle(
                            name = title,
                            emojis = emptyList(),
                            subtitle = stringResource(R.string.post_history_position, pagerState.targetPage + 1, versions.size),
                        )
                    }
                },
                actions = {
                    if (!versions.isNullOrEmpty()) {
                        val page = pagerState.targetPage
                        IconButton(onClick = { go(page - 1) }, enabled = page > 0) {
                            Icon(Symbols.Outlined.ChevronLeft, stringResource(R.string.post_history_previous))
                        }
                        IconButton(onClick = { go(page + 1) }, enabled = page < versions.lastIndex) {
                            Icon(Symbols.Outlined.ChevronRight, stringResource(R.string.post_history_next))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                versions == null && state.error != null -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoadErrorMessage(state.error, R.string.post_history_error)
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.post_history_retry)) }
                }
                versions == null -> DetailLoading()
                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { it },
                ) { index ->
                    PostVersionPage(versions[index], statusId, index, callbacks)
                }
            }
        }
    }
}

/** 版 1 つ: 投稿者 / CW・本文 / 投票の選択肢 / メディア / その版になった日時。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PostVersionPage(version: PostVersion, statusId: String, index: Int, callbacks: PostCallbacks) {
    val post = remember(version, statusId, index) { version.asPost(statusId, index) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PostDetailAuthor(post, callbacks.onUserClick)
        Spacer(Modifier.size(12.dp))
        // 版どうしを見比べるので CW は開いたまま、長文も折りたたまない。投票は件数が無いので選択肢だけを別に出す
        PostBody(
            post,
            callbacks = callbacks,
            mediaHeight = PostDetailDefaults.MediaHeight,
            spacing = 12.dp,
            textStyle = MaterialTheme.typography.bodyLarge,
            extras = false,
            fixed = true,
            translatable = false,
        )
        version.pollOptions?.let { options ->
            PollOptions(options, version.emojis, Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.size(12.dp))
        val context = LocalContext.current
        // ロケールや 12/24 時間設定の変更で再計算されるよう Configuration を読む
        LocalConfiguration.current
        val time = formatDetailedTime(version.createdAt, is24Hour = DateFormat.is24HourFormat(context))
        val kind = stringResource(if (index == 0) R.string.post_history_kind_created else R.string.post_history_kind_edited)
        Text(
            text = "$time · $kind",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 版の投票の選択肢 (件数は返らないので文言だけ)。投票のボタンと同じ枠線のピル型を押せない形で並べる。 */
@Composable
private fun PollOptions(options: List<String>, emojis: List<CustomEmoji>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { title ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmojiText(
                    text = title,
                    emojis = emojis,
                    postContent = true,
                    style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                )
            }
        }
    }
}
