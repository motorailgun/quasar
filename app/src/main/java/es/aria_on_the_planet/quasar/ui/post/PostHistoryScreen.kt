package es.aria_on_the_planet.quasar.ui.post

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.ListDiffKind
import es.aria_on_the_planet.quasar.domain.model.ListDiffItem
import es.aria_on_the_planet.quasar.domain.model.PostVersion
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.asPost
import es.aria_on_the_planet.quasar.domain.model.diffList
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.list.LoadErrorMessage
import es.aria_on_the_planet.quasar.ui.component.media.MediaAttachments
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.post.PostCallbacks
import es.aria_on_the_planet.quasar.ui.component.post.PostDetailAuthor
import es.aria_on_the_planet.quasar.ui.component.post.PostDetailDefaults
import es.aria_on_the_planet.quasar.ui.component.post.formatDetailedTime
import es.aria_on_the_planet.quasar.ui.component.post.rememberPostLinkHandler
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailLoading
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailTitle
import es.aria_on_the_planet.quasar.ui.component.scaffold.backIcon
import es.aria_on_the_planet.quasar.ui.component.scaffold.backLabel
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import es.aria_on_the_planet.quasar.ui.theme.extendedColors
import kotlinx.coroutines.launch

/**
 * 編集履歴の版 (ポスト詳細の「Edited」→ 版の一覧のシートから)。版は左右のスワイプかトップバーの ‹ › で前後に送る
 * (左が古い)。各版は投稿者・本文 (CW は開いたまま)・投票の選択肢・メディアと、その版になった日時。2 つ目以降の版は
 * 1 つ前の版との差分を色付けする ([PostVersionPage])。
 *
 * @param initialIndex 最初に出す版 (古い順の 0 始まり)。
 * @param language ポストの言語 (今の版の `language`)。版 (`StatusEdit`) には言語が無いので、全部の版をこの言語で出す
 *   ([EmojiText] の同名の引数)。
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
    language: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PostHistoryContent(
        state = state,
        statusId = statusId,
        initialIndex = initialIndex,
        onBack = onBack,
        onRetry = viewModel::retry,
        callbacks = PostCallbacks(onUserClick = onUserClick, onMediaClick = onMediaClick, onHashtagClick = onHashtagClick),
        language = language,
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
    language: String? = null,
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
                    PostVersionPage(versions[index], versions.getOrNull(index - 1), statusId, index, callbacks, language)
                }
            }
        }
    }
}

/**
 * 版 1 つ: 投稿者 / CW / 本文 / 投票の選択肢 / メディア / その版になった日時。[previous] (1 つ前の版) があれば、それとの差分を
 * 出す: 本文・CW は単語単位で足された所 (緑) と消された所 (赤・取り消し線) を両方 ([EmojiText] の `diffFrom`)、投票の選択肢は
 * 足された・消された選択肢 (投票ごとなら欄全体) に帯を、文言だけ変わった選択肢は文言に同じ色を敷き、添付は足された・外されたものの左上に印 ([MediaDiffBadge])。外された添付・選択肢も元の位置に出す。
 * 版どうしを見比べるので CW は開いたまま、長文も折りたたまない。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PostVersionPage(
    version: PostVersion,
    previous: PostVersion?,
    statusId: String,
    index: Int,
    callbacks: PostCallbacks,
    language: String?,
) {
    val media = remember(version, previous) {
        if (previous == null) {
            version.mediaAttachments.map { ListDiffItem(it, ListDiffKind.Equal) }
        } else {
            diffList(previous.mediaAttachments, version.mediaAttachments, key = { it.id })
        }
    }
    // メディアビューアで外された添付も見られるよう、並べた順の添付を持たせる
    val post = remember(version, statusId, index, media) {
        version.asPost(statusId, index).copy(mediaAttachments = media.map { it.item })
    }
    val emojis = remember(version, previous) { (version.emojis + previous?.emojis.orEmpty()).distinctBy { it.shortcode } }
    val pollOptions = remember(version, previous) {
        when {
            previous == null -> version.pollOptions?.map { ListDiffItem(it, ListDiffKind.Equal) }
            previous.pollOptions == null && version.pollOptions == null -> null
            // 選択肢は位置で対応するので、同じ所で文言が変わったものは「変更」(消して足したことにしない)
            else -> diffList(previous.pollOptions.orEmpty(), version.pollOptions.orEmpty(), pairChanges = true)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PageHorizontalPadding, vertical = 12.dp),
    ) {
        PostDetailAuthor(post, callbacks.onUserClick)
        Spacer(Modifier.size(12.dp))
        val contentWarning = version.contentWarning
        val previousWarning = previous?.contentWarning
        if (contentWarning != null || previousWarning != null) {
            // CW ごと足された・外された版は、アイコンごと塗る (外された CW も消された所として出す)
            val whole = when {
                previous == null -> null
                previousWarning == null -> ListDiffKind.Inserted
                contentWarning == null -> ListDiffKind.Deleted
                else -> null
            }
            VersionContentWarning(
                text = contentWarning ?: previousWarning.orEmpty(),
                diffFrom = if (whole == null) previousWarning else null,
                whole = whole,
                emojis = emojis,
                language = language,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        EmojiText(
            text = version.text,
            emojis = emojis,
            isHtml = true,
            postContent = true,
            style = MaterialTheme.typography.bodyLarge,
            onLinkClick = rememberPostLinkHandler(post, callbacks.onUserClick, callbacks.onHashtagClick),
            diffFrom = previous?.text,
            language = language,
        )
        if (!pollOptions.isNullOrEmpty()) {
            // 投票ごと足された・外された版は、選択肢ごとではなく投票の欄全体に色を敷く
            val wholePoll = when {
                previous == null -> null
                previous.pollOptions == null && version.pollOptions != null -> ListDiffKind.Inserted
                previous.pollOptions != null && version.pollOptions == null -> ListDiffKind.Deleted
                else -> null
            }
            PollOptions(pollOptions, emojis, wholePoll, language, Modifier.padding(top = 12.dp))
        }
        if (media.isNotEmpty()) {
            val kinds = media.associate { it.item.id to it.kind }
            MediaAttachments(
                attachments = post.mediaAttachments,
                height = PostDetailDefaults.MediaHeight,
                sensitive = version.sensitive,
                artworkUrl = post.author.iconUrl,
                onClick = { tap -> callbacks.onMediaClick(tap.toClick(post)) },
                overlay = { attachment -> kinds[attachment.id]?.let { kind -> MediaDiffBadge(kind) } },
                modifier = Modifier.padding(top = 12.dp),
            )
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

/**
 * 開いたままの CW (警告のアイコン + 警告文)。[diffFrom] (前の版の警告文) があれば単語単位の差分を書き込む。CW ごと
 * 足された・外された版 ([whole]) は、警告文だけでなくアイコンまで差分の色で塗る (外されたものは取り消し線も)。
 */
