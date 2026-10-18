package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.ui.component.post.descriptionRes
import es.aria_on_the_planet.quasar.ui.component.post.labelRes
import es.aria_on_the_planet.quasar.ui.component.post.selectorIcon

/**
 * 公開範囲のボタン (X の「Everyone can reply」の位置)。押すと選択メニューを出す。
 *
 * @param compact `true` ならアイコンだけ (クイック投稿バーなど幅の狭い所用)。
 */
@Composable
fun VisibilityButton(
    visibility: PostVisibility,
    onChange: (PostVisibility) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(visibility.labelRes)
    Box(modifier) {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.semantics { stateDescription = label },
        ) {
            Icon(visibility.selectorIcon, contentDescription = stringResource(R.string.composer_visibility))
            if (!compact) {
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
                Icon(Symbols.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        VisibilityMenu(
            expanded = expanded,
            selected = visibility,
            onSelect = {
                expanded = false
                onChange(it)
            },
            onDismiss = { expanded = false },
        )
    }
}

@Composable
private fun VisibilityMenu(
    expanded: Boolean,
    selected: PostVisibility,
    onSelect: (PostVisibility) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        PostVisibility.entries.forEach { visibility ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(visibility.labelRes), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(visibility.descriptionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = { onSelect(visibility) },
                leadingIcon = { Icon(visibility.selectorIcon, contentDescription = null) },
                trailingIcon = {
                    // 選択の有無で幅が変わらないよう枠は常に確保する
                    Box(Modifier.size(24.dp)) {
                        if (visibility == selected) Icon(Symbols.Filled.Check, contentDescription = null)
                    }
                },
            )
        }
    }
}
