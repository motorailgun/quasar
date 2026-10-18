package es.aria_on_the_planet.quasar.ui.post

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R

/**
 * 自分のポストのメニュー (︙)。編集と削除 (error 色)。確認は呼び出し側 ([onDelete] の先) が行う。
 *
 * @param busy 編集の準備・削除の最中。ボタンの代わりに読み込み中を出し、押せない。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OwnPostMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }, enabled = !busy) {
            if (busy) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                Icon(
                    Symbols.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.post_menu),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.post_edit)) },
                leadingIcon = { Icon(Symbols.Outlined.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            val error = MaterialTheme.colorScheme.error
            DropdownMenuItem(
                text = { Text(stringResource(R.string.post_delete)) },
                leadingIcon = { Icon(Symbols.Outlined.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
                colors = MenuDefaults.itemColors(textColor = error, leadingIconColor = error),
            )
        }
    }
}
