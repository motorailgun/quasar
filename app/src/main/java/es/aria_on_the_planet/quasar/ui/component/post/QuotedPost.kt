package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.modifier.bottomFadingEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.Quote
import es.aria_on_the_planet.quasar.domain.model.QuoteState
import es.aria_on_the_planet.quasar.ui.component.media.MediaAttachments
import es.aria_on_the_planet.quasar.ui.component.poll.PollView
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import es.aria_on_the_planet.quasar.ui.component.time.LocalNow

private val QuoteShape = RoundedCornerShape(16.dp)
private val QuoteFadeHeight = 32.dp

/**
 * 引用されたポストを、枠付きで縮小表示する。表示できない状態の場合はその旨のプレースホルダを出す。
 * 引用の引用はさらに入れ子にせず、プレースホルダ 1 行にとどめる。
 *
 * @param maxHeight 本文側 (ヘッダ・本文・投票) の高さ上限。超える分は下端をフェードさせて切る。
 *   メディアはこの上限とは独立して常に表示する。`null` なら制限なし。
 */
@Composable
fun QuotedPost(
    quote: Quote,
    modifier: Modifier = Modifier,
    mediaHeight: Dp = PostCardDefaults.MediaHeight * 0.75f,
    onPostClick: (Post) -> Unit = {},
    onMediaClick: (MediaClick) -> Unit = {},
    maxHeight: Dp? = null,
    nested: Boolean = false,
) {
    val post = quote.post
    if (quote.state != QuoteState.Accepted || post == null || nested) {
        QuotePlaceholder(
            text = stringResource(if (nested && post != null) R.string.quote_nested else quote.state.messageRes()),
            modifier = modifier,
        )
        return
    }
    OutlinedCard(
        onClick = { onPostClick(post) },
        shape = QuoteShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            // 本文側 (ヘッダ・本文・投票) は高さ上限を超える分を切り、下端をフェードさせる。
            // 全文は引用元の詳細画面で読む
            var bodyHeight by remember { mutableIntStateOf(0) }
            val maxPx = maxHeight?.let { with(LocalDensity.current) { it.roundToPx() } }
            val overflows = maxPx != null && bodyHeight > maxPx
            Box(
                Modifier
                    .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
                    .clipToBounds()
                    .bottomFadingEdge(height = QuoteFadeHeight, strength = { if (overflows) 1f else 0f }),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .onSizeChanged { bodyHeight = it.height },
                ) {
                    QuoteHeader(post)
                    var expanded by rememberContentWarningExpanded(post)
                    post.contentWarning?.let { cw ->
                        ContentWarning(
                            text = cw,
                            emojis = post.emojis,
                            expanded = expanded,
                            onToggle = { expanded = !expanded },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (expanded) {
                        if (post.text.isNotBlank()) {
                            EmojiText(
                                text = post.text,
                                emojis = post.emojis,
                                isHtml = true,
                                postContent = true,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        // 引用内の投票は閲覧のみ (結果表示)
                        post.poll?.let { PollView(poll = it, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
            }
            // メディアは本文の高さ上限とは独立して常に表示する
            if (post.mediaAttachments.isNotEmpty()) {
                MediaAttachments(
                    attachments = post.mediaAttachments,
                    height = mediaHeight,
                    sensitive = post.sensitive,
                    artworkUrl = post.author.iconUrl,
                    onClick = { tap -> onMediaClick(tap.toClick(post)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            post.quote?.let { inner ->
                QuotedPost(quote = inner, nested = true, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun QuoteHeader(post: Post) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        UserIcon(user = post.author, size = 20.dp)
        Spacer(Modifier.width(6.dp))
        EmojiText(
            text = post.author.displayName.ifBlank { post.author.screenName },
            emojis = post.author.emojis,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "@${post.author.screenName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // 長い acct で時刻が隠れないよう別にする
        Text(
            text = " · ${relativeTime(post.createdAt, LocalNow.current)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun QuotePlaceholder(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = QuoteShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

private fun QuoteState.messageRes(): Int = when (this) {
    QuoteState.Pending -> R.string.quote_pending
    QuoteState.Deleted -> R.string.quote_deleted
    QuoteState.BlockedAccount, QuoteState.BlockedDomain -> R.string.quote_blocked
    QuoteState.MutedAccount -> R.string.quote_muted
    QuoteState.Accepted, QuoteState.Rejected, QuoteState.Revoked, QuoteState.Unauthorized -> R.string.quote_unavailable
}
