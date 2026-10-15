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
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.card.LinkPreviewCard
import es.aria_on_the_planet.quasar.ui.component.media.MediaAttachments
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import java.time.Duration
import java.time.Instant

object PostCardDefaults {
    val MediaHeight: Dp = 160.dp
}

/**
 * ポスト 1 件。タイムライン・プロフィール等で再利用する stateless コンポーネント。
 *
 * @param mediaHeight 添付メディアの表示高さ。各メディアはこの高さに揃えられる。
 * @param pinned プロフィールのピン留めとして表示する場合 `true`。小さな「Pinned」ラベルを付ける。
 */
@Composable
fun PostCard(
    post: Post,
    modifier: Modifier = Modifier,
    mediaHeight: Dp = PostCardDefaults.MediaHeight,
    pinned: Boolean = false,
    onUserClick: (User) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onMediaClick: (MediaAttachment) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPostClick(post) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (pinned) {
            PinnedLabel(Modifier.padding(start = 52.dp, bottom = 2.dp))
        }
        post.boostedBy?.let { booster ->
            BoostedByLabel(
                booster,
                Modifier
                    .padding(start = 52.dp, bottom = 4.dp)
                    .clickable { onUserClick(booster) },
            )
        }
        Row {
            UserIcon(
                user = post.author,
                modifier = Modifier.clickable { onUserClick(post.author) },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AuthorLine(post, onAuthorClick = { onUserClick(post.author) })
                Spacer(Modifier.size(4.dp))
                EmojiText(
                    text = post.text,
                    emojis = post.emojis,
                    isHtml = true,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (post.mediaAttachments.isNotEmpty()) {
                    MediaAttachments(
                        attachments = post.mediaAttachments,
                        height = mediaHeight,
                        onClick = onMediaClick,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                post.card?.let { card ->
                    LinkPreviewCard(card = card, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.size(8.dp))
                PostActionRow(post)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserIcon(user: User, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    AsyncImage(
        model = user.iconUrl,
        contentDescription = user.screenName,
        modifier = modifier
            .size(size)
            .clip(MaterialShapes.Cookie9Sided.toShape()),
    )
}

@Composable
private fun AuthorLine(post: Post, onAuthorClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 表示名 + screenName をまとめてタップ領域にする (時刻は含めない)
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable(onClick = onAuthorClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
        }
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
private fun PinnedLabel(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.PushPin,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.post_pinned),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** リプライ・ブースト・リアクション・ブックマークの件数と、自分が操作済みかどうかを表示する。 */
@Composable
private fun PostActionRow(post: Post) {
    val viewer = post.viewer
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PostActionIndicator(
            icon = Icons.AutoMirrored.Outlined.Reply,
            label = stringResource(R.string.post_replies),
            count = post.repliesCount,
        )
        PostActionIndicator(
            icon = if (viewer.reblogged) Icons.Filled.Repeat else Icons.Outlined.Repeat,
            label = stringResource(R.string.post_boosts),
            count = post.boostsCount,
            active = viewer.reblogged,
            activeColor = MaterialTheme.colorScheme.tertiary,
        )
        PostActionIndicator(
            icon = if (viewer.favourited) Icons.Filled.Star else Icons.Outlined.StarOutline,
            label = stringResource(R.string.post_reactions),
            count = post.reactionsCount,
            active = viewer.favourited,
            activeColor = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        PostActionIndicator(
            icon = if (viewer.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            label = stringResource(R.string.post_bookmark),
            active = viewer.bookmarked,
            activeColor = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun PostActionIndicator(
    icon: ImageVector,
    label: String,
    count: Int? = null,
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    val activeState = stringResource(R.string.post_action_done)
    Row(
        modifier = Modifier.semantics { if (active) stateDescription = activeState },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = color)
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            Text(text = count.toString(), style = MaterialTheme.typography.labelMedium, color = color)
        }
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
