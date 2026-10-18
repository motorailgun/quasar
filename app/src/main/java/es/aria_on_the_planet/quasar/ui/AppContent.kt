package es.aria_on_the_planet.quasar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.di.AppContainer
import es.aria_on_the_planet.quasar.ui.component.blur.LocalSurfaceBlurEnabled
import es.aria_on_the_planet.quasar.ui.component.blur.rememberSurfaceBlurEnabled
import es.aria_on_the_planet.quasar.ui.component.dialog.ActionConfirmationHost
import es.aria_on_the_planet.quasar.ui.component.link.rememberNewTaskUriHandler
import es.aria_on_the_planet.quasar.ui.component.player.LocalInlinePlayback
import es.aria_on_the_planet.quasar.ui.component.player.rememberInlinePlayback
import es.aria_on_the_planet.quasar.ui.component.time.LocalNow
import es.aria_on_the_planet.quasar.ui.component.time.rememberTickingNow
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings
import es.aria_on_the_planet.quasar.ui.theme.QuasarTheme
import es.aria_on_the_planet.quasar.ui.theme.SyncWindowBackground

/**
 * Activity の setContent の最上位 (アプリ本体の MainActivity とデバッグ用ギャラリー)。テーマとアプリ全体の設定・表示用の現在時刻 ([LocalNow])・外部リンクの開き方 ([LocalUriHandler]) を提供し、
 * 操作の前の確認のダイアログ ([ActionConfirmationHost]) を出す。ウィンドウの背景もテーマに合わせる ([SyncWindowBackground])。
 * 設定の読み込み前は中身を描かない (既定値で描いてから切り替わらないように。普通は最初のフレームまでに読み終わる)。
 */
@Composable
fun QuasarAppContent(
    container: AppContainer,
    content: @Composable () -> Unit,
) {
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
    val playback = rememberInlinePlayback(container.okHttpClient)
    val now by rememberTickingNow()
    QuasarTheme {
        SyncWindowBackground()
        val loaded = settings
        if (loaded != null) {
            CompositionLocalProvider(
                LocalDisplaySettings provides loaded.display,
                LocalInlinePlayback provides playback,
                LocalNow provides now,
                LocalUriHandler provides rememberNewTaskUriHandler(),
            ) {
                // 設定とバッテリーセーバーから決まるので、表示設定を提供した内側で求める
                CompositionLocalProvider(LocalSurfaceBlurEnabled provides rememberSurfaceBlurEnabled()) {
                    ActionConfirmationHost(loaded.confirmations, content)
                }
            }
        }
    }
}
