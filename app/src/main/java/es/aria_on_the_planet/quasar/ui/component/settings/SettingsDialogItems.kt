package es.aria_on_the_planet.quasar.ui.component.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R

/**
 * 選択肢から 1 つ選ぶ項目 (AOSP の「Screen timeout」の形)。説明文に今の値を出し、押すとラジオボタンのダイアログ。
 * 選んだ時点で決まり、ダイアログを閉じる。
 *
 * @param label 選択肢の表示名。
 * @param description 選択肢の補足 (ダイアログの中だけ)。
 */
@Composable
fun <T> SettingsChoiceItem(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: (@Composable (T) -> String?)? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsNavigationItem(
        title = title,
        summary = label(selected),
        onClick = { open = true },
        modifier = modifier,
        enabled = enabled,
        showChevron = false,
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(
                    Modifier
                        .selectableGroup()
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { option ->
                        ChoiceRow(
                            label = label(option),
                            description = description?.invoke(option),
                            selected = option == selected,
                            onClick = {
                                open = false
                                onSelect(option)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
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

/**
 * 連続値の項目。説明文に今の値を出し、押すとスライダーのダイアログ。OK で決まる (動かしている間は反映しない)。
 *
 * @param steps 両端を除く刻みの数 (`Slider` と同じ)。0 なら連続。
 * @param valueLabel 値の表示 (説明文とダイアログ)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: @Composable (Float) -> String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsNavigationItem(
        title = title,
        summary = valueLabel(value),
        onClick = { open = true },
        modifier = modifier,
        showChevron = false,
    )
    if (open) {
        val slider = rememberSliderState(value = value, steps = steps, trackRange = valueRange)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    Text(valueLabel(slider.value), style = MaterialTheme.typography.titleMedium)
                    Slider(state = slider, onValueChange = { slider.value = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    onValueChange(slider.value)
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
