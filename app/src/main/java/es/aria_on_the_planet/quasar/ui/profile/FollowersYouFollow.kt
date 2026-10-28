package es.aria_on_the_planet.quasar.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.post.UserIcon
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import es.aria_on_the_planet.quasar.ui.component.user.avatarShape

private const val MaxIcons = 3
private const val MaxNames = 2
private val IconSize = 24.dp
private val IconRing = 2.dp
private val IconOverlapStep = 16.dp

/** 「自分がフォローしている人のうち、このアカウントをフォローしている人」の要約 (X の Followed by ...)。 */
@Composable
fun FollowersYouFollow(users: List<User>, modifier: Modifier = Modifier) {
    if (users.isEmpty()) return
    val icons = users.take(MaxIcons)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // 左のアイコンが上に来るように重ねる
        Box {
            icons.forEachIndexed { index, user ->
                Box(
                    Modifier
                        .offset(x = IconOverlapStep * index)
                        .zIndex((icons.size - index).toFloat())
                        .background(MaterialTheme.colorScheme.surface, avatarShape(ring = IconRing))
                        .padding(IconRing),
                ) {
                    UserIcon(user = user, size = IconSize)
                }
            }
        }
        Spacer(Modifier.width(IconOverlapStep * (icons.size - 1) + 8.dp))
        EmojiText(
            text = followedByText(users),
            emojis = users.take(MaxNames).flatMap { it.emojis },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun followedByText(users: List<User>): String {
    val names = users.take(MaxNames).map { it.nameOrAcct }
    val others = users.size - names.size
    return when {
        others > 0 -> pluralStringResource(R.plurals.profile_followed_by_others, others, names[0], names[1], others)
        names.size == 2 -> stringResource(R.string.profile_followed_by_two, names[0], names[1])
        else -> stringResource(R.string.profile_followed_by_one, names[0])
    }
}
