package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText

/** CW の展開状態。ポストごとに保持し、構成変更でも維持する。CW が無いポストでは常に展開扱い。 */
@Composable
fun rememberContentWarningExpanded(post: Post): MutableState<Boolean> =
    rememberSaveable(post.statusId) { mutableStateOf(post.contentWarning == null) }

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
    // `>` を展開時は上向きに回す
    val rotation by animateFloatAsState(if (expanded) -90f else 0f, label = "cwChevron")
    Column(modifier) {
        EmojiText(text = text, emojis = emojis, style = style)
        Row(
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(if (expanded) R.string.cw_show_less else R.string.cw_show_more),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
            )
        }
    }
}
