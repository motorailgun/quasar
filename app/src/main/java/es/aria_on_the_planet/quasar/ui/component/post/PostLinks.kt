package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalUriHandler
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.findByUrl

/**
 * ポスト本文のリンクのタップ ([es.aria_on_the_planet.quasar.ui.component.text.EmojiText] の `onLinkClick`)。
 * メンション ([Post.mentions] の `url` と一致) なら [onUserClick] でプロフィールへ、それ以外はブラウザで開く。
 */
@Composable
internal fun rememberPostLinkHandler(post: Post, onUserClick: (User) -> Unit): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    val currentOnUserClick by rememberUpdatedState(onUserClick)
    val mentions = post.mentions
    return remember(mentions, uriHandler) {
        { href ->
            val mention = mentions.findByUrl(href)
            if (mention != null) currentOnUserClick(mention.asUser()) else uriHandler.openUri(href)
        }
    }
}