@Composable
private fun VersionContentWarning(
    text: String,
    diffFrom: String?,
    whole: ListDiffKind?,
    emojis: List<CustomEmoji>,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.extendedColors
    val roles = when (whole) {
        ListDiffKind.Inserted -> colors.diffAdded
        ListDiffKind.Deleted -> colors.diffRemoved
        ListDiffKind.Equal, ListDiffKind.Changed, null -> null
    }
    val style = MaterialTheme.typography.bodyLarge
    Row(modifier.then(if (roles != null) Modifier.background(roles.container) else Modifier)) {
        // 1 行目の文字の高さの中央に合わせる (bodyLarge は行の高さ 24sp)
        Icon(
            Symbols.Outlined.Warning,
            contentDescription = null,
            tint = roles?.onContainer ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp, end = 6.dp)
                .size(20.dp),
        )
        EmojiText(
            text = text,
            emojis = emojis,
            postContent = true,
            color = roles?.onContainer ?: Color.Unspecified,
            style = if (whole == ListDiffKind.Deleted) style.copy(textDecoration = TextDecoration.LineThrough) else style,
            diffFrom = diffFrom,
            language = language,
        )
    }
}

/**
 * 版の投票の選択肢 (件数は返らないので文言だけ)。投票のボタンと同じ枠線のピル型を押せない形で並べる。前の版と比べて:
 * * 選択肢ごと足された・消されたもの: ピルより一回り大きい四角形 (本文の差分の色と同じく角は丸めない) を差分の色で後ろに敷く
 *   ([diffBackdrop]。選択肢の間隔にはみ出すだけで高さは変えない)。消されたものは取り消し線。
 * * 同じ位置で文言だけが変わったもの ([ListDiffKind.Changed]): ピルはそのままで文言に本文と同じ単語単位の差分。
 * * 投票ごと足された・外された版 ([wholePoll]): 選択肢ごとではなく、投票の欄全体に同じ形の四角形を敷く。
 */
