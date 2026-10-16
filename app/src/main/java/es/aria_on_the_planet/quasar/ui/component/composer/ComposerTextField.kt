package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.ui.theme.QuasarTheme

/**
 * 枠の無い本文欄 (X の投稿欄と同じ見た目)。プレースホルダは空のときだけ出す。
 *
 * @param minHeight 空でもこの高さを確保し、余白のタップでも入力を始められるようにする。
 * @param outputTransformation 表示だけの装飾 (本文は変えない)。絵文字の強調 ([rememberEmojiShortcodeHighlight]) 等。
 */
@Composable
fun ComposerTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.composer_placeholder),
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    minHeight: Dp = 0.dp,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.MultiLine(),
    enabled: Boolean = true,
    outputTransformation: OutputTransformation? = null,
) {
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        state = state,
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight),
        enabled = enabled,
        textStyle = textStyle.copy(color = color),
        lineLimits = lineLimits,
        outputTransformation = outputTransformation,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorator = { innerTextField ->
            Box {
                if (state.text.isEmpty()) {
                    Text(placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                innerTextField()
            }
        },
    )
}

/** 本文の上に出す CW (警告文) の入力欄。下に区切り線を付けて本文と分ける。 */
@Composable
fun ContentWarningField(
    state: TextFieldState,
    visible: Boolean,
    modifier: Modifier = Modifier,
    outputTransformation: OutputTransformation? = null,
) {
    AnimatedVisibility(visible, modifier = modifier) {
        Column(Modifier.padding(bottom = 8.dp)) {
            ComposerTextField(
                state = state,
                placeholder = stringResource(R.string.composer_cw_placeholder),
                textStyle = MaterialTheme.typography.bodyLarge,
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                outputTransformation = outputTransformation,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ComposerTextFieldPreview() {
    QuasarTheme {
        Column(Modifier.padding(16.dp)) {
            ContentWarningField(rememberTextFieldState("Spoilers"), visible = true)
            ComposerTextField(rememberTextFieldState(), minHeight = 96.dp)
        }
    }
}
