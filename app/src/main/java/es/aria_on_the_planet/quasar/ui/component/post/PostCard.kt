package es.aria_on_the_planet.quasar.ui.component.post

import es.aria_on_the_planet.quasar.ui.theme.DisabledContentAlpha
import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.ui.component.account.LocalAccountActions
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings
import es.aria_on_the_planet.quasar.ui.theme.extendedColors
import es.aria_on_the_planet.quasar.ui.component.user.UserIcon
import es.aria_on_the_planet.quasar.ui.component.user.UserName
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji

object PostCardDefaults {
    val MediaHeight: Dp = 160.dp
    val AvatarSize: Dp = 40.dp
    val ContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
}

/** [PostCardMode.Preview] の本文の行数の上限。 */
private const val PreviewMaxLines = 6

/** アイコンと本文の列の間。 */
private val AvatarGap = 12.dp

/** [PostCard] の表示の種類。 */
enum class PostCardMode {
    /** 一覧・スレッド用の全部入り。 */
    Full,

    /**
     * 返信画面の上に出す返信先 (X の返信画面の形)。本文 (CW・長文の折りたたみ込み) とメディアだけで、
     * ラベル (ブースト・リプライ・ピン留め)・公開範囲・投票・リンクプレビュー・引用・アクション行は出さない。
     * カード全体のタップも受けない。
     */
    ReplyContext,

    /**
     * ポストのメニューの上に出す対象のポスト ([PostMenuPreview])。出すものは [ReplyContext] と同じだが、開閉できない:
     * CW は開いたまま (警告のアイコン + 警告文の下に本文)、長文は設定に関わらず [PreviewMaxLines] 行で切ってフェードさせるだけ
     * (「Show more」は出さない)。
     */
    Preview,
}

/**
 * ポスト 1 件。タイムライン・プロフィール等で再利用する stateless コンポーネント。
 *
 * @param callbacks ポストへの操作の口 ([PostCallbacks])。
 * @param mediaHeight 添付メディアの表示高さ。各メディアはこの高さに揃えられる。
 * @param pinned プロフィールのピン留めとして表示する場合 `true`。小さな「Pinned」ラベルを付ける。
 * @param showReplyIndicator リプライのとき「Reply to @user」を出すか。スレッド表示など、
 *   返信関係が別の形で分かる場所では `false` にする。
 * @param mode 表示の種類 ([PostCardMode])。
 * @param avatarSize 投稿者のアイコンの大きさ。ラベル (ブースト等) の左端もこれに合わせてずらす。
 * @param contentPadding カードの内側の余白。
 * @param authorTrailing 投稿者行の右端に置くもの ([PostCardMode.ReplyContext] の ✕ 等)。公開範囲のアイコンの代わりに出す。
 */
@Composable
fun PostCard(
    post: Post,
    modifier: Modifier = Modifier,
    callbacks: PostCallbacks = PostCallbacks.None,
    mediaHeight: Dp = PostCardDefaults.MediaHeight,
    pinned: Boolean = false,
    showReplyIndicator: Boolean = true,
    mode: PostCardMode = PostCardMode.Full,
    avatarSize: Dp = PostCardDefaults.AvatarSize,
    contentPadding: PaddingValues = PostCardDefaults.ContentPadding,
    authorTrailing: (@Composable () -> Unit)? = null,
) {
    val full = mode == PostCardMode.Full
    val onUserClick = callbacks.onUserClick
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (full) Modifier.clickable { callbacks.onPostClick(post) } else Modifier)
            .padding(contentPadding),
    ) {
        if (full) PostCardLabels(post, pinned, showReplyIndicator, onUserClick, labelStart = avatarSize + AvatarGap)
        Row {
            UserIcon(
                user = post.author,
                size = avatarSize,
                modifier = Modifier.clickable { onUserClick(post.author) },
            )
            Spacer(Modifier.width(AvatarGap))
            Column(Modifier.weight(1f)) {
                AuthorLine(
                    post,
                    onAuthorClick = { onUserClick(post.author) },
                    showVisibility = full,
                    showMenu = full,
                    trailing = authorTrailing,
                )
                Spacer(Modifier.size(4.dp))
                PostBody(
                    post,
                    callbacks = callbacks,
                    mediaHeight = mediaHeight,
                    spacing = 8.dp,
                    // 長い本文は設定の行数で折りたたむ
                    collapsedMaxLines = if (mode == PostCardMode.Preview) PreviewMaxLines else LocalDisplaySettings.current.longPostMaxLines,
                    extras = full,
                    fixed = mode == PostCardMode.Preview,
                )
                if (full) {
                    Spacer(Modifier.size(8.dp))
                    PostActionRow(post, onAction = callbacks.onAction, onReply = callbacks.onReply)
                }
            }
        }
    }
}

