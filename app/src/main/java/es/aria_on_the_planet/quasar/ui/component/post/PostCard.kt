package es.aria_on_the_planet.quasar.ui.component.post

import es.aria_on_the_planet.quasar.ui.theme.DisabledContentAlpha
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.card.LinkPreviewCard
import es.aria_on_the_planet.quasar.ui.component.media.MediaAttachments
import es.aria_on_the_planet.quasar.ui.component.poll.PollView
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings
import es.aria_on_the_planet.quasar.ui.theme.extendedColors
import java.time.Duration
import java.time.Instant
import es.aria_on_the_planet.quasar.ui.component.time.LocalNow

object PostCardDefaults {
    val MediaHeight: Dp = 160.dp
}

/**
 * ポスト 1 件。タイムライン・プロフィール等で再利用する stateless コンポーネント。
 *
 * @param mediaHeight 添付メディアの表示高さ。各メディアはこの高さに揃えられる。
 * @param pinned プロフィールのピン留めとして表示する場合 `true`。小さな「Pinned」ラベルを付ける。
 * @param onVote 投票する。`null` なら投票できず、投票は結果表示になる (未ログイン時など)。
 * @param onAction アクション行のブースト・お気に入り・ブックマークが押されたとき。`null` なら表示のみ (未ログイン時など)。
 * @param showReplyIndicator リプライのとき「Reply to @user」を出すか。スレッド表示など、
 *   返信関係が別の形で分かる場所では `false` にする。
 */
