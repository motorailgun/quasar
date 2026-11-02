package es.aria_on_the_planet.quasar.ui.component.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.ui.component.blur.LocalBackdropBlur
import es.aria_on_the_planet.quasar.ui.component.blur.rememberSurfaceBlurStyle
import es.aria_on_the_planet.quasar.ui.component.blur.surfaceBlur
import kotlinx.coroutines.launch

/**
 * ポスト・プロフィールの︙のメニューの入れ物。画面下からのシート ([ModalBottomSheet]) に
 * [androidx.compose.material3.DropdownMenuItem] を並べる (文字は [MenuItemText]、区切り線は入れない)。
 * [expanded] が `false` になったら閉じるアニメーションの後で消す (項目を押して閉じたときも下へ引っ込む)。
 *
 * 背景は設定 (`DisplaySettings.menuBlur`) に従って後ろの画面をぼかす ([LocalBackdropBlur])。シート自体の面は透明にし、
 * 持ち手・余白 (ナビゲーションバー等) も含めて中身の側で背景を描く (シートの `modifier` はシートの位置のずれより外側に
 * 付くので、そこにぼかしを付けると画面の上端に描かれてしまう)。
 *
 * @param header 項目の上に出すもの (ポストのメニューなら対象のポストの縮小表示)。シートは画面の高さを超えて伸びないので、
 *   中身全体を縦にスクロールできるようにしてある (横画面等でも全項目に届く)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    header: (@Composable () -> Unit)? = null,
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
    val blurState = LocalBackdropBlur.current
    val scope = rememberCoroutineScope()
    val blurStyle = rememberSurfaceBlurStyle(tintColor = BottomSheetDefaults.ContainerColor, enabled = blurState != null)
    val background = blurState?.let { Modifier.surfaceBlur(it, blurStyle) }
        ?: Modifier.background(BottomSheetDefaults.ContainerColor)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Box(Modifier.fillMaxWidth()) {
            // 開くときに弾むとシートの面だけが縦に伸びて中身は元の高さのままなので、背景は下へはみ出させて描いておく
            Box(Modifier.matchParentSize().extendDownward(BackgroundOverflow).then(background))
            Column(Modifier.windowInsetsPadding(BottomSheetDefaults.modalWindowInsets)) {
                BottomSheetDefaults.DragHandle(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(role = Role.Button) {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
                        },
                )
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 16.dp),
                ) {
                    header?.invoke()
                    content()
                }
            }
        }
    }
}

/** 大きさは今のまま、描く範囲だけ下へ [extra] 広げる (シートの面で切られるので普段は見えない)。 */
private fun Modifier.extendDownward(extra: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight + extra.roundToPx()))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
}

/** 弾んだときに伸びる分より大きく。 */
private val BackgroundOverflow = 64.dp

/** [ActionMenu] の項目 ([androidx.compose.material3.DropdownMenuItem] の `text`) の文字。メニュー標準の Medium より細い Regular にする。 */
@Composable
fun MenuItemText(text: String) {
    Text(text, fontWeight = FontWeight.Normal)
}