/** 本文の上のラベル (ピン留め・ブースト・リプライ)。 */
@Composable
private fun PostCardLabels(post: Post, pinned: Boolean, showReplyIndicator: Boolean, onUserClick: (User) -> Unit, labelStart: Dp) {
    if (pinned) {
        PinnedLabel(Modifier.padding(start = labelStart, bottom = 2.dp))
    }
    post.boostedBy?.let { booster ->
        BoostedByLabel(
            booster,
            Modifier
                .padding(start = labelStart, bottom = 4.dp)
                .clickable { onUserClick(booster) },
        )
    }
    if (showReplyIndicator && post.inReplyToId != null) {
        val acct = post.inReplyToAcct
        PostLabel(
            Symbols.Outlined.Reply,
            if (acct != null) stringResource(R.string.post_reply_to, "@$acct") else stringResource(R.string.post_reply),
            Modifier.padding(start = labelStart, bottom = 4.dp),
        )
    }
}

@Composable
private fun AuthorLine(
    post: Post,
    onAuthorClick: () -> Unit,
    showVisibility: Boolean = true,
    showMenu: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 表示名 + screenName + 時刻は左に詰め、公開範囲のアイコンとメニューは右端に寄せる
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            AuthorNameAndTime(post, onAuthorClick)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (showVisibility && post.visibility.hasIcon) {
            Spacer(Modifier.width(8.dp))
            // アクション行のアイコンと同じ幅の枠の中央に置く
            Box(Modifier.size(ActionIconSize), contentAlignment = Alignment.Center) {
                VisibilityIcon(post.visibility, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // ポストのメニュー (︙)。アカウントへの操作の口 (LocalAccountActions) が無い所では出さない
        if (trailing == null && showMenu && LocalAccountActions.current != null) {
            // ︙ は細く左右に余白のあるアイコンなので、間を空けずに置いても公開範囲のアイコンとは十分離れて見える
            PostMenu(post, compact = true)
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
        UserName(
            post.author,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
        text = postTimeText(post.createdAt),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun BoostedByLabel(booster: User, modifier: Modifier = Modifier) {
    PostLabel(Symbols.Outlined.Repeat, stringResource(R.string.post_boosted_by, booster.nameOrAcct), modifier, booster.emojis)
}

@Composable
private fun PostLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier, emojis: List<CustomEmoji> = emptyList()) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(4.dp))
        val style = MaterialTheme.typography.labelMedium
        val color = MaterialTheme.colorScheme.tertiary
        // 絵文字の無いものは EmojiText (変換・絵文字の差し込み) を通さない
        if (emojis.isEmpty()) {
            Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            EmojiText(text, emojis, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PinnedLabel(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Symbols.Outlined.PushPin,
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
 * [onAction] があればブースト・お気に入り・ブックマークを、[onReply] があればリプライを押せる。
 *
 * @param reactionDetail ポスト詳細用の表示。ブースト・お気に入りの件数をアイコンの横に「N boosts」「M favourites」と
 *   出し (X の一時期の詳細画面の形)、アイコンのタップは通常の操作、件数のタップは [onReactionCountClick]
 *   (リアクションしたユーザーの一覧へ。引数は [PostActionType.Boost] か [PostActionType.Favourite])。
 */
@Composable
fun PostActionRow(
    post: Post,
    modifier: Modifier = Modifier,
    onAction: OnPostAction? = null,
    onReply: ((Post) -> Unit)? = null,
    reactionDetail: Boolean = false,
    onReactionCountClick: ((PostActionType) -> Unit)? = null,
) {
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
    ) {
        if (reactionDetail) {
            // リプライ / ブースト + 件数 / お気に入り + 件数 を左から詰めて並べ、ブックマークは右端
            ReplyAction(post, onReply)
            Spacer(Modifier.width(ReactionDetailGap))
            BoostAction(post, onAction, showCount = false)
            ReactionCountLabel(
                count = post.boostsCount,
                text = { pluralStringResource(R.plurals.post_detail_boosts, it, it) },
                onClick = onReactionCountClick?.let { { it(PostActionType.Boost) } },
            )
            Spacer(Modifier.width(ReactionDetailGap))
            FavouriteAction(post, onAction, showCount = false)
            ReactionCountLabel(
                count = post.reactionsCount,
                text = { pluralStringResource(R.plurals.post_detail_favourites, it, it) },
                onClick = onReactionCountClick?.let { { it(PostActionType.Favourite) } },
            )
            Spacer(Modifier.weight(1f))
            BookmarkAction(post, onAction)
            return@Row
        }
        // 5 個分の等幅の枠があるとして、リプライ・ブースト・お気に入りを左の 3 つ (幅の 3/5) の左端に置き、
        // ブックマークは右端。件数の桁が変わってもアイコンの位置は動かない
        Row(Modifier.fillMaxWidth(ActionGroupFraction), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ReplyAction(post, onReply) }
            Box(Modifier.weight(1f)) { BoostAction(post, onAction, showCount = true) }
            Box(Modifier.weight(1f)) { FavouriteAction(post, onAction, showCount = true) }
        }
        Spacer(Modifier.weight(1f))
        BookmarkAction(post, onAction)
    }
}

// アクション行の各ボタン (通常の表示と詳細の表示で共通)

@Composable
private fun ReplyAction(post: Post, onReply: ((Post) -> Unit)?) {
    PostActionButton(
        icon = Symbols.Outlined.Reply,
        label = stringResource(R.string.post_replies),
        count = post.repliesCount,
        toggle = false,
        onClick = onReply?.let { { it(post) } },
    )
}

@Composable
private fun BoostAction(post: Post, onAction: OnPostAction?, showCount: Boolean) {
    val reblogged = post.viewer.reblogged
    PostActionButton(
        icon = if (reblogged) Symbols.Filled.Repeat else Symbols.Outlined.Repeat,
        label = stringResource(R.string.post_boosts),
        count = if (showCount) post.boostsCount else null,
        active = reblogged,
        activeColor = MaterialTheme.extendedColors.reblog.color,
        // フォロワー限定・ダイレクトはブーストできない (既にブースト済みなら取り消しはできるようにする)
        enabled = post.visibility.boostable || reblogged,
        onClick = onAction?.let { { it(post, PostActionType.Boost) } },
    )
}

@Composable
private fun FavouriteAction(post: Post, onAction: OnPostAction?, showCount: Boolean) {
    val favourited = post.viewer.favourited
    PostActionButton(
        icon = if (favourited) Symbols.Filled.Star else Symbols.Outlined.Star,
        label = stringResource(R.string.post_reactions),
        count = if (showCount) post.reactionsCount else null,
        active = favourited,
        activeColor = MaterialTheme.extendedColors.favourite.color,
        onClick = onAction?.let { { it(post, PostActionType.Favourite) } },
    )
}

@Composable
private fun BookmarkAction(post: Post, onAction: OnPostAction?) {
    val bookmarked = post.viewer.bookmarked
    PostActionButton(
        icon = if (bookmarked) Symbols.Filled.Bookmark else Symbols.Outlined.Bookmark,
        label = stringResource(R.string.post_bookmark),
        active = bookmarked,
        activeColor = MaterialTheme.colorScheme.secondary,
        onClick = onAction?.let { { it(post, PostActionType.Bookmark) } },
    )
}

/**
 * 詳細表示 ([PostActionRow] の `reactionDetail`) のリアクションの件数「N boosts」(Medium・onSurfaceVariant)。[onClick] があれば押せる
 * (リアクションしたユーザーの一覧へ)。件数は [PostActionButton] と同じく増えたら上へ、減ったら下へ入れ替わる。
 */
@Composable
private fun ReactionCountLabel(
    count: Int,
    text: @Composable (Int) -> String,
    onClick: (() -> Unit)?,
) {
    Box(
        Modifier
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = ActionPaddingHorizontal, vertical = 4.dp),
    ) {
        AnimatedCount(count) { value ->
            Text(
                text = text(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 件数の入れ替わり: 増えたら下から上へ、減ったら上から下へ。
 * 一覧では項目が画面に入るたびに組み立てるので、件数が変わるまでは [AnimatedContent] を作らずそのまま出す
 * (1 つ約 0.36ms で、アクション行の 3 つでポスト 1 件の組み立ての 3 割ほどを占めていた)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedCount(count: Int, content: @Composable (Int) -> Unit) {
    val holder = remember { CountTransition(count) }
    val transitionState = holder.stateFor(count)
    if (transitionState == null) {
        content(count)
        return
    }
    val motion = MaterialTheme.motionScheme
    rememberTransition(transitionState, label = "postActionCount").AnimatedContent(
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (slideInVertically(motion.fastSpatialSpec()) { it * direction } + fadeIn(motion.fastEffectsSpec()))
                .togetherWith(
                    slideOutVertically(motion.fastSpatialSpec()) { -it * direction } + fadeOut(motion.fastEffectsSpec()),
                )
                .using(SizeTransform(clip = false))
        },
    ) { value -> content(value) }
}

/** [AnimatedCount] の最初の件数と、件数が変わってから使う遷移 (最初の件数から始めるので、初回の変化も動く)。 */
private class CountTransition(private val initial: Int) {
    private var state: MutableTransitionState<Int>? = null

    /** 件数が一度も変わっていなければ `null`。 */
    fun stateFor(count: Int): MutableTransitionState<Int>? {
        val current = state ?: if (count == initial) return null else MutableTransitionState(initial).also { state = it }
        current.targetState = count
        return current
    }
}

/** 詳細表示のアクション行の項目 (リプライ / ブースト / お気に入り) の間隔。 */
private val ReactionDetailGap = 12.dp

/** アクション行のうちリプライ・ブースト・お気に入りが占める幅の割合 (5 等分のうち 3 つ)。 */
private const val ActionGroupFraction = 3f / 5f

/** アクション行のボタンの左右の余白 (押せる範囲)。 */
internal val ActionPaddingHorizontal = 6.dp

/**
 * アクション行の 1 項目。[onClick] が `null` なら表示のみ、あればオン・オフの切り替えボタン ([toggle] が `false` なら
 * 普通のボタン) になる。[enabled] が `false` ならグレーアウトして押せない。
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
    toggle: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val motion = MaterialTheme.motionScheme
    val targetColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledContentAlpha)
        active -> activeColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val scope = rememberCoroutineScope()
    val animation = remember { ActionButtonAnimation(targetColor, active) }
    // 状態・色の変化はアニメーションを始めるだけなので、ボタンごとにコルーチンを置かず反映の後で拾う
    SideEffect { animation.update(scope, motion, targetColor, active) }
    val interactionSource = remember { MutableInteractionSource() }
    if (onClick != null) {
        LaunchedEffect(interactionSource) { animation.followPresses(interactionSource, motion) }
    }
    val color = animation.color.value
    val burstColors = remember(activeColor) { listOf(activeColor, activeColor.copy(alpha = 0.55f)) }
    val density = LocalDensity.current
    val iconCenterX = with(density) { (ActionPaddingHorizontal + ActionIconSize / 2).toPx() }
    val activeState = stringResource(R.string.post_action_done)
    Row(
        modifier = Modifier
            // 粒はボタンの外まで飛ぶので clip より外側で描く
            .actionBurst(
                progress = { animation.burst.value },
                centerOf = { size -> Offset(iconCenterX, size.height / 2) },
                colors = burstColors,
            )
            .clip(CircleShape)
            .then(
                if (onClick != null && !toggle) {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else if (onClick != null) {
                    Modifier.toggleable(
                        value = active,
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = ripple(),
                        role = Role.Switch,
                        onValueChange = {
                            animation.tapped = true
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
                    val scale = animation.pressScale.value * animation.popScale.value
                    scaleX = scale
                    scaleY = scale
                },
            tint = color,
        )
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            AnimatedCount(count) { value ->
                Text(text = value.toString(), style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

internal val ActionIconSize = 18.dp

/**
 * [PostActionButton] のアニメーション (色・押下の縮小・タップでオンにしたときの弾みと粒)。
 * 一覧では項目が画面に入るたびにボタンが組み立てられるので、`animate*AsState` や状態ごとの `LaunchedEffect`
 * (どれもボタンごとにコルーチンを持つ) を使わず、変化があったときだけアニメーションを起動する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class ActionButtonAnimation(initialColor: Color, private var active: Boolean) {
    val color = Animatable(initialColor)
    val pressScale = Animatable(1f)
    val popScale = Animatable(1f)
    val burst = Animatable(0f)

    /** タップでオンにしようとしたか。実際にオンになったとき (確認ダイアログを挟むとその後) に弾ませる。 */
    var tapped = false
    private var targetColor = initialColor

    /** 反映された状態を受け取る。ストリーム等による変化では弾ませない。 */
    fun update(scope: CoroutineScope, motion: MotionScheme, targetColor: Color, active: Boolean) {
        if (targetColor != this.targetColor) {
            this.targetColor = targetColor
            scope.launch { color.animateTo(targetColor, motion.defaultEffectsSpec()) }
        }
        if (active == this.active) return
        this.active = active
        if (active && tapped) {
            scope.launch {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(ActionBurstDurationMillis, easing = LinearEasing))
            }
            scope.launch {
                popScale.animateTo(PopIconScale, motion.fastSpatialSpec())
                popScale.animateTo(1f, motion.defaultSpatialSpec())
            }
        } else if (burst.value != 0f) {
            scope.launch { burst.snapTo(0f) }
        }
        tapped = false
    }

    /** 押している間はアイコンを縮める。 */
    suspend fun followPresses(interactionSource: InteractionSource, motion: MotionScheme) = coroutineScope {
        val presses = mutableListOf<PressInteraction.Press>()
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> presses += interaction
                is PressInteraction.Release -> presses -= interaction.press
                is PressInteraction.Cancel -> presses -= interaction.press
                else -> return@collect
            }
            val target = if (presses.isEmpty()) 1f else PressedIconScale
            launch { pressScale.animateTo(target, motion.fastSpatialSpec()) }
        }
    }
}

/** 押している間のアイコンの縮小率。 */
private const val PressedIconScale = 0.8f

/** タップでオンになったときに弾ませる最大の拡大率。 */
private const val PopIconScale = 1.3f
