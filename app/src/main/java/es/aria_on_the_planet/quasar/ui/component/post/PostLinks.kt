package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalUriHandler
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostLinkTarget
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.linkTarget

/**
 * ポスト本文のリンクのタップ ([es.aria_on_the_planet.quasar.ui.component.text.EmojiText] の `onLinkClick`)。
 * 行き先は [linkTarget] で決め、メンションは [onUserClick] でプロフィールへ、ハッシュタグは [onHashtagClick]
 * (`null` ならブラウザ)、それ以外はブラウザで開く。
 */
@Composable
internal fun rememberPostLinkHandler(
    post: Post,
    onUserClick: (User) -> Unit,
    onHashtagClick: ((String) -> Unit)?,
): (url: String, text: String?) -> Unit {
    val uriHandler = LocalUriHandler.current
    val currentOnUserClick by rememberUpdatedState(onUserClick)
    val currentOnHashtagClick by rememberUpdatedState(onHashtagClick)
    return remember(post, uriHandler) {
        { url, text ->
            when (val target = post.linkTarget(url, text)) {
                is PostLinkTarget.Account -> currentOnUserClick(target.mention.asUser())
                is PostLinkTarget.Tag -> currentOnHashtagClick?.invoke(target.hashtag.name) ?: uriHandler.openUri(url)
                is PostLinkTarget.External -> uriHandler.openUri(url)
            }
        }
    }
}