@Composable
private fun PollOptions(
    options: List<ListDiffItem<String>>,
    emojis: List<CustomEmoji>,
    wholePoll: ListDiffKind?,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.extendedColors
    fun rolesOf(kind: ListDiffKind?) = when (kind) {
        ListDiffKind.Inserted -> colors.diffAdded
        ListDiffKind.Deleted -> colors.diffRemoved
        ListDiffKind.Equal, ListDiffKind.Changed, null -> null
    }
    val wholeRoles = rolesOf(wholePoll)
    Column(
        modifier
            .fillMaxWidth()
            .then(if (wholeRoles != null) Modifier.diffBackdrop(wholeRoles.container) else Modifier),
        verticalArrangement = Arrangement.spacedBy(PollOptionSpacing),
    ) {
        options.forEach { option ->
            val roles = rolesOf(option.kind)
            val textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    // 欄全体に敷いたときは選択肢ごとには敷かない
                    .then(if (roles != null && wholeRoles == null) Modifier.diffBackdrop(roles.container) else Modifier)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmojiText(
                    text = option.item,
                    emojis = emojis,
                    postContent = true,
                    color = roles?.onContainer ?: Color.Unspecified,
                    style = if (option.kind == ListDiffKind.Deleted) {
                        textStyle.copy(textDecoration = TextDecoration.LineThrough)
                    } else {
                        textStyle
                    },
                    diffFrom = option.previous,
                    language = language,
                )
            }
        }
    }
}

/**
 * 差分の色の、自分より左右に [DiffBackdropOutsetHorizontal]、上下に [DiffBackdropOutsetVertical] ずつ大きい四角形を
 * 後ろに描く。角は本文の差分の色 (文字の背景) と揃えて丸めない。描くだけで大きさ・位置は変えない (変わらない選択肢と
 * 並び・高さが揃ったまま)。上下のはみ出しは選択肢の間隔 ([PollOptionSpacing]) の半分なので、続いた選択肢の四角形は接する。
 */
private fun Modifier.diffBackdrop(color: Color): Modifier = drawBehind {
    val h = DiffBackdropOutsetHorizontal.toPx()
    val v = DiffBackdropOutsetVertical.toPx()
    drawRect(color = color, topLeft = Offset(-h, -v), size = Size(size.width + h * 2, size.height + v * 2))
}

/** 版のページの左右の余白 ([PostVersionPage])。 */
private val PageHorizontalPadding = 16.dp
private val PollOptionSpacing = 8.dp
private val DiffBackdropOutsetHorizontal = 6.dp
private val DiffBackdropOutsetVertical = 4.dp

/** 添付の左上の印: 前の版から足された (＋、緑) / 外された (－、赤)。変わらないものには出さない。 */
@Composable
private fun BoxScope.MediaDiffBadge(kind: ListDiffKind) {
    val colors = MaterialTheme.extendedColors
    val (roles, icon, label) = when (kind) {
        // 添付は ID で比べるので Changed にはならない
        ListDiffKind.Equal, ListDiffKind.Changed -> return
        ListDiffKind.Inserted -> Triple(colors.diffAdded, Symbols.Filled.Add, R.string.post_history_media_added)
        ListDiffKind.Deleted -> Triple(colors.diffRemoved, Symbols.Filled.Remove, R.string.post_history_media_removed)
    }
    Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .size(28.dp)
            .background(roles.color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = stringResource(label), tint = roles.onColor, modifier = Modifier.size(20.dp))
    }
}
