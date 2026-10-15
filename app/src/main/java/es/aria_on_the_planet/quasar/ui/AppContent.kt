package es.aria_on_the_planet.quasar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.di.AppContainer
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings
import es.aria_on_the_planet.quasar.ui.theme.QuasarTheme

/** 各 Activity の setContent の最上位。テーマとアプリ全体の設定を提供する。 */
@Composable
fun QuasarAppContent(container: AppContainer, content: @Composable () -> Unit) {
    val displaySettings by container.displaySettings.collectAsStateWithLifecycle()
    QuasarTheme {
        CompositionLocalProvider(LocalDisplaySettings provides displaySettings, content = content)
    }
}
