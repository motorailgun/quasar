package es.aria_on_the_planet.quasar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.di.AppContainer
import es.aria_on_the_planet.quasar.ui.component.dialog.ActionConfirmationHost
import es.aria_on_the_planet.quasar.ui.component.player.LocalInlinePlayback
import es.aria_on_the_planet.quasar.ui.component.player.rememberInlinePlayback
import es.aria_on_the_planet.quasar.ui.component.time.LocalNow
import es.aria_on_the_planet.quasar.ui.component.time.rememberTickingNow
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings
import es.aria_on_the_planet.quasar.ui.theme.QuasarTheme

/**
 * 各 Activity の setContent の最上位。テーマとアプリ全体の設定・表示用の現在時刻 ([LocalNow]) を提供し、
 * 操作の前の確認のダイアログ ([ActionConfirmationHost]) を出す。
 * 設定の読み込み前は中身を描かない (既定値で描いてから切り替わらないように。普通は最初のフレームまでに読み終わる)。
 *
 * @param inlinePlayback 一覧での動画等のインライン再生を有効にするか (Activity ごとに Player を 1 つ持つ)。
 */
@Composable
fun QuasarAppContent(
    container: AppContainer,
    inlinePlayback: Boolean = true,
    content: @Composable () -> Unit,
) {
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
    val playback = if (inlinePlayback) rememberInlinePlayback(container.okHttpClient) else null
    val now by rememberTickingNow()
    QuasarTheme {
        val loaded = settings
        if (loaded != null) {
            CompositionLocalProvider(
                LocalDisplaySettings provides loaded.display,
                LocalInlinePlayback provides playback,
                LocalNow provides now,
            ) {
                ActionConfirmationHost(loaded.confirmations, content)
            }
        }
    }
}
