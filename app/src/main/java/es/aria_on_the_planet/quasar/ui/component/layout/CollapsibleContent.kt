package es.aria_on_the_planet.quasar.ui.component.layout

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.ui.component.modifier.bottomFadingEdge

/**
 * [collapsedMaxHeight] を超える内容は折りたたみ、下端をフェードさせて `>` ボタンで展開できるようにする。
 * 収まる場合はそのまま表示する。
 */
@Composable
fun CollapsibleContent(
    collapsedMaxHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var contentHeight by remember { mutableIntStateOf(0) }
    val maxPx = with(LocalDensity.current) { collapsedMaxHeight.roundToPx() }
    val overflows = contentHeight > maxPx
    val fade by animateFloatAsState(if (overflows && !expanded) 1f else 0f, label = "collapseFade")
    // `>` を下向き (折りたたみ時) ⇔ 上向き (展開時) に回す
    val rotation by animateFloatAsState(if (expanded) -90f else 90f, label = "chevronRotation")

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .then(if (expanded) Modifier else Modifier.heightIn(max = collapsedMaxHeight))
                .clipToBounds()
                .bottomFadingEdge(height = FadeHeight, strength = { fade }),
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
            FilledTonalIconButton(onClick = { expanded = !expanded }) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

private val FadeHeight = 48.dp
