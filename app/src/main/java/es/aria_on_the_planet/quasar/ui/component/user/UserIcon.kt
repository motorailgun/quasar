package es.aria_on_the_planet.quasar.ui.component.user

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.image.ImageKind
import es.aria_on_the_planet.quasar.ui.component.image.RemoteImage

/** ユーザーのアイコン。[shape] は既定で設定のアイコンの形 ([avatarShape])。 */
@Composable
fun UserIcon(user: User, modifier: Modifier = Modifier, size: Dp = 40.dp, shape: Shape = avatarShape()) {
    RemoteImage(
        url = user.iconUrl,
        staticUrl = user.iconStaticUrl,
        kind = ImageKind.Avatar,
        contentDescription = user.screenName,
        modifier = modifier
            .size(size)
            .clip(shape),
    )
}
