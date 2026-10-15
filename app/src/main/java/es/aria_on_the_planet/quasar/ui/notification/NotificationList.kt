package es.aria_on_the_planet.quasar.ui.notification

import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.NotificationGroup
import es.aria_on_the_planet.quasar.domain.model.NotificationType
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.image.rememberBlurHashPainter
import es.aria_on_the_planet.quasar.ui.component.list.LoadMoreEffect
import es.aria_on_the_planet.quasar.ui.component.list.StickToTopEffect
import es.aria_on_the_planet.quasar.ui.component.post.PostCard
import es.aria_on_the_planet.quasar.ui.component.post.UserIcon
import es.aria_on_the_planet.quasar.ui.component.post.relativeTime
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import es.aria_on_the_planet.quasar.ui.theme.extendedColors

/**
 * 通知の一覧。サーバ側グループ ([NotificationEntry.Single]) と、ユーザー単位にまとめた行
 * ([NotificationEntry.AccountReactions]) を並べる。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationList(
    entries: List<NotificationEntry>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onUserClick: (User) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onMediaClick: (MediaAttachment) -> Unit = {},
    onVote: (suspend (Post, List<Int>) -> Boolean)? = null,
) {
    LoadMoreEffect(state, onLoadMore)
    // 先頭にいるときにストリーミングの新着が入ったら見せる
    StickToTopEffect(state, entries.firstOrNull()?.key)
    LazyColumn(modifier = modifier, state = state) {
        items(entries, key = { it.key }, contentType = { it.contentType }) { entry ->
            Column(Modifier.animateItem()) {
                when (entry) {
                    is NotificationEntry.AccountReactions -> AccountReactionsItem(entry, onUserClick = onUserClick, onPostClick = onPostClick)
                    is NotificationEntry.Single -> NotificationItem(
                        group = entry.group,
                        onUserClick = onUserClick,
                        onPostClick = onPostClick,
                        onMediaClick = onMediaClick,
                        onVote = onVote,
                    )
                }
                HorizontalDivider()
            }
        }
        if (isLoadingMore) {
            item(key = "loading_more", contentType = "loading") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }
        }
    }
}

/**
 * 通知 1 件。
 * リアクション (お気に入り・ブースト) とフォローは、した側のアイコンと 1 行の説明、対象ポストの控えめな抜粋で
 * コンパクトに表示する。メンション等は「誰が何をしたか」の見出しと対象のポストをそのまま表示する。
 */
@Composable
fun NotificationItem(
    group: NotificationGroup,
    modifier: Modifier = Modifier,
    onUserClick: (User) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onMediaClick: (MediaAttachment) -> Unit = {},
    onVote: (suspend (Post, List<Int>) -> Boolean)? = null,
) {
    if (group.type.isCompact) {
        CompactNotification(group, modifier, onUserClick, onPostClick)
        return
    }
    // メンションは本文に @ユーザー名 が入るので、見出しを付けずポストだけを出す
    if (group.type == NotificationType.Mention && group.post != null) {
        PostCard(
            post = group.post,
            modifier = modifier,
            onUserClick = onUserClick,
            onPostClick = onPostClick,
            onMediaClick = onMediaClick,
            onVote = onVote,
            showReplyIndicator = false,
        )
        return
    }
    Column(modifier.fillMaxWidth()) {
        NotificationHeader(group, onUserClick)
        val post = group.post
        if (post != null) {
            PostCard(
                post = post,
                onUserClick = onUserClick,
                onPostClick = onPostClick,
                onMediaClick = onMediaClick,
                onVote = onVote,
                showReplyIndicator = true,
            )
        } else {
            group.accounts.firstOrNull()?.let { AccountRow(it, onUserClick) }
        }
    }
}

/** コンパクト表示にする種別 (した側が主役で、対象ポストは補足でよいもの)。 */
private val NotificationType.isCompact: Boolean
    get() = when (this) {
        NotificationType.Favourite, NotificationType.Reblog,
        NotificationType.Follow, NotificationType.FollowRequest,
        -> true
        else -> false
    }

private val CompactTypeIconSize = 28.dp
private val CompactAvatarSize = 32.dp
/** コンパクト表示で並べるアイコンの最大数。 */
private const val MaxCompactAvatars = 6
private val ThumbnailSize = 56.dp

/**
 * X の通知に倣ったコンパクト表示。
 * 左に種別アイコン、右にした側のアイコン → 「**名前** 〜しました · 時刻」→ 対象ポストの抜粋 (低強調)。
 * メディアがあれば右端にサムネイル。
 */
