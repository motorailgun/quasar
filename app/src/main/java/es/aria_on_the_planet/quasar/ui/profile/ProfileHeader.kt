package es.aria_on_the_planet.quasar.ui.profile

import android.icu.text.CompactDecimalFormat
import android.icu.text.NumberFormat
import android.text.format.DateFormat
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.ProfileField
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.post.UserIcon
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HeaderHeight = 140.dp
private val AvatarSize = 88.dp
private val AvatarRing = 4.dp

/** プロフィール上部 (ヘッダ画像・アイコン・名前・bio・フィールド・統計)。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileHeader(
    profile: Profile,
    modifier: Modifier = Modifier,
    followersYouFollow: List<User> = emptyList(),
) {
    val user = profile.user
    Column(modifier.fillMaxWidth()) {
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(HeaderHeight)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (profile.headerUrl != null) {
                    AsyncImage(
                        model = profile.headerUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // ヘッダに半分重なるアイコン。背景色のリングで縁取る
            Box(
                Modifier
                    .padding(start = 16.dp, top = HeaderHeight - AvatarSize / 2 - AvatarRing)
                    .background(MaterialTheme.colorScheme.surface, MaterialShapes.Cookie9Sided.toShape())
                    .padding(AvatarRing),
            ) {
                UserIcon(user = user, size = AvatarSize)
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiText(
                        text = user.displayName.ifBlank { user.screenName },
                        emojis = user.emojis,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (profile.locked) {
                        BadgeIcon(Icons.Outlined.Lock, stringResource(R.string.profile_locked))
                    }
                    if (profile.bot) {
                        BadgeIcon(Icons.Outlined.SmartToy, stringResource(R.string.profile_bot))
                    }
                }
                Text(
                    text = "@${user.fullScreenName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

            ProfileStats(profile)

            FollowersYouFollow(followersYouFollow)
        }
        HorizontalDivider()
    }
}

@Composable
private fun BadgeIcon(icon: ImageVector, description: String) {
    Icon(
        icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 6.dp)
            .size(20.dp),
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
                            Icons.Outlined.Verified,
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
            Icons.Outlined.CalendarMonth,
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
private fun ProfileStats(profile: Profile) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Stat(profile.postsCount, stringResource(R.string.profile_posts))
        Stat(profile.followingCount, stringResource(R.string.profile_following))
        Stat(profile.followersCount, stringResource(R.string.profile_followers))
    }
}

@Composable
private fun Stat(count: Int, label: String) {
    val locale = LocalConfiguration.current.locales[0]
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
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
