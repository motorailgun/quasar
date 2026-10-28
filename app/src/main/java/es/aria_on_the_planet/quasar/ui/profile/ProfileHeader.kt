package es.aria_on_the_planet.quasar.ui.profile

import android.icu.text.CompactDecimalFormat
import android.icu.text.NumberFormat
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import es.aria_on_the_planet.quasar.ui.component.user.UserName
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import es.aria_on_the_planet.quasar.ui.component.player.MediaOverlayColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.toSize
import es.aria_on_the_planet.quasar.ui.component.player.LocalInlinePlayback
import es.aria_on_the_planet.quasar.ui.mediaviewer.MediaViewerArgs
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.ProfileField
import es.aria_on_the_planet.quasar.domain.model.Relationship
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.image.ImageKind
import es.aria_on_the_planet.quasar.ui.component.image.RemoteImage
import es.aria_on_the_planet.quasar.ui.component.post.UserIcon
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import es.aria_on_the_planet.quasar.ui.component.user.avatarShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HeaderHeight = 140.dp
private val AvatarSize = 88.dp
private val AvatarRing = 4.dp

/**
 * プロフィール上部 (ヘッダ画像・アイコン・名前・bio・フィールド・統計)。
 *
 * @param relationship 相手との関係。@acct の横に「Follows you」等を出す。取得前・失敗時は `null`。
 * @param actions アイコンの右 (ヘッダ画像の下) に置くボタン (フォロー / プロフィール編集)。
 * @param onFollowingClick / [onFollowersClick] 統計のフォロー数・フォロワー数のタップ (一覧を開く)。
 * @param onOpenImage アイコン・ヘッダ画像のタップ (メディアビューアを開く)。`null` ならタップできない。
 * @param onUserClick 引っ越し先のアカウントのタップ (プロフィールを開く)。
 * @param followRequest 統計の下に置く、相手からのフォローリクエストへの返事 ([FollowRequestCard])。
 * @param topBarHeight 上に重ねたトップバーの高さ (ステータスバー込み)。0 より大きければヘッダ画像をステータスバーの分だけ
 *   高くして上端に薄いグラデーションを敷く (引っ越し先の表示があれば、それをバーの下から始める)。
 * @param onImageBottomChanged ヘッダ画像の下端の位置 (このヘッダの上端から、px)。トップバーを不透明にする判定に使う。
 */
