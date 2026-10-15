package es.aria_on_the_planet.quasar.ui.component.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant

/** 相対時刻 (「3m」等) や投票の残り時間を更新する間隔。 */
const val NowTickIntervalMillis = 15_000L

/**
 * 表示用の現在時刻。[QuasarAppContent][es.aria_on_the_planet.quasar.ui.QuasarAppContent] が
 * [NowTickIntervalMillis] ごとに更新して提供する。相対時刻を出す Composable はここから読むことで、
 * 時間が経つと再コンポーズされて表示が進む。未提供 (プレビュー等) ではその時点の時刻。
 */
val LocalNow = compositionLocalOf<Instant> { Instant.now() }

/**
 * [intervalMillis] ごとに進む現在時刻。画面が前面 (STARTED) にある間だけ進め、前面に戻ったときはすぐ更新する。
 */
@Composable
fun rememberTickingNow(intervalMillis: Long = NowTickIntervalMillis): State<Instant> {
    val now = remember { mutableStateOf(Instant.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, intervalMillis) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now.value = Instant.now()
                delay(intervalMillis)
            }
        }
    }
    return now
}
