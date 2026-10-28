package es.aria_on_the_planet.quasar.ui.component.user

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText

/**
 * ユーザーの表示名 (カスタム絵文字込み) と、その右に並ぶ印 (承認制・bot)。名前を単独で出す所 (ポストの投稿者行・
 * ユーザー一覧・プロフィール等) はこれを使い、印をそれぞれで足さない。文の一部の名前 (「〇〇 boosted」等) には使わない。
 *
 * 名前が省略されても印は残す。印の大きさは文字の大きさに合わせる。承認制の印は [showLocked] のときだけ (今はプロフィールのみ)。
 */
@Composable
fun UserName(
    user: User,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = 1,
    showLocked: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        EmojiText(
            text = user.nameOrAcct,
            emojis = user.emojis,
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        val size = with(LocalDensity.current) { (if (style.fontSize.isSpecified) style.fontSize else 14.sp).toDp() }
        if (showLocked && user.locked) {
            Badge(Symbols.Outlined.Lock, stringResource(R.string.profile_locked), size)
        }
        if (user.bot) {
            Badge(Symbols.Outlined.SmartToy, stringResource(R.string.profile_bot), size)
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, description: String, size: Dp) {
    Icon(
        icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = size / 4)
            .size(size),
    )
}