@Composable
fun ProfileHeader(
    profile: Profile,
    modifier: Modifier = Modifier,
    followersYouFollow: List<User> = emptyList(),
    relationship: Relationship? = null,
    onFollowingClick: () -> Unit = {},
    onFollowersClick: () -> Unit = {},
    onOpenImage: ((MediaViewerArgs) -> Unit)? = null,
    onUserClick: (User) -> Unit = {},
    actions: @Composable () -> Unit = {},
    followRequest: @Composable () -> Unit = {},
    topBarHeight: Dp = 0.dp,
    onImageBottomChanged: (Int) -> Unit = {},
) {
    val user = profile.user
    val overlaid = topBarHeight > 0.dp
    // 引っ越し先の表示があればそれがバーの下に来るので、画像は伸ばさない
    val imageHeight = when {
        overlaid && profile.movedTo == null -> HeaderHeight + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        else -> HeaderHeight
    }
    val imageHeightPx = with(LocalDensity.current) { imageHeight.roundToPx() }
    var movedToHeight by remember(profile.movedTo == null) { mutableIntStateOf(0) }
    val imageBottom = movedToHeight + imageHeightPx
    val currentOnImageBottomChanged by rememberUpdatedState(onImageBottomChanged)
    LaunchedEffect(imageBottom) { currentOnImageBottomChanged(imageBottom) }
    val windowSize = LocalWindowInfo.current.containerSize
    val inlinePlayback = LocalInlinePlayback.current
    // 閉じるときに縮んで戻る先。タップの時点の位置を渡す
    var headerBounds by remember { mutableStateOf<Rect?>(null) }
    var avatarBounds by remember { mutableStateOf<Rect?>(null) }
    val openImage: ((id: String, url: String, staticUrl: String?, bounds: Rect?) -> Unit)? = onOpenImage?.let { open ->
        { id, url, staticUrl, bounds ->
            // ビューアはこの画面に重ねて出す (画面はコンポジションに残る) ので、ピン留めポストのインライン再生はここで止める
            inlinePlayback?.stopActive()
            open(MediaViewerArgs.singleImage(id, url, staticUrl, bounds, windowSize))
        }
    }
    Column(modifier.fillMaxWidth()) {
        profile.movedTo?.let {
            Column(
                Modifier
                    .onSizeChanged { size -> movedToHeight = size.height }
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                if (overlaid) Spacer(Modifier.height(topBarHeight))
                MovedTo(it, onClick = { onUserClick(it) })
            }
        }
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                val headerUrl = profile.headerUrl
                if (headerUrl != null) {
                    RemoteImage(
                        url = headerUrl,
                        staticUrl = profile.headerStaticUrl,
                        kind = ImageKind.Other,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { headerBounds = Rect(it.positionInWindow(), it.size.toSize()) }
                            .then(
                                if (openImage != null) {
                                    Modifier.clickable(onClickLabel = stringResource(R.string.profile_view_header)) {
                                        openImage("header:${user.inServerId}", headerUrl, profile.headerStaticUrl, headerBounds)
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    if (overlaid && profile.movedTo == null) {
                        // トップバー (戻るボタン)・ステータスバーを画像の上でも読めるよう、上端だけ薄く暗くする
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(topBarHeight)
                                .background(Brush.verticalGradient(listOf(MediaOverlayColors.TopBarScrim, Color.Transparent))),
                        )
                    }
                }
            }
            // ヘッダに半分重なるアイコン。背景色のリングで縁取る
            Box(
                Modifier
                    .padding(start = 16.dp, top = imageHeight - AvatarSize / 2 - AvatarRing)
                    .background(MaterialTheme.colorScheme.surface, avatarShape(ring = AvatarRing))
                    .padding(AvatarRing),
            ) {
                UserIcon(
                    user = user,
                    size = AvatarSize,
                    modifier = Modifier
                        .onGloballyPositioned { avatarBounds = Rect(it.positionInWindow(), it.size.toSize()) }
                        .then(
                            if (openImage != null && user.iconUrl.isNotBlank()) {
                                Modifier.clickable(onClickLabel = stringResource(R.string.profile_view_avatar)) {
                                    openImage("avatar:${user.inServerId}", user.iconUrl, user.iconStaticUrl, avatarBounds)
                                }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            // X と同じく、アイコンの右・ヘッダ画像の下 (下端をアイコンに揃える)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(start = AvatarSize + 32.dp, end = 16.dp),
            ) {
                actions()
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                UserName(
                    user,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = Int.MAX_VALUE,
                    showLocked = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "@${user.fullScreenName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    relationship?.let { RelationshipLabel(it) }
                }
            }

            if (user.note.isNotBlank()) {
                EmojiText(
                    text = user.note,
                    emojis = user.emojis,
                    isHtml = true,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (profile.fields.isNotEmpty()) {
                ProfileFields(profile.fields, user.emojis)
            }

            profile.createdAt?.let { JoinedDate(it) }

            ProfileStats(profile, onFollowingClick, onFollowersClick)

            FollowersYouFollow(followersYouFollow)

            followRequest()
        }
    }
}

/** 引っ越し先のアカウント。行全体のタップでそのプロフィールを開く。 */
@Composable
private fun MovedTo(target: User, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_moved_to),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 見出しから引っ越し先へ下りる矢印 (↳)。その分アカウントを右へ寄せる。RTL ではアイコン側が反転する (autoMirror)
            Icon(
                Symbols.Outlined.SubdirectoryArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            UserIcon(target, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                UserName(target, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    text = "@${target.fullScreenName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 相手 → 自分の関係 (X の「Follows you」)。フォローリクエストが来ているときは [FollowRequestCard] で出す。 */
@Composable
private fun RelationshipLabel(relationship: Relationship) {
    if (!relationship.followedBy) return
    val text = R.string.profile_follows_you
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ProfileFields(fields: List<ProfileField>, emojis: List<CustomEmoji>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fields.forEach { field ->
            val verified = field.verifiedAt != null
            Row(verticalAlignment = Alignment.Top) {
                EmojiText(
                    text = field.name,
                    emojis = emojis,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.35f),
                )
                Spacer(Modifier.width(12.dp))
                EmojiText(
                    text = field.value,
                    emojis = emojis,
                    isHtml = true,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (verified) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.65f),
                )
                // 認証アイコンの有無で列位置がずれないよう、枠は常に確保する
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(18.dp),
                ) {
                    if (verified) {
                        Icon(
                            Symbols.Outlined.Verified,
                            contentDescription = stringResource(R.string.profile_verified),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinedDate(createdAt: Instant) {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMM")
    val date = DateTimeFormatter.ofPattern(pattern, locale).withZone(ZoneId.systemDefault()).format(createdAt)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Symbols.Outlined.CalendarMonth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.profile_joined, date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileStats(profile: Profile, onFollowingClick: () -> Unit, onFollowersClick: () -> Unit) {
    // タップできる統計は押した範囲が分かるよう少し余白を取るので、その分だけ間隔を詰める
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Stat(profile.postsCount, stringResource(R.string.profile_posts))
        Stat(profile.followingCount, stringResource(R.string.profile_following), onFollowingClick)
        Stat(profile.followersCount, stringResource(R.string.profile_followers), onFollowersClick)
    }
}

@Composable
private fun Stat(count: Int, label: String, onClick: (() -> Unit)? = null) {
    val locale = LocalConfiguration.current.locales[0]
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        } else {
            Modifier.padding(end = 4.dp)
        },
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(formatCount(count, locale)) }
            append(' ')
            withStyle(SpanStyle(color = onSurfaceVariant)) { append(label) }
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** 1 万未満は桁区切り、それ以上は短縮表記 (例: 19.6K, 1.9万)。 */
internal fun formatCount(count: Int, locale: Locale): String =
    if (count < 10_000) {
        NumberFormat.getIntegerInstance(locale).format(count)
    } else {
        CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(count)
    }
