package es.aria_on_the_planet.quasar.ui.component.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import okhttp3.OkHttpClient

/**
 * 一覧でのインライン再生に使う [PlaybackController]。`null` ならインライン再生せず、タップでビューアを開く。
 * [QuasarAppContent][es.aria_on_the_planet.quasar.ui.QuasarAppContent] がアプリで 1 つ提供する (再生できるのは同時に 1 つ)。
 * 別の画面を開くと、元の画面のメディアはコンポジションから外れて自分の再生を止める。
 */
val LocalInlinePlayback = staticCompositionLocalOf<PlaybackController?> { null }

/**
 * アプリ全体のインライン再生。自動再生はしない (帯域の細いサーバも多いため、タップで再生)。
 * アプリが背面に回ったら ([Lifecycle.Event.ON_STOP]) Player を解放してデコーダを手放す。
 */
@Composable
fun rememberInlinePlayback(okHttpClient: OkHttpClient): PlaybackController {
    val context = LocalContext.current.applicationContext
    val controller = remember(okHttpClient) { PlaybackController { createMediaPlayer(context, okHttpClient) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.release()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }
    return controller
}
