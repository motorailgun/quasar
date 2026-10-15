package es.aria_on_the_planet.quasar.ui.component.text

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji

/**
 * カスタム絵文字に対応した [Text] のラッパー。
 * アカウント名・ポスト本文・プロフィール文など、`:shortcode:` を含みうるテキストは必ずこれを使う。
 *
 * @param isHtml `true` の場合 [text] を Mastodon の HTML として解釈する (リンク付き)。
 */
@Composable
fun EmojiText(
    text: String,
    emojis: List<CustomEmoji>,
    modifier: Modifier = Modifier,
    isHtml: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, emojis, isHtml, linkColor) {
        val base = if (isHtml) {
            AnnotatedString.fromHtml(
                htmlString = simplifyMastodonHtml(text),
                linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor)),
            ).trimEnd()
        } else {
            AnnotatedString(text)
        }
        base.replaceEmojiShortcodes(emojis)
    }
    val inlineContent = remember(emojis) { emojiInlineContent(emojis) }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent,
    )
}

private fun emojiInlineContent(emojis: List<CustomEmoji>): Map<String, InlineTextContent> =
    emojis.associate { emoji ->
        EmojiInlineIdPrefix + emoji.shortcode to InlineTextContent(
            placeholder = Placeholder(
                width = 1.2.em,
                height = 1.2.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            AsyncImage(model = emoji.url, contentDescription = ":${emoji.shortcode}:")
        }
    }

private fun AnnotatedString.trimEnd(): AnnotatedString {
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return if (end == length) this else subSequence(0, end)
}
