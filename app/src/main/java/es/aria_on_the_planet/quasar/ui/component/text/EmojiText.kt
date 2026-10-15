package es.aria_on_the_planet.quasar.ui.component.text

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
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
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, emojis, isHtml, linkColor) {
        val base = if (isHtml) {
            AnnotatedString.fromHtml(
                htmlString = simplifyMastodonHtml(text),
                linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor)),
            ).trimWhitespace()
        } else {
            AnnotatedString(text)
        }
        base.replaceEmojiShortcodes(emojis)
    }
    // 絵文字の高さは行の高さに合わせ、幅は読み込み済みの画像の縦横比から決める (未読み込みは正方形)
    val height = emojiHeight(style)
    val ratios = emojis.map { EmojiAspectRatios[it.url] ?: 1f }
    val inlineContent = remember(emojis, ratios, height) { emojiInlineContent(emojis, ratios, height) }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent,
        onTextLayout = onTextLayout,
    )
}

private fun emojiInlineContent(
    emojis: List<CustomEmoji>,
    ratios: List<Float>,
    height: TextUnit,
): Map<String, InlineTextContent> =
    emojis.zip(ratios).associate { (emoji, ratio) ->
        EmojiInlineIdPrefix + emoji.shortcode to InlineTextContent(
            placeholder = Placeholder(
                width = height * ratio,
                height = height,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            AsyncImage(
                model = emoji.url,
                contentDescription = ":${emoji.shortcode}:",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    val size = state.painter.intrinsicSize
                    EmojiAspectRatios.record(emoji.url, size.width, size.height)
                },
            )
        }
    }

/** HTML 変換で前後に残る空白・改行を取り除く。 */
private fun AnnotatedString.trimWhitespace(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start == -1) return AnnotatedString("")
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return if (start == 0 && end == length) this else subSequence(start, end)
}
