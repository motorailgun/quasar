package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import java.time.Duration
import java.time.Instant

/** ポスト 1 件。タイムライン・プロフィール等で再利用する stateless コンポーネント。 */
@Composable
fun PostCard(
    post: Post,
    modifier: Modifier = Modifier,
    onUserClick: (User) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPostClick(post) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        post.boostedBy?.let { booster ->
            BoostedByLabel(booster, Modifier.padding(start = 52.dp, bottom = 4.dp))
        }
        Row {
            UserIcon(
                user = post.author,
                modifier = Modifier.clickable { onUserClick(post.author) },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AuthorLine(post)
                Spacer(Modifier.size(4.dp))
                EmojiText(
                    text = post.text,
                    emojis = post.emojis,
                    isHtml = true,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Counter(Icons.Outlined.Repeat, stringResource(R.string.post_boosts), post.boostsCount)
                    Counter(Icons.Outlined.StarOutline, stringResource(R.string.post_reactions), post.reactionsCount)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserIcon(user: User, modifier: Modifier = Modifier) {
    AsyncImage(
        model = user.iconUrl,
        contentDescription = user.screenName,
        modifier = modifier
            .size(40.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape()),
    )
}

@Composable
private fun AuthorLine(post: Post) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EmojiText(
            text = post.author.displayName.ifBlank { post.author.screenName },
            emojis = post.author.emojis,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "@${post.author.screenName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = relativeTime(post.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoostedByLabel(booster: User, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Repeat,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(4.dp))
        EmojiText(
            text = stringResource(R.string.post_boosted_by, booster.displayName.ifBlank { booster.screenName }),
            emojis = booster.emojis,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Counter(icon: ImageVector, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun relativeTime(instant: Instant, now: Instant = Instant.now()): String {
    val d = Duration.between(instant, now)
    return when {
        d.toMinutes() < 1 -> "${d.seconds.coerceAtLeast(0)}s"
        d.toHours() < 1 -> "${d.toMinutes()}m"
        d.toDays() < 1 -> "${d.toHours()}h"
        else -> "${d.toDays()}d"
    }
}
