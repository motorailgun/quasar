package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.foundation.layout.Column
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
 * 本文側の表示切り替えは呼び出し側が [expanded] を見て行う。
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
        EmojiText(text = text, emojis = emojis, style = style)
        ShowMoreToggle(expanded = expanded, onToggle = onToggle)
    }
}
