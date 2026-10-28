package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.replyRecipients
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.post.PostCallbacks
import es.aria_on_the_planet.quasar.ui.component.post.PostCard
import es.aria_on_the_planet.quasar.ui.component.post.PostCardMode
import es.aria_on_the_planet.quasar.ui.component.post.PostExcerpt
import es.aria_on_the_planet.quasar.ui.component.post.PostMedia
import es.aria_on_the_planet.quasar.ui.component.post.QuoteHeader
import es.aria_on_the_planet.quasar.ui.component.post.ThreadLineDefaults
import es.aria_on_the_planet.quasar.ui.component.post.threadLines

/**
 * クイック投稿バーの本文の上に出す返信先。縮小したヘッダ (アイコン・名前・時刻) + 本文 [BodyMaxLines] 行 +
 * メディアのサムネイル + 返信をやめる ✕。CW 付きのポストは本文の代わりに警告文を出す。
 */
@Composable
fun ReplyTargetPreview(
    post: Post,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onMediaClick: (MediaClick) -> Unit = {},
) {
    Surface(
        shape = PreviewShape,
        // 下地はバーのぼかし。透明の Surface は文字色を決めないので明示する
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuoteHeader(post, Modifier.weight(1f))
                CancelReplyButton(onCancel, size = 36.dp)
            }
            PostExcerpt(
                post,
                style = MaterialTheme.typography.bodySmall,
                maxLines = BodyMaxLines,
                modifier = Modifier.padding(end = 8.dp),
            )
            PostMedia(post, PreviewMediaHeight, onMediaClick, Modifier.padding(top = 6.dp, end = 8.dp))
        }
    }
}

/**
 * フルの投稿画面の上に出す返信先 (X の返信画面の形)。
 *
 * ```
 * (icon) 名前 @acct · 時刻        [✕]
 *   │    本文 (長文は折りたたみ)
 *   │    [メディア]
 *   │    Replying to @acct
 * (自分のアイコン) 本文欄 …          ← 呼び出し側 (Composer)。縦線はここまで届く
 * ```
 *
 * 下端まで縦線を描くので、直下に置く投稿欄のアイコンの上端とぴったり付ける (間に余白を空けない)。
 *
 * @param myAcct ログイン中のアカウントの acct。「Replying to」の宛先から除く。
 */
@Composable
fun ReplyContext(
    post: Post,
    myAcct: String?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onMediaClick: (MediaClick) -> Unit = {},
) {
    val line = Modifier.threadLines(
        xs = listOf(ThreadLineDefaults.AvatarCenterX),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
    Column(modifier.fillMaxWidth()) {
        PostCard(
            post = post,
            mode = PostCardMode.ReplyContext,
            mediaHeight = ContextMediaHeight,
            callbacks = PostCallbacks(onMediaClick = onMediaClick),
            authorTrailing = { CancelReplyButton(onCancel, size = 32.dp) },
            modifier = Modifier.threadLines(
                xs = listOf(ThreadLineDefaults.AvatarCenterX),
                color = MaterialTheme.colorScheme.outlineVariant,
                startY = ThreadLineDefaults.AvatarTopY,
            ),
        )
        // PostCard の本文と同じ列 (左余白 16dp + アイコン 40dp + 12dp) に揃える
        ReplyingToLabel(
            accts = replyRecipients(post, myAcct).ifEmpty { listOf(post.author.screenName) },
            modifier = line.padding(start = 68.dp, end = 16.dp),
        )
        // 投稿欄のアイコンまで縦線を繋ぐ
        Spacer(line.fillMaxWidth().height(ThreadLineDefaults.AvatarTopY))
    }
}

/** 「Replying to @acct …」。宛先は primary 色。 */
@Composable
fun ReplyingToLabel(accts: List<String>, modifier: Modifier = Modifier) {
    val names = accts.joinToString(" ") { "@$it" }
    val full = stringResource(R.string.composer_replying_to, names)
    val accent = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        append(full)
        val start = full.indexOf(names)
        if (start >= 0) addStyle(SpanStyle(color = accent), start, start + names.length)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun CancelReplyButton(onClick: () -> Unit, size: Dp) {
    IconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(
            Symbols.Filled.Close,
            contentDescription = stringResource(R.string.composer_cancel_reply),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val PreviewShape = RoundedCornerShape(16.dp)
private const val BodyMaxLines = 2
private val PreviewMediaHeight = 48.dp
private val ContextMediaHeight = 120.dp
