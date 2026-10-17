package es.aria_on_the_planet.quasar.ui.component.blur

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings
import es.aria_on_the_planet.quasar.ui.settings.surfaceBlurRadius

/**
 * 一覧の上に浮かぶ UI の背景のぼかし (Haze)。半径と色味の濃さは [LocalDisplaySettings] の
 * `surfaceBlurRadius` / `surfaceBlurTintAlpha` で、アプリ全体で揃える。
 *
 * 下地 (`backgroundColor`) に surface を必ず敷く: 一覧そのものは背景が透明 (Scaffold が塗っている) なので、
 * 下地が無いとぼかした結果が透けて後ろの実物がくっきり見えてしまう。
 *
 * @param tintColor 重ねる色味 (不透明度は設定値で上書きする)。部品ごとに面の役割に合わせて選ぶ。
 */
@Composable
fun rememberSurfaceBlurStyle(tintColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh): HazeBlurStyle {
    val settings = LocalDisplaySettings.current
    val radius = settings.surfaceBlurRadius
    val background = MaterialTheme.colorScheme.surface
    val tint = tintColor.copy(alpha = settings.surfaceBlurTintAlpha)
    return remember(radius, background, tint) {
        HazeBlurStyle {
            blurRadius(radius)
            backgroundColor(background)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
        }
    }
}

/**
 * ぼかした面 ([rememberSurfaceBlurStyle]) の縁。後ろの一覧から浮いて見えるよう、薄い outlineVariant の 1dp。
 * タイムラインのヘッダのタブ・クイック投稿バーで共通。
 */
@Composable
fun surfaceBlurBorder(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
