package es.aria_on_the_planet.quasar.ui.settings

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.settings.DisplaySettings

/** 表示設定。`QuasarAppContent` が `SettingsStore` の値を提供する。 */
val LocalDisplaySettings = staticCompositionLocalOf { DisplaySettings() }

/** 浮かぶ UI の背景のぼかしの半径。 */
val DisplaySettings.surfaceBlurRadius: Dp
    get() = surfaceBlurRadiusDp.dp
