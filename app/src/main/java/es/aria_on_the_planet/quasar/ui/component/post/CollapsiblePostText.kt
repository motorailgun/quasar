package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.ui.component.layout.CollapsibleContent
import es.aria_on_the_planet.quasar.ui.component.layout.ShowMoreToggle
import es.aria_on_the_planet.quasar.ui.component.layout.rememberExpandedState
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText

private val FadeHeight = 40.dp

/**
 * 長いポスト本文 (HTML) を [maxCollapsedLines] 行で折りたたむ ([CollapsibleContent] の上の薄いラッパー)。
 * 超える場合は下端をフェードさせ、CW と同じ「Show more >」で展開できるようにする。
 *
 * 本文のスタイルは行の高さが固定で、カスタム絵文字も行の高さに揃えているため、
 * 「N 行」は「行の高さ × N」の高さと一致する。そのため高さで判定する [CollapsibleContent] をそのまま使う。
 *
 * @param stateKey 展開状態を保持するキー (ポストの statusId など)。
 * @param maxCollapsedLines `null` なら折りたたまない。
 * @param showToggle `false` なら「Show more」を出さず、折りたたんだ高さで切ってフェードさせるだけ (開けない)。
 * @param onLinkClick 本文のリンクのタップ ([EmojiText] の同名の引数)。
 */
@Composable
fun CollapsiblePostText(
    html: String,
    emojis: List<CustomEmoji>,
    stateKey: String,
    maxCollapsedLines: Int?,
    modifier: Modifier = Modifier,
    showToggle: Boolean = true,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    onLinkClick: ((url: String, text: String?) -> Unit)? = null,
) {
    val lineHeight = lineHeightDp(style)
    if (maxCollapsedLines == null || lineHeight == null) {
        EmojiText(
            text = html, emojis = emojis, isHtml = true, postContent = true, style = style, modifier = modifier,
            onLinkClick = onLinkClick,
        )
        return
    }
    CollapsibleContent(
        collapsedMaxHeight = lineHeight * maxCollapsedLines,
        modifier = modifier,
        expanded = rememberExpandedState(stateKey),
        fadeHeight = FadeHeight,
        toggle = { expanded, onToggle -> if (showToggle) ShowMoreToggle(expanded = expanded, onToggle = onToggle) },
    ) {
        EmojiText(
            text = html, emojis = emojis, isHtml = true, postContent = true, style = style, onLinkClick = onLinkClick,
        )
    }
}

/** 1 行の高さ。行の高さが sp で指定されていなければ文字サイズの 1.2 倍。どちらも不明なら `null`。 */
@Composable
private fun lineHeightDp(style: TextStyle): Dp? = with(LocalDensity.current) {
    when {
        style.lineHeight.isSp -> style.lineHeight.toDp()
        style.fontSize.isSp -> (style.fontSize * 1.2f).toDp()
        else -> null
    }
}
