package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.ComposeLimits
import es.aria_on_the_planet.quasar.domain.model.PollDraft
import es.aria_on_the_planet.quasar.ui.theme.QuasarTheme
import kotlin.time.Duration

/**
 * 投票の編集欄: 選択肢 (追加・削除)、期間、複数選択。表示用の [es.aria_on_the_planet.quasar.ui.component.poll.PollView]
 * とは別物。選択肢は 2 つ以上・[ComposeLimits.maxPollOptions] 以下に保つ。
 */
@Composable
fun PollEditor(
    poll: PollDraft,
    onChange: (PollDraft) -> Unit,
    limits: ComposeLimits,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        poll.options.forEachIndexed { index, option ->
            PollOptionField(
                index = index,
                value = option,
                maxLength = limits.maxPollOptionCharacters,
                onValueChange = { value ->
                    onChange(poll.copy(options = poll.options.toMutableList().also { it[index] = value }))
                },
                onRemove = if (poll.options.size > MinOptions) {
                    { onChange(poll.copy(options = poll.options.filterIndexed { i, _ -> i != index })) }
                } else {
                    null
                },
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (poll.options.size < limits.maxPollOptions) {
                TextButton(onClick = { onChange(poll.copy(options = poll.options + "")) }) {
                    Icon(Symbols.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.composer_poll_add_option), modifier = Modifier.padding(start = 6.dp))
                }
            }
            PollDurationChip(
                selected = poll.expiresIn,
                options = limits.pollDurations,
                onSelect = { onChange(poll.copy(expiresIn = it)) },
            )
            FilterChip(
                selected = poll.multiple,
                onClick = { onChange(poll.copy(multiple = !poll.multiple)) },
                label = { Text(stringResource(R.string.composer_poll_multiple)) },
                leadingIcon = if (poll.multiple) {
                    { Icon(Symbols.Filled.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun PollOptionField(
    index: Int,
    value: String,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    onRemove: (() -> Unit)?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.composer_poll_option, index + 1)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        isError = value.length > maxLength,
        // 上限に近づいてから残りを出す (常に出すとうるさい)
        suffix = if (value.length > maxLength - 10) {
            { Text((maxLength - value.length).toString()) }
        } else {
            null
        },
        trailingIcon = onRemove?.let {
            {
                IconButton(onClick = it) {
                    Icon(Symbols.Filled.Close, contentDescription = stringResource(R.string.composer_poll_remove_option))
                }
            }
        },
    )
}

@Composable
private fun PollDurationChip(
    selected: Duration,
    options: List<Duration>,
    onSelect: (Duration) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = { Text(durationLabel(selected)) },
            leadingIcon = {
                Icon(
                    Symbols.Outlined.Schedule,
                    contentDescription = stringResource(R.string.composer_poll_duration),
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            trailingIcon = {
                Icon(Symbols.Filled.ArrowDropDown, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize))
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { duration ->
                DropdownMenuItem(
                    text = { Text(durationLabel(duration)) },
                    onClick = {
                        expanded = false
                        onSelect(duration)
                    },
                    trailingIcon = if (duration == selected) {
                        { Icon(Symbols.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** 「30 minutes」「6 hours」「3 days」。割り切れる最大の単位で表す。 */
@Composable
private fun durationLabel(duration: Duration): String {
    val minutes = duration.inWholeMinutes.toInt()
    return when {
        minutes % MinutesPerDay == 0 ->
            pluralStringResource(R.plurals.composer_poll_days, minutes / MinutesPerDay, minutes / MinutesPerDay)
        minutes % 60 == 0 -> pluralStringResource(R.plurals.composer_poll_hours, minutes / 60, minutes / 60)
        else -> pluralStringResource(R.plurals.composer_poll_minutes, minutes, minutes)
    }
}

private const val MinOptions = 2
private const val MinutesPerDay = 24 * 60

@Preview(showBackground = true)
@Composable
private fun PollEditorPreview() {
    QuasarTheme {
        PollEditor(
            poll = PollDraft(options = listOf("Cats", "")),
            onChange = {},
            limits = ComposeLimits(),
            modifier = Modifier.padding(16.dp),
        )
    }
}
