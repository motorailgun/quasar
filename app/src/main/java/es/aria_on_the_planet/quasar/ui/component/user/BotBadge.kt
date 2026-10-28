package es.aria_on_the_planet.quasar.ui.component.user

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols

/** bot のアカウント (`User.bot`) の印。表示名の右に置く。 */
@Composable
fun BotBadge(modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Icon(
        Symbols.Outlined.SmartToy,
        contentDescription = stringResource(R.string.profile_bot),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(size),
    )
}
