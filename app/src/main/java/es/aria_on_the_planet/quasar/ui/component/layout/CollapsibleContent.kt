package es.aria_on_the_planet.quasar.ui.component.layout

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.ui.component.modifier.bottomFadingEdge

object CollapsibleContentDefaults {
    val FadeHeight: Dp = 48.dp
}

/**
 * 折りたたみ・展開の状態。構成変更やリストのスクロールでも保持する。
 * CW・長文・ピン留めなど、開閉を持つ部品で共通に使う。
 *
 * @param key 状態を区別するキー (ポストの statusId など)。変わると状態も作り直す。
 */
@Composable
fun rememberExpandedState(key: Any? = null, initiallyExpanded: Boolean = false): MutableState<Boolean> =
    rememberSaveable(key) { mutableStateOf(initiallyExpanded) }

/**
 * [collapsedMaxHeight] を超える内容は折りたたみ、下端をフェードさせて [toggle] で展開できるようにする。
 * 収まる場合はそのまま表示し、[toggle] も出さない。
 *
 * @param expanded 開閉状態。呼び出し側でキー付きの状態を渡せる。
 * @param toggle 開閉ボタン。既定は中央の `>` ボタン ([ChevronToggle])。
 */
@Composable
fun CollapsibleContent(
    collapsedMaxHeight: Dp,
    modifier: Modifier = Modifier,
    expanded: MutableState<Boolean> = rememberExpandedState(),
    fadeHeight: Dp = CollapsibleContentDefaults.FadeHeight,
    toggle: @Composable (expanded: Boolean, onToggle: () -> Unit) -> Unit = { isExpanded, onToggle ->
        ChevronToggle(expanded = isExpanded, onToggle = onToggle)
    },
    content: @Composable () -> Unit,
) {
    var isExpanded by expanded
    var contentHeight by remember { mutableIntStateOf(0) }
    val maxPx = with(LocalDensity.current) { collapsedMaxHeight.roundToPx() }
    val overflows = contentHeight > maxPx
    val fade by animateFloatAsState(if (overflows && !isExpanded) 1f else 0f, label = "collapseFade")

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .then(if (isExpanded) Modifier else Modifier.heightIn(max = collapsedMaxHeight))
                .clipToBounds()
                .bottomFadingEdge(height = fadeHeight, strength = { fade }),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { contentHeight = it.height },
            ) {
                content()
            }
        }
        if (overflows) {
            toggle(isExpanded) { isExpanded = !isExpanded }
        }
    }
}
