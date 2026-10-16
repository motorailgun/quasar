package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.ui.theme.QuasarTheme

/**
 * 投稿欄の下端のツールバー。上に区切り線、左端に [leading] (文字数カウンタ等)、ボタン群 ([actions]) は右寄せ。
 */
@Composable
fun ComposerToolbar(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit,
) {
    Column(modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(start = 12.dp)) { leading() }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * メディアを添付するボタン。
 *
 * @param active 添付があるか (オンの色で示す)。
 */
@Composable
fun AttachMediaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = composerIconColors(active)) {
        Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.composer_add_media))
    }
}

/** 投票の有無を切り替えるボタン。 */
@Composable
fun PollButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = composerToggleColors(),
    ) {
        val description = if (checked) R.string.composer_remove_poll else R.string.composer_add_poll
        Icon(Icons.Outlined.Poll, contentDescription = stringResource(description))
    }
}

/** CW 欄の表示を切り替えるボタン。 */
@Composable
fun ContentWarningButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = composerToggleColors(),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = stringResource(R.string.composer_content_warning))
    }
}

/**
 * 投稿欄のアイコンの色。オン = primary、オフ = primary を背景色へ寄せて沈ませた色、
 * 選べない (無効) = Material 3 の標準の無効色 (onSurface 38%)。
 */
object ComposerIconColors {
    @Composable
    fun on(): Color = MaterialTheme.colorScheme.primary

    @Composable
    fun off(): Color = lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surface, OffFraction)

    @Composable
    fun disabled(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)

    private const val OffFraction = 0.5f
    private const val DisabledAlpha = 0.38f
}

@Composable
private fun composerIconColors(active: Boolean) = IconButtonDefaults.iconButtonColors(
    contentColor = if (active) ComposerIconColors.on() else ComposerIconColors.off(),
    disabledContentColor = ComposerIconColors.disabled(),
)

@Composable
private fun composerToggleColors() = IconButtonDefaults.iconToggleButtonColors(
    contentColor = ComposerIconColors.off(),
    checkedContentColor = ComposerIconColors.on(),
    disabledContentColor = ComposerIconColors.disabled(),
)

/**
 * 文字数のリング。残りが [WarningThreshold] 以下になると残り文字数を出し、超過すると error 色になる。
 */
@Composable
fun CharacterCounter(used: Int, max: Int, modifier: Modifier = Modifier) {
    val remaining = max - used
    val progress by animateFloatAsState((used.toFloat() / max).coerceIn(0f, 1f), label = "characterProgress")
    val color by animateColorAsState(
        when {
            remaining < 0 -> MaterialTheme.colorScheme.error
            remaining <= WarningThreshold -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "characterColor",
    )
    val description = stringResource(R.string.composer_remaining_characters, remaining)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (remaining <= WarningThreshold) {
            Text(remaining.toString(), style = MaterialTheme.typography.labelLarge, color = color)
        }
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(24.dp),
            color = color,
            strokeWidth = 3.dp,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

private const val WarningThreshold = 20

@Preview(showBackground = true)
@Composable
private fun ComposerToolbarPreview() {
    QuasarTheme {
        ComposerToolbar(leading = { CharacterCounter(used = 488, max = 500) }) {
            VisibilityButton(PostVisibility.Public, onChange = {})
            AttachMediaButton(onClick = {}, enabled = false)
            PollButton(checked = true, onCheckedChange = {})
            ContentWarningButton(checked = false, onCheckedChange = {})
        }
    }
}
