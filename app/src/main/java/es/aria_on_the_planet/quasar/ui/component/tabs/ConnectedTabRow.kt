package es.aria_on_the_planet.quasar.ui.component.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 画面内のタブ切り替え。Material 3 Expressive の連結ボタン (connected button group) で、各ボタンは等幅。
 * 選択中のボタンは塗り (primary)、押下で内側の角が締まる。
 *
 * 一覧の上に常に出ているので目立ちすぎないよう、既定 (ピル型・40dp) より細く (ExtraSmall = 32dp)、
 * 角丸も小さめの角丸長方形にしている。
 *
 * タイムラインのカラム切り替え (`TimelineHeader`) はぼかし・幅の追従があるので別実装。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        labels.forEachIndexed { index, label ->
            ToggleButton(
                checked = index == selectedIndex,
                onCheckedChange = { onSelect(index) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.Tab },
                buttonSize = ToggleButtonSize.ExtraSmall,
                shapes = tabShapes(isFirst = index == 0, isLast = index == labels.lastIndex),
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 外側の角は [OuterCorner]、隣と接する内側の角は [InnerCorner] (押下中は [PressedInnerCorner])。選択中は全部 [OuterCorner]。 */
private fun tabShapes(isFirst: Boolean, isLast: Boolean): ToggleButtonShapes {
    fun shape(inner: Dp) = RoundedCornerShape(
        topStart = if (isFirst) OuterCorner else inner,
        bottomStart = if (isFirst) OuterCorner else inner,
        topEnd = if (isLast) OuterCorner else inner,
        bottomEnd = if (isLast) OuterCorner else inner,
    )
    return ToggleButtonShapes(
        shape = shape(InnerCorner),
        pressedShape = shape(PressedInnerCorner),
        checkedShape = RoundedCornerShape(OuterCorner),
    )
}

private val OuterCorner = 10.dp
private val InnerCorner = 4.dp
private val PressedInnerCorner = 2.dp
