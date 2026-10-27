package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.post.UserIcon
import es.aria_on_the_planet.quasar.ui.component.user.avatarShape

/** 投稿するアカウントのアイコン。[user] の取得前は同じ形のプレースホルダ。[shape] は既定で設定のアイコンの形。 */
@Composable
fun ComposerAccountIcon(user: User?, modifier: Modifier = Modifier, size: Dp = 40.dp, shape: Shape = avatarShape()) {
    if (user != null) {
        UserIcon(user, modifier, size, shape)
    } else {
        Box(
            modifier
                .size(size)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape),
        )
    }
}