@Composable
fun PostCard(
    post: Post,
    modifier: Modifier = Modifier,
    mediaHeight: Dp = PostCardDefaults.MediaHeight,
    pinned: Boolean = false,
    onUserClick: (User) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onMediaClick: (MediaClick) -> Unit = {},
    onVote: (suspend (Post, List<Int>) -> Boolean)? = null,
    onAction: OnPostAction? = null,
    showReplyIndicator: Boolean = true,
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
        if (showReplyIndicator && post.inReplyToId != null) {
            ReplyToLabel(post.inReplyToAcct, Modifier.padding(start = 52.dp, bottom = 4.dp))
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
                var expanded by rememberContentWarningExpanded(post)
                post.contentWarning?.let { cw ->
                    ContentWarning(
                        text = cw,
                        emojis = post.emojis,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                    )
                }
                // CW で隠すのは本文・投票。メディアは CW とは独立に sensitive で制御する
                AnimatedVisibility(visible = expanded) {
                    Column {
                        // 長い本文は設定の行数で折りたたむ
                        CollapsiblePostText(
                            html = post.text,
                            emojis = post.emojis,
                            stateKey = post.statusId,
                            maxCollapsedLines = LocalDisplaySettings.current.longPostMaxLines,
                        )
                        post.poll?.let { poll ->
                            PollView(
                                poll = poll,
                                onVote = onVote?.let { vote -> { choices -> vote(post, choices) } },
                                modifier = Modifier.padding(top = 8.dp),
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
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                // リンクプレビュー・引用も本文の一部として CW で隠す
                AnimatedVisibility(visible = expanded) {
                    Column {
                        post.card?.let { card ->
                            LinkPreviewCard(card = card, modifier = Modifier.padding(top = 8.dp))
                        }
                        post.quote?.let { quote ->
                            QuotedPost(
                                quote = quote,
                                mediaHeight = mediaHeight * 0.75f,
                                // 引用の高さは添付画像と同じ高さまで
                                maxHeight = mediaHeight,
                                onPostClick = onPostClick,
                                onMediaClick = onMediaClick,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                PostActionRow(post, onAction = onAction)
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
        // 表示名 + screenName + 時刻は左に詰め、公開範囲のアイコンは右端に寄せる
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            AuthorNameAndTime(post, onAuthorClick)
        }
        if (post.visibility.hasIcon) {
            Spacer(Modifier.width(8.dp))
            // アクション行のアイコンと同じ幅の枠の中央に置き、右端のブックマークと縦に揃える
            Box(Modifier.size(ActionIconSize), contentAlignment = Alignment.Center) {
                VisibilityIcon(post.visibility, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RowScope.AuthorNameAndTime(post: Post, onAuthorClick: () -> Unit) {
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
        text = relativeTime(post.createdAt, LocalNow.current),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReplyToLabel(acct: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Outlined.Reply,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (acct != null) stringResource(R.string.post_reply_to, "@$acct") else stringResource(R.string.post_reply),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun BoostedByLabel(booster: User, modifier: Modifier = Modifier) {
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

/**
 * リプライ・ブースト・リアクション・ブックマークの件数と、自分が操作済みかどうかを表示する。
 * [onAction] があればブースト・お気に入り・ブックマークを押せる (リプライは未実装で表示のみ)。
 */
@Composable
fun PostActionRow(post: Post, modifier: Modifier = Modifier, onAction: OnPostAction? = null) {
    val viewer = post.viewer
    Row(
        // 押せる範囲を広げた分 (左右の余白) だけ外にはみ出させ、アイコンの位置は余白なしのときと揃える
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val extra = (ActionPaddingHorizontal * 2).roundToPx()
                val placeable = measurable.measure(constraints.copy(
                    minWidth = constraints.minWidth + extra,
                    maxWidth = constraints.maxWidth + extra,
                ))
                layout(constraints.maxWidth, placeable.height) {
                    placeable.place(-ActionPaddingHorizontal.roundToPx(), 0)
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp - ActionPaddingHorizontal * 2),
    ) {
        PostActionButton(
            icon = Icons.AutoMirrored.Outlined.Reply,
            label = stringResource(R.string.post_replies),
            count = post.repliesCount,
        )
        PostActionButton(
            icon = if (viewer.reblogged) Icons.Filled.Repeat else Icons.Outlined.Repeat,
            label = stringResource(R.string.post_boosts),
            count = post.boostsCount,
            active = viewer.reblogged,
            activeColor = MaterialTheme.extendedColors.reblog.color,
            // フォロワー限定・ダイレクトはブーストできない (既にブースト済みなら取り消しはできるようにする)
            enabled = post.visibility.boostable || viewer.reblogged,
            onClick = onAction?.let { { it(post, PostActionType.Boost) } },
        )
        PostActionButton(
            icon = if (viewer.favourited) Icons.Filled.Star else Icons.Outlined.StarOutline,
            label = stringResource(R.string.post_reactions),
            count = post.reactionsCount,
            active = viewer.favourited,
            activeColor = MaterialTheme.extendedColors.favourite.color,
            onClick = onAction?.let { { it(post, PostActionType.Favourite) } },
        )
        Spacer(Modifier.weight(1f))
        PostActionButton(
            icon = if (viewer.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            label = stringResource(R.string.post_bookmark),
            active = viewer.bookmarked,
            activeColor = MaterialTheme.colorScheme.secondary,
            onClick = onAction?.let { { it(post, PostActionType.Bookmark) } },
        )
    }
}

/** アクション行のボタンの左右の余白 (押せる範囲)。 */
private val ActionPaddingHorizontal = 6.dp

/**
 * アクション行の 1 項目。[onClick] が `null` なら表示のみ、あればオン・オフの切り替えボタンになる。
 * [enabled] が `false` ならグレーアウトして押せない。
 *
 * アニメーションは Expressive の motionScheme (テーマの spring) に合わせる:
 * 押している間はアイコンを縮め、タップでオンになったらアイコンを弾ませて後ろから粒を散らす ([actionBurst])。
 * ストリーム等による状態の変化では弾ませない。
 * 件数は増えたら上へ、減ったら下へ入れ替わる。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PostActionButton(
    icon: ImageVector,
    label: String,
    count: Int? = null,
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val motion = MaterialTheme.motionScheme
    val color by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledContentAlpha)
            active -> activeColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "postActionColor",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) PressedIconScale else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "postActionPress",
    )
    // タップでオンになったときだけ弾ませ、粒を散らす
    val popScale = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    var tapped by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active && tapped) {
            launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(ActionBurstDurationMillis, easing = LinearEasing))
            }
            popScale.animateTo(PopIconScale, motion.fastSpatialSpec())
            popScale.animateTo(1f, motion.defaultSpatialSpec())
        } else {
            burst.snapTo(0f)
        }
        tapped = false
    }
    val burstColors = listOf(activeColor, activeColor.copy(alpha = 0.55f))
    val density = LocalDensity.current
    val iconCenterX = with(density) { (ActionPaddingHorizontal + ActionIconSize / 2).toPx() }
    val activeState = stringResource(R.string.post_action_done)
    Row(
        modifier = Modifier
            // 粒はボタンの外まで飛ぶので clip より外側で描く
            .actionBurst(
                progress = { burst.value },
                centerOf = { size -> Offset(iconCenterX, size.height / 2) },
                colors = burstColors,
            )
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.toggleable(
                        value = active,
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = ripple(),
                        role = Role.Switch,
                        onValueChange = {
                            tapped = true
                            onClick()
                        },
                    )
                } else {
                    Modifier.semantics { if (active) stateDescription = activeState }
                },
            )
            .padding(horizontal = ActionPaddingHorizontal, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier
                .size(ActionIconSize)
                .graphicsLayer {
                    val scale = pressScale * popScale.value
                    scaleX = scale
                    scaleY = scale
                },
            tint = color,
        )
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    // 増えたら下から上へ、減ったら上から下へ
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInVertically(motion.fastSpatialSpec()) { it * direction } + fadeIn(motion.fastEffectsSpec()))
                        .togetherWith(
                            slideOutVertically(motion.fastSpatialSpec()) { -it * direction } + fadeOut(motion.fastEffectsSpec()),
                        )
                        .using(SizeTransform(clip = false))
                },
                label = "postActionCount",
            ) { value ->
                Text(text = value.toString(), style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

private val ActionIconSize = 18.dp

/** 押せない項目の色の不透明度 (Material の disabled content と同じ)。 */

/** 押している間のアイコンの縮小率。 */
private const val PressedIconScale = 0.8f

/** タップでオンになったときに弾ませる最大の拡大率。 */
private const val PopIconScale = 1.3f

internal fun relativeTime(instant: Instant, now: Instant = Instant.now()): String {
    val d = Duration.between(instant, now)
    return when {
        d.toMinutes() < 1 -> "${d.seconds.coerceAtLeast(0)}s"
        d.toHours() < 1 -> "${d.toMinutes()}m"
        d.toDays() < 1 -> "${d.toHours()}h"
        else -> "${d.toDays()}d"
    }
}
