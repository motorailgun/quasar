package es.aria_on_the_planet.quasar.ui.component.blur

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState

/**
 * 画面全体を元にしたぼかし (︙のメニューのシートの背景) の [HazeState]。設定でオフ・[BackdropBlurHost] の外なら `null`。
 * シートは別のウィンドウだが、Haze は元と効果が別のウィンドウなら画面の座標で合わせる (`HazePositionStrategy.Auto`)。
 */
val LocalBackdropBlur = staticCompositionLocalOf<HazeState?> { null }

/**
 * [content] 全体を [LocalBackdropBlur] のぼかしの元にする。`QuasarAppContent` が置く。
 *
 * 元は常に付けておく。Haze の元は使う効果が無い間はレイヤを作らずそのまま描くので、メニューを開いていない間の負荷は
 * ほぼ無い。開いている間だけ付ける形にすると、効果 (シート) より後から付いた元が使われていることに気づかず、
 * 中身をレイヤに描かないため何も映らなかった (Haze 2.0.1)。
 *
 * @param enabled 設定 (`DisplaySettings.menuBlur`) がオンでバッテリーセーバー中でないか。`false` なら元を付けず、
 *   [LocalBackdropBlur] は `null` (メニューは不透明な面になる)。
 */
@Composable
fun BackdropBlurHost(enabled: Boolean, content: @Composable () -> Unit) {
    val state = remember { HazeState() }
    Box(Modifier.surfaceBlurSource(state, enabled), propagateMinConstraints = true) {
        CompositionLocalProvider(LocalBackdropBlur provides state.takeIf { enabled }, content = content)
    }
}