@Composable
private fun CompactNotification(
    group: NotificationGroup,
    modifier: Modifier,
    onUserClick: (User) -> Unit,
    onPostClick: (Post) -> Unit,
) {
    val post = group.post
    val first = group.accounts.firstOrNull()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { if (post != null) onPostClick(post) else first?.let(onUserClick) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // PostCard のアイコン列 (40dp) に合わせて種別アイコンを置く
        Box(Modifier.width(40.dp), contentAlignment = Alignment.TopEnd) {
            Icon(
                group.type.filledIcon(),
                contentDescription = null,
                tint = group.type.accentColor(),
                modifier = Modifier.size(CompactTypeIconSize),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // した側のアイコン列。個々のアイコンのタップでそのユーザーのプロフィール
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                group.accounts.take(MaxCompactAvatars).forEach { user ->
                    UserIcon(
                        user = user,
                        size = CompactAvatarSize,
                        modifier = Modifier.clickable { onUserClick(user) },
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            // 名前だけ太字にするため HTML で組む (名前はエスケープ済み)
            val boldName = first?.let { "<b>${TextUtils.htmlEncode(it.displayName.ifBlank { it.screenName })}</b>" }.orEmpty()
            val time = group.latestAt?.let { " · " + relativeTime(it) }.orEmpty()
            EmojiText(
                text = group.type.groupMessage(boldName, others = group.count - 1) + time,
                emojis = first?.emojis.orEmpty(),
                isHtml = true,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (post != null) {
                // 対象ポストは補足情報なので控えめに。CW があれば本文ではなく警告文を出す
                val cw = post.contentWarning
                EmojiText(
                    text = cw ?: post.text,
                    emojis = post.emojis,
                    isHtml = cw == null,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        post?.mediaAttachments?.firstOrNull()?.let { media ->
            Spacer(Modifier.width(12.dp))
            MediaThumbnail(media, sensitive = post.sensitive)
        }
    }
}

/**
 * 1 人のユーザーが複数のポストにしたリアクションをまとめた行 (「〇〇 favourited 3 of your posts」)。
 * 見た目はコンパクト表示と同じで、抜粋とサムネイルは最も新しいポストのもの。行のタップでそのポストを開く。
 */
@Composable
private fun AccountReactionsItem(
    entry: NotificationEntry.AccountReactions,
    onUserClick: (User) -> Unit,
    onPostClick: (Post) -> Unit,
) {
    val account = entry.account
    val post = entry.latestPost
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (post != null) onPostClick(post) else onUserClick(account) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.TopEnd) {
            Icon(
                entry.type.filledIcon(),
                contentDescription = null,
                tint = entry.type.accentColor(),
                modifier = Modifier.size(CompactTypeIconSize),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            UserIcon(
                user = account,
                size = CompactAvatarSize,
                modifier = Modifier.clickable { onUserClick(account) },
            )
            Spacer(Modifier.size(8.dp))
            val boldName = "<b>${TextUtils.htmlEncode(account.displayName.ifBlank { account.screenName })}</b>"
            val time = entry.latestAt?.let { " · " + relativeTime(it) }.orEmpty()
            EmojiText(
                text = entry.type.multiplePostsMessage(boldName, entry.members.size) + time,
                emojis = account.emojis,
                isHtml = true,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (post != null) {
                val cw = post.contentWarning
                EmojiText(
                    text = cw ?: post.text,
                    emojis = post.emojis,
                    isHtml = cw == null,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        post?.mediaAttachments?.firstOrNull()?.let { media ->
            Spacer(Modifier.width(12.dp))
            MediaThumbnail(media, sensitive = post.sensitive)
        }
    }
}

/** 対象ポストの先頭メディアの小さなサムネイル。閲覧注意ならぼかし (BlurHash) のみ。 */
@Composable
private fun MediaThumbnail(media: MediaAttachment, sensitive: Boolean) {
    val placeholder = rememberBlurHashPainter(media.blurhash)
    val shape = RoundedCornerShape(8.dp)
    val thumbModifier = Modifier
        .size(ThumbnailSize)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    if (sensitive || media.previewUrl == null) {
        Box(thumbModifier) {
            if (placeholder != null) {
                Image(placeholder, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    } else {
        AsyncImage(
            model = media.previewUrl,
            contentDescription = media.description,
            placeholder = placeholder,
            error = placeholder,
            contentScale = ContentScale.Crop,
            modifier = thumbModifier,
        )
    }
}

@Composable
private fun NotificationHeader(group: NotificationGroup, onUserClick: (User) -> Unit) {
    val account = group.accounts.firstOrNull()
    val name = account?.let { it.displayName.ifBlank { it.screenName } }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // PostCard のアイコン列 (40dp) に揃えてアイコンを右寄せで置く
        Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterEnd) {
            Icon(
                group.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        EmojiText(
            text = group.type.message(name),
            emojis = account?.emojis.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = account != null) { account?.let(onUserClick) },
        )
        group.latestAt?.let { at ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = relativeTime(at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** ポストを伴わない通知 (フォロー等) の、操作したアカウントの行。 */
@Composable
private fun AccountRow(user: User, onUserClick: (User) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUserClick(user) }
            .padding(start = 68.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserIcon(user = user)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            EmojiText(
                text = user.displayName.ifBlank { user.screenName },
                emojis = user.emojis,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${user.fullScreenName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** コンパクト表示用の塗りアイコン。 */
private fun NotificationType.filledIcon(): ImageVector = when (this) {
    NotificationType.Favourite -> Icons.Filled.Star
    NotificationType.Reblog -> Icons.Filled.Repeat
    NotificationType.Follow, NotificationType.FollowRequest -> Icons.Filled.Person
    else -> icon()
}

/** 種別ごとのアクセント色 (アクション行の操作済み表示と同じ Custom colors)。 */
@Composable
private fun NotificationType.accentColor(): Color = when (this) {
    NotificationType.Favourite -> MaterialTheme.extendedColors.favourite.color
    NotificationType.Reblog -> MaterialTheme.extendedColors.reblog.color
    NotificationType.Follow, NotificationType.FollowRequest -> MaterialTheme.extendedColors.follow.color
    else -> MaterialTheme.colorScheme.secondary
}

private fun NotificationType.icon(): ImageVector = when (this) {
    NotificationType.Mention -> Icons.Outlined.AlternateEmail
    NotificationType.Status -> Icons.Outlined.Notifications
    NotificationType.Reblog -> Icons.Outlined.Repeat
    NotificationType.Follow, NotificationType.FollowRequest -> Icons.Outlined.PersonAdd
    NotificationType.Favourite -> Icons.Outlined.Star
    NotificationType.Poll -> Icons.Outlined.Poll
    NotificationType.Update, NotificationType.QuotedUpdate -> Icons.Outlined.Edit
    NotificationType.Quote -> Icons.Outlined.FormatQuote
    NotificationType.Unknown -> Icons.Outlined.Notifications
}

/** ユーザー単位の行の説明文 (「〇〇 favourited N of your posts」)。 */
@Composable
private fun NotificationType.multiplePostsMessage(name: String, posts: Int): String = when (this) {
    NotificationType.Reblog -> pluralStringResource(R.plurals.notification_reblog_posts, posts, name, posts)
    else -> pluralStringResource(R.plurals.notification_favourite_posts, posts, name, posts)
}

private val NotificationEntry.contentType: String
    get() = when (this) {
        is NotificationEntry.AccountReactions -> "account_reactions"
        is NotificationEntry.Single -> if (group.type.isCompact) "compact" else "post"
    }

/**
 * グループの説明文。他に [others] 人いれば「A and N others 〜」。
 * まとめられる種別 (favourite / reblog / follow) 以外は単体の文言を使う。
 */
@Composable
private fun NotificationType.groupMessage(name: String, others: Int): String = when {
    others <= 0 -> message(name)
    this == NotificationType.Favourite -> pluralStringResource(R.plurals.notification_favourite_group, others, name, others)
    this == NotificationType.Reblog -> pluralStringResource(R.plurals.notification_reblog_group, others, name, others)
    this == NotificationType.Follow -> pluralStringResource(R.plurals.notification_follow_group, others, name, others)
    else -> message(name)
}

@Composable
private fun NotificationType.message(name: String): String = when (this) {
    NotificationType.Mention -> stringResource(R.string.notification_mention, name)
    NotificationType.Status -> stringResource(R.string.notification_status, name)
    NotificationType.Reblog -> stringResource(R.string.notification_reblog, name)
    NotificationType.Follow -> stringResource(R.string.notification_follow, name)
    NotificationType.FollowRequest -> stringResource(R.string.notification_follow_request, name)
    NotificationType.Favourite -> stringResource(R.string.notification_favourite, name)
    NotificationType.Poll -> stringResource(R.string.notification_poll)
    NotificationType.Update -> stringResource(R.string.notification_update, name)
    NotificationType.Quote -> stringResource(R.string.notification_quote, name)
    NotificationType.QuotedUpdate -> stringResource(R.string.notification_quoted_update, name)
    NotificationType.Unknown -> stringResource(R.string.notification_unknown, name)
}
