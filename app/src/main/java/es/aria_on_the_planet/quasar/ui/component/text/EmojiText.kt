package es.aria_on_the_planet.quasar.ui.component.text

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.ui.component.image.ImageKind
import es.aria_on_the_planet.quasar.ui.component.image.RemoteImage

/**
 * カスタム絵文字に対応した [Text] のラッパー。
 * アカウント名・ポスト本文・プロフィール文など、`:shortcode:` を含みうるテキストは必ずこれを使う。
 *
 * @param isHtml `true` の場合 [text] を Mastodon の HTML として解釈する (リンク付き)。
 * @param postContent ポストの中身 (本文・CW・投票・通知の抜粋) なら `true`。絵文字のアニメーションの設定が
 *   「本文」と「それ以外」で分かれているため。表示名・bio・フィールド等は `false`。
 * @param onLinkClick HTML のリンクのタップ (href とリンクの文字列)。`null` なら
 *   [androidx.compose.ui.platform.LocalUriHandler] でそのまま開く。メンション・ハッシュタグを画面内で開く等、
 *   リンク先によって振り分けるときに渡す。
 * @param diffFrom 前の版の文字列 ([text] と同じ形式)。渡すと前の版との差分 (単語単位) を [diffStyles] で書き込む
 *   (足された所・消された所の両方を出す。[withDiffFrom])。編集履歴用。[emojis] には両方の版の絵文字を渡す。
 * @param language 中身の言語 (ポストの `language` 等)。文字のロケールにして、漢字をその言語の字形で出す ([withContentLanguage])。
 */
@Composable
fun EmojiText(
    text: String,
    emojis: List<CustomEmoji>,
    modifier: Modifier = Modifier,
    isHtml: Boolean = false,
    postContent: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onLinkClick: ((url: String, text: String?) -> Unit)? = null,
    diffFrom: String? = null,
    diffStyles: TextDiffStyles? = if (diffFrom != null) textDiffStyles() else null,
    language: String? = null,
) {
    val textStyle = remember(style, language) { style.withContentLanguage(language) }
    val linkColor = MaterialTheme.colorScheme.primary
    // ラムダは毎回変わりうるので、変換をやり直さないよう最新の値を参照する固定のリスナーにする
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val handlesLinks = onLinkClick != null
    val annotated = remember(text, emojis, isHtml, linkColor, handlesLinks, diffFrom, diffStyles) {
        // リスナーにはリンクしか渡らないので、リンクの文字列 (`#tag` 等) は変換後に引けるようにしておく
        var linkTexts = emptyMap<String, String>()
        val listener = if (isHtml && handlesLinks) {
            LinkInteractionListener { link ->
                (link as? LinkAnnotation.Url)?.let { currentOnLinkClick?.invoke(it.url, linkTexts[it.url]) }
            }
        } else {
            null
        }
        fun annotate(source: String): AnnotatedString = if (isHtml) {
            AnnotatedString.fromHtml(
                htmlString = simplifyMastodonHtml(source),
                linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                linkInteractionListener = listener,
            ).trimWhitespace()
        } else {
            AnnotatedString(source)
        }
        val current = annotate(text)
        val base = if (diffFrom != null && diffStyles != null) current.withDiffFrom(annotate(diffFrom), diffStyles) else current
        if (listener != null) linkTexts = base.linkTexts()
        base.replaceEmojiShortcodes(emojis)
    }
    // 絵文字の高さは行の高さに合わせ、幅は読み込み済みの画像の縦横比から決める (未読み込みは正方形)
    val height = emojiHeight(style)
    val ratios = emojis.map { EmojiAspectRatios[it.url] ?: 1f }
    val kind = if (postContent) ImageKind.PostEmoji else ImageKind.OtherEmoji
    val inlineContent = remember(emojis, ratios, height, kind) { emojiInlineContent(emojis, ratios, height, kind) }
    Text(
        text = annotated,
        modifier = modifier,
        style = textStyle,
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
    kind: ImageKind,
): Map<String, InlineTextContent> =
    emojis.zip(ratios).associate { (emoji, ratio) ->
        EmojiInlineIdPrefix + emoji.shortcode to InlineTextContent(
            placeholder = Placeholder(
                width = height * ratio,
                height = height,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            RemoteImage(
                url = emoji.url,
                staticUrl = emoji.staticUrl,
                kind = kind,
                contentDescription = ":${emoji.shortcode}:",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                // 枠は縦横比が分かるまで仮の正方形なので、その幅で縮めて読ませない
                fitHeight = true,
                // 静止版を読んだときも縦横比は同じなので、アニメーション版の URL で記録する
                onLoaded = { size -> EmojiAspectRatios.record(emoji.url, size.width, size.height) },
            )
        }
    }

/** URL リンクの href → リンクの文字列。同じ href が複数あれば最初のもの。 */
private fun AnnotatedString.linkTexts(): Map<String, String> =
    getLinkAnnotations(0, length)
        .mapNotNull { range -> (range.item as? LinkAnnotation.Url)?.let { it.url to text.substring(range.start, range.end) } }
        .distinctBy { it.first }
        .toMap()

/** HTML 変換で前後に残る空白・改行を取り除く。 */
private fun AnnotatedString.trimWhitespace(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start == -1) return AnnotatedString("")
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return if (start == 0 && end == length) this else subSequence(start, end)
}
