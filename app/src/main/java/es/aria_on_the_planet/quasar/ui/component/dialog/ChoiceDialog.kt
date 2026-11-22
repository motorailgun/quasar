package es.aria_on_the_planet.quasar.ui.component.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R

/**
 * 選択肢から 1 つ選ぶラジオボタンのダイアログ (AOSP の設定の「Screen timeout」の形)。選んだ時点で [onSelect]
 * (閉じるのは呼び出し側)。中身は LazyColumn なので、選択肢が多くても (言語の一覧等) 開くときに全部を組み立てない。
 *
 * @param groups 選択肢のまとまり。まとまりの間に区切り線を引く。
 * @param label 選択肢の表示名。
 * @param description 選択肢の補足。
 */
@Composable
fun <T> ChoiceDialog(
    title: String,
    groups: List<List<T>>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    description: (@Composable (T) -> String?)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.selectableGroup()) {
                groups.forEachIndexed { index, options ->
                    if (index > 0) item(contentType = "divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    items(options, contentType = { "choice" }) { option ->
                        ChoiceRow(
                            label = label(option),
                            description = description?.invoke(option),
                            selected = option == selected,
                            onClick = { onSelect(option) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ChoiceRow(label: String, description: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
