package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.layout.ShowMoreToggle
import es.aria_on_the_planet.quasar.ui.component.layout.rememberExpandedState
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText

/** CW の展開状態。ポストごとに保持し、構成変更でも維持する。CW が無いポストでは常に展開扱い。 */
@Composable
fun rememberContentWarningExpanded(post: Post): MutableState<Boolean> =
    rememberExpandedState(key = post.statusId, initiallyExpanded = post.contentWarning == null)

/**
 * Content Warning。本文の代わりに警告文を出し、その下の「Show more >」で本文を展開する。
 * 本文側の表示切り替えは呼び出し側が [expanded] を見て行う (PostBody は [ContentWarningBody]、QuotedPost は `if (expanded)`)。
 */
@Composable
fun ContentWarning(
    text: String,
    emojis: List<CustomEmoji>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Column(modifier) {
        EmojiText(text = text, emojis = emojis, postContent = true, style = style)
        ShowMoreToggle(expanded = expanded, onToggle = onToggle)
    }
}

/**
 * CW で隠す部分 (本文・投票・リンクプレビュー・引用)。CW のあるポスト ([hasWarning]) だけ [expanded] に合わせて
 * 開閉をアニメーションし、無いポストは `AnimatedVisibility` を作らずにそのまま出す。一覧では項目が画面に入るたびに
 * 組み立てるので、使わないアニメーションの分だけ重くなる (ポスト 1 件の組み立ての 2 割ほどを占めていた)。
 */
@Composable
fun ContentWarningBody(
    hasWarning: Boolean,
    expanded: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (hasWarning) {
        AnimatedVisibility(visible = expanded) { Column(content = content) }
    } else {
        Column(content = content)
    }
}
