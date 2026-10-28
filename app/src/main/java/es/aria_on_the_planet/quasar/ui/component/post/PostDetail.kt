package es.aria_on_the_planet.quasar.ui.component.post

import android.text.format.DateFormat
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.ui.component.user.UserIcon
import es.aria_on_the_planet.quasar.ui.component.user.UserName

object PostDetailDefaults {
    val MediaHeight: Dp = 240.dp
}

/**
 * ポスト詳細画面の主役となるポスト。[PostCard] と同じ要素を、本文を大きく・詳細な投稿日時付きで表示する。
 *
 * @param callbacks ポストへの操作の口 ([PostCallbacks])。
 * @param menu 投稿者の行の右端 (公開範囲のアイコンの右) に出すもの (ポストのメニュー [PostMenu])。
 * @param reactionDetail アクション行のブースト・お気に入りの件数を「N boosts」「M favourites」で出し、件数のタップで
 *   [onReactionCountClick] (リアクションしたユーザーの一覧) を呼ぶ ([PostActionRow] の同名の引数)。
 */
@Composable
fun PostDetail(
    post: Post,
    modifier: Modifier = Modifier,
    callbacks: PostCallbacks = PostCallbacks.None,
    mediaHeight: Dp = PostDetailDefaults.MediaHeight,
    menu: (@Composable () -> Unit)? = null,
    reactionDetail: Boolean = false,
    onReactionCountClick: ((Post, PostActionType) -> Unit)? = null,
) {
    val onUserClick = callbacks.onUserClick
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
        // 投稿者 (タップでプロフィール) と、右端に公開範囲のアイコン (+ メニュー)
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
                        UserName(post.author, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
            menu?.invoke()
        }
        Spacer(Modifier.size(12.dp))
        // 全文を読む場所なので折りたたまない
        PostBody(
            post,
            callbacks = callbacks,
            mediaHeight = mediaHeight,
            spacing = 12.dp,
            textStyle = MaterialTheme.typography.bodyLarge,
        )
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
        PostActionRow(
            post,
            onAction = callbacks.onAction,
            onReply = callbacks.onReply,
            reactionDetail = reactionDetail,
            onReactionCountClick = onReactionCountClick?.let { click -> { type -> click(post, type) } },
        )
    }
}
