package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.card.LinkPreviewCard
import es.aria_on_the_planet.quasar.ui.component.media.MediaAttachments
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.poll.PollView
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText

/**
 * ポストの中身: CW / 本文 / 投票 / 添付メディア / リンクプレビュー / 引用。[PostCard] と [PostDetail] で共通。
 * CW で隠すのは本文・投票・リンクプレビュー・引用で、メディアは CW とは独立に sensitive で隠す。
 *
 * @param spacing 本文の後の各部品 (投票・メディア・カード・引用) の上の間隔。
 * @param collapsedMaxLines 長文をこの行数で折りたたむ。`null` なら全文。
 * @param extras 投票・リンクプレビュー・引用も出すか (返信先の表示 [PostCardMode.ReplyContext] では出さない)。
 * @param fixed 開閉させない ([PostCardMode.Preview])。CW は開いたまま、長文は開閉のボタン無しで切ってフェードさせる。
 */
@Composable
internal fun PostBody(
    post: Post,
    callbacks: PostCallbacks,
    mediaHeight: Dp,
    spacing: Dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    collapsedMaxLines: Int? = null,
    extras: Boolean = true,
    fixed: Boolean = false,
) {
    var expanded by rememberContentWarningExpanded(post)
    // 開いたままなら隠す部分は無いのと同じ (アニメーションの入れ物も作らない)
    val hasWarning = post.contentWarning != null && !fixed
    if (fixed) {
        post.contentWarning?.let { cw ->
            FixedContentWarning(cw, post, textStyle, Modifier.padding(bottom = spacing))
        }
    } else post.contentWarning?.let { cw ->
        ContentWarning(
            text = cw,
            emojis = post.emojis,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            style = textStyle,
        )
    }
    ContentWarningBody(hasWarning = hasWarning, expanded = expanded) {
        CollapsiblePostText(
            html = post.text,
            emojis = post.emojis,
            stateKey = post.statusId,
            maxCollapsedLines = collapsedMaxLines,
            showToggle = !fixed,
            style = textStyle,
            onLinkClick = rememberPostLinkHandler(post, callbacks.onUserClick, callbacks.onHashtagClick),
        )
        if (extras) {
            post.poll?.let { poll ->
                PollView(
                    poll = poll,
                    onVote = callbacks.onVote?.let { vote -> { choices -> vote(post, choices) } },
                    modifier = Modifier.padding(top = spacing),
                )
            }
        }
    }
    PostMedia(post, mediaHeight, callbacks.onMediaClick, Modifier.padding(top = spacing))
    // カード・引用の無いポストでは CW の入れ物ごと作らない
    if (extras && (post.card != null || post.quote != null)) {
        ContentWarningBody(hasWarning = hasWarning, expanded = expanded) {
            post.card?.let { card ->
                LinkPreviewCard(card = card, modifier = Modifier.padding(top = spacing))
            }
            post.quote?.let { quote ->
                QuotedPost(
                    quote = quote,
                    callbacks = callbacks,
                    mediaHeight = mediaHeight * 0.75f,
                    // 引用の本文側の高さは添付画像と同じ高さまで
                    maxHeight = mediaHeight,
                    modifier = Modifier.padding(top = spacing),
                )
            }
        }
    }
}

/** 開いたままの CW ([PostBody] の `fixed`)。本文と見分けられるよう、警告文の左に警告のアイコンを付ける。 */
@Composable
private fun FixedContentWarning(text: String, post: Post, style: TextStyle, modifier: Modifier = Modifier) {
    Row(modifier) {
        // 1 行目の文字の高さの中央に合わせる (bodyMedium は行の高さ 20sp)
        Icon(
            Symbols.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp, end = 6.dp).size(18.dp),
        )
        EmojiText(text = text, emojis = post.emojis, postContent = true, style = style)
    }
}

/** ポストの添付メディアの並び。無ければ何も出さない。タップはポストを添えて [onMediaClick] へ。 */
@Composable
internal fun PostMedia(post: Post, height: Dp, onMediaClick: (MediaClick) -> Unit, modifier: Modifier = Modifier) {
    if (post.mediaAttachments.isEmpty()) return
    MediaAttachments(
        attachments = post.mediaAttachments,
        height = height,
        sensitive = post.sensitive,
        artworkUrl = post.author.iconUrl,
        onClick = { tap -> onMediaClick(tap.toClick(post)) },
        modifier = modifier,
    )
}

/** ポストの控えめな抜粋 (通知・返信先の縮小表示)。CW があれば本文ではなく警告文を出す。 */
@Composable
internal fun PostExcerpt(
    post: Post,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val cw = post.contentWarning
    EmojiText(
        text = cw ?: post.text,
        emojis = post.emojis,
        isHtml = cw == null,
        postContent = true,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
