package es.aria_on_the_planet.quasar.ui.component.post

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.card.LinkPreviewCard
import es.aria_on_the_planet.quasar.ui.component.media.MediaAttachments
import es.aria_on_the_planet.quasar.ui.component.poll.PollView
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText

object PostDetailDefaults {
    val MediaHeight: Dp = 240.dp
}

/**
 * ポスト詳細画面の主役となるポスト。[PostCard] と同じ要素を、本文を大きく・詳細な投稿日時付きで表示する。
 */
@Composable
fun PostDetail(
    post: Post,
    modifier: Modifier = Modifier,
    mediaHeight: Dp = PostDetailDefaults.MediaHeight,
    onUserClick: (User) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onMediaClick: (MediaClick) -> Unit = {},
    onVote: (suspend (Post, List<Int>) -> Boolean)? = null,
    onAction: OnPostAction? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        post.boostedBy?.let { booster ->
            BoostedByLabel(
                booster,
                Modifier
                    .padding(bottom = 8.dp)
                    .clickable { onUserClick(booster) },
            )
        }
        // 投稿者 (タップでプロフィール) と、右端に公開範囲のアイコン
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // タップ領域は投稿者の部分だけにする
            Box(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.clickable { onUserClick(post.author) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserIcon(user = post.author)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        EmojiText(
                            text = post.author.displayName.ifBlank { post.author.screenName },
                            emojis = post.author.emojis,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "@${post.author.fullScreenName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (post.visibility.hasIcon) {
                Spacer(Modifier.width(8.dp))
                VisibilityIcon(post.visibility, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(12.dp))
        var expanded by rememberContentWarningExpanded(post)
        post.contentWarning?.let { cw ->
            ContentWarning(
                text = cw,
                emojis = post.emojis,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        // CW で隠すのは本文・投票・リンクプレビュー・引用。メディアは sensitive で別に制御する
        AnimatedVisibility(visible = expanded) {
            Column {
                EmojiText(
                    text = post.text,
                    emojis = post.emojis,
                    isHtml = true,
                    postContent = true,
                    style = MaterialTheme.typography.bodyLarge,
                )
                post.poll?.let { poll ->
                    PollView(
                        poll = poll,
                        onVote = onVote?.let { vote -> { choices -> vote(post, choices) } },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        if (post.mediaAttachments.isNotEmpty()) {
            MediaAttachments(
                attachments = post.mediaAttachments,
                height = mediaHeight,
                sensitive = post.sensitive,
                artworkUrl = post.author.iconUrl,
                onClick = { tap -> onMediaClick(tap.toClick(post)) },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                post.card?.let { card ->
                    LinkPreviewCard(card = card, modifier = Modifier.padding(top = 12.dp))
                }
                post.quote?.let { quote ->
                    QuotedPost(
                        quote = quote,
                        mediaHeight = mediaHeight * 0.75f,
                        maxHeight = mediaHeight,
                        onPostClick = onPostClick,
                        onMediaClick = onMediaClick,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        val context = LocalContext.current
        // ロケールや 12/24 時間設定の変更で再計算されるよう Configuration を読む
        LocalConfiguration.current
        val linkColor = MaterialTheme.colorScheme.primary
        val time = formatDetailedTime(post.createdAt, is24Hour = DateFormat.is24HourFormat(context))
        Text(
            // 日時 · 投稿に使ったアプリ (website があればリンク)
            text = buildAnnotatedString {
                append(time)
                post.application?.let { app ->
                    append(" · ")
                    if (app.website != null) {
                        withLink(LinkAnnotation.Url(app.website, TextLinkStyles(SpanStyle(color = linkColor)))) {
                            append(app.name)
                        }
                    } else {
                        append(app.name)
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        PostActionRow(post, onAction = onAction)
    }
}
