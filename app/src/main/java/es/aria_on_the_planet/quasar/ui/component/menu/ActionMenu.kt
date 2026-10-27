package es.aria_on_the_planet.quasar.ui.component.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * ポスト・プロフィールの︙のメニューの入れ物。画面下からのシート ([ModalBottomSheet]) に
 * [androidx.compose.material3.DropdownMenuItem] を並べる (文字は [MenuItemText]、区切り線は入れない)。
 * [expanded] が `false` になったら閉じるアニメーションの後で消す (項目を押して閉じたときも下へ引っ込む)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            showing = true
        } else if (showing) {
            sheetState.hide()
            showing = false
        }
    }
    if (!showing) return
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 16.dp), content = content)
    }
}

/** [ActionMenu] の項目 ([androidx.compose.material3.DropdownMenuItem] の `text`) の文字。メニュー標準の Medium より細い Regular にする。 */
@Composable
fun MenuItemText(text: String) {
    Text(text, fontWeight = FontWeight.Normal)
}
