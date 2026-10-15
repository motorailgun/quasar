package es.aria_on_the_planet.quasar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.di.AppContainer
import es.aria_on_the_planet.quasar.ui.component.player.LocalInlinePlayback
import es.aria_on_the_planet.quasar.ui.component.player.rememberInlinePlayback
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings
import es.aria_on_the_planet.quasar.ui.theme.QuasarTheme

/**
 * 各 Activity の setContent の最上位。テーマとアプリ全体の設定を提供する。
 *
 * @param inlinePlayback 一覧での動画等のインライン再生を有効にするか (Activity ごとに Player を 1 つ持つ)。
 */
@Composable
fun QuasarAppContent(
    container: AppContainer,
    inlinePlayback: Boolean = true,
    content: @Composable () -> Unit,
) {
    val displaySettings by container.displaySettings.collectAsStateWithLifecycle()
    val playback = if (inlinePlayback) rememberInlinePlayback(container.okHttpClient) else null
    QuasarTheme {
        CompositionLocalProvider(
            LocalDisplaySettings provides displaySettings,
            LocalInlinePlayback provides playback,
            content = content,
        )
    }
}
