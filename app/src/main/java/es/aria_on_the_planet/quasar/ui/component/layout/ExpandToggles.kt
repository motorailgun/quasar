package es.aria_on_the_planet.quasar.ui.component.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R

/** primary 色の「Show more >」/「Show less ^」。CW と長文の折りたたみで使う。 */
@Composable
fun ShowMoreToggle(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    // `>` を展開時は上向きに回す
    val rotation by animateFloatAsState(if (expanded) -90f else 0f, label = "showMoreChevron")
    Row(
        modifier = modifier
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(if (expanded) R.string.show_less else R.string.show_more),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            Symbols.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation),
        )
    }
}

/** 中央に置く `>` の丸ボタン (折りたたみ時は下向き、展開時は上向き)。ピン留め等で使う。 */
@Composable
fun ChevronToggle(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (expanded) -90f else 90f, label = "chevronRotation")
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FilledTonalIconButton(onClick = onToggle) {
            Icon(
                Symbols.Filled.KeyboardArrowRight,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}
