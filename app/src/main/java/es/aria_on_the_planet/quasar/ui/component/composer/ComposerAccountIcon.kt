package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.post.UserIcon

/** 投稿するアカウントのアイコン。[user] の取得前は同じ形のプレースホルダ。 */
@Composable
fun ComposerAccountIcon(user: User?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    if (user != null) {
        UserIcon(user, modifier, size)
    } else {
        Box(
            modifier
                .size(size)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        )
    }
}
