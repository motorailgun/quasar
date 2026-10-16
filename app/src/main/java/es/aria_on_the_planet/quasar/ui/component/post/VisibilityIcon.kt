package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.PostVisibility

/**
 * 公開範囲のアイコン (Mastodon 公式 Web UI を元に、目立たせる必要のあるものだけ)。
 * 公開・フォロワー限定は何も出さず、未収載は月、ダイレクトは手紙。
 */
@Composable
fun VisibilityIcon(
    visibility: PostVisibility,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    tint: Color = LocalContentColor.current,
) {
    val (icon, label) = visibility.icon() ?: return
    Icon(icon, contentDescription = stringResource(label), modifier = modifier.size(size), tint = tint)
}

/** アイコンを出す公開範囲か。 */
val PostVisibility.hasIcon: Boolean get() = icon() != null

private fun PostVisibility.icon(): Pair<ImageVector, Int>? = when (this) {
    PostVisibility.Unlisted -> Icons.Outlined.Nightlight to R.string.post_visibility_unlisted
    PostVisibility.Direct -> Icons.Outlined.MailOutline to R.string.post_visibility_direct
    PostVisibility.Public, PostVisibility.Private -> null
}

/** 公開範囲を選ぶ場面 (投稿画面) 用の、すべての公開範囲に付くアイコン。 */
val PostVisibility.selectorIcon: ImageVector
    get() = when (this) {
        PostVisibility.Public -> Icons.Outlined.Public
        PostVisibility.Unlisted -> Icons.Outlined.Nightlight
        PostVisibility.Private -> Icons.Outlined.Lock
        PostVisibility.Direct -> Icons.Outlined.MailOutline
    }

/** 公開範囲の名前 (文字列リソース)。 */
val PostVisibility.labelRes: Int
    get() = when (this) {
        PostVisibility.Public -> R.string.post_visibility_public
        PostVisibility.Unlisted -> R.string.post_visibility_unlisted
        PostVisibility.Private -> R.string.post_visibility_private
        PostVisibility.Direct -> R.string.post_visibility_direct
    }

/** 公開範囲の説明 (文字列リソース)。 */
val PostVisibility.descriptionRes: Int
    get() = when (this) {
        PostVisibility.Public -> R.string.post_visibility_public_description
        PostVisibility.Unlisted -> R.string.post_visibility_unlisted_description
        PostVisibility.Private -> R.string.post_visibility_private_description
        PostVisibility.Direct -> R.string.post_visibility_direct_description
    }
