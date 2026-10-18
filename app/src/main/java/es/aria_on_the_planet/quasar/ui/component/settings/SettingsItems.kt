package es.aria_on_the_planet.quasar.ui.component.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/*
 * AOSP の設定画面 (Android 16) 風の部品。見出し ([SettingsCategory]) の下に、角丸の面をつなげたまとまり
 * ([SettingsGroup]) で項目を並べる。まとまりの両端だけ大きく角丸、項目の間は小さい角丸 + 隙間。
 */

/** まとまりの両端の角丸。 */
private val GroupShape = RoundedCornerShape(24.dp)

/** まとまりの中の項目の角丸 (両端は [GroupShape] で切る)。 */
private val ItemShape = RoundedCornerShape(4.dp)

/** まとまりの見出し (「Brightness」等)。 */
@Composable
fun SettingsCategory(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp),
    )
}

/**
 * 見出し + 角丸の面をつなげた項目のまとまり。[content] には下の `Settings*Item` を並べる。
 * 両端の大きな角丸はまとまり全体を切って付けるので、項目は何個でも、条件で出し入れしてもよい。
 */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.padding(horizontal = 16.dp)) {
        if (title != null) SettingsCategory(title) else Spacer(Modifier.height(16.dp))
        Column(
            Modifier.clip(GroupShape),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun itemShapes(): ListItemShapes = ListItemDefaults.shapes(shape = ItemShape, selectedShape = ItemShape)

/**
 * 項目の色。オンのスイッチの項目 (checked) も同じ色にする (AOSP と同じく、面の色で選択を示さない)。
 * [contentColor] は題名とアイコンの色 (ログアウト等の取り消せない操作は error)。
 */
@Composable
private fun itemColors(contentColor: Color = Color.Unspecified): ListItemColors {
    val colors = MaterialTheme.colorScheme
    return ListItemDefaults.segmentedColors(
        containerColor = colors.surfaceBright,
        contentColor = contentColor,
        leadingContentColor = contentColor,
        selectedContainerColor = colors.surfaceBright,
        selectedContentColor = colors.onSurface,
        selectedSupportingContentColor = colors.onSurfaceVariant,
        selectedLeadingContentColor = colors.onSurfaceVariant,
        selectedTrailingContentColor = colors.onSurfaceVariant,
    )
}

@Composable
private fun Summary(summary: String?): (@Composable () -> Unit)? = summary?.let { { Text(it) } }

/** 押すと別のページ・ダイアログを開く項目。[icon] はトップの一覧用。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsNavigationItem(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = itemShapes(),
        colors = itemColors(),
        modifier = modifier,
        enabled = enabled,
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        supportingContent = Summary(summary),
        trailingContent = if (showChevron) {
            { Icon(Symbols.Filled.KeyboardArrowRight, contentDescription = null) }
        } else {
            null
        },
        content = { Text(title) },
    )
}

/** 押すとその場で操作する項目 (ログアウト等)。[destructive] なら題名とアイコンを error 色にする。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsActionItem(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = itemShapes(),
        colors = itemColors(contentColor = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified),
        modifier = modifier,
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        supportingContent = Summary(summary),
        content = { Text(title) },
    )
}

/** オン / オフの項目。行のどこを押しても切り替わる。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSwitchItem(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = itemShapes(),
        colors = itemColors(),
        modifier = modifier,
        enabled = enabled,
        supportingContent = Summary(summary),
        trailingContent = { SettingsSwitch(checked, onCheckedChange = null, enabled = enabled) },
        content = { Text(title) },
    )
}

/**
 * 左右に分かれた項目 (AOSP の「Adaptive brightness」の形)。左側 (題名) を押すと詳細のページ ([onClick])、
 * 縦線の右のスイッチで全体のオン / オフ。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSplitSwitchItem(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = itemShapes(),
        colors = itemColors(),
        modifier = modifier,
        supportingContent = Summary(summary),
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbols.Filled.KeyboardArrowRight, contentDescription = null)
                VerticalDivider(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                SettingsSwitch(checked, onCheckedChange)
            }
        },
        content = { Text(title) },
    )
}

/** AOSP と同じく、つまみに ✓ / ✕ を出すスイッチ。 */
@Composable
fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            Icon(
                if (checked) Symbols.Filled.Check else Symbols.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}
