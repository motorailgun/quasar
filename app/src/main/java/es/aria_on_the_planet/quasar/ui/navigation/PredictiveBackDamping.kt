package es.aria_on_the_planet.quasar.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import es.aria_on_the_planet.quasar.domain.model.settings.PredictiveBackSettings
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.ln

/*
 * 予測型「戻る」で指で引いている間の動きを抑える (幅いっぱい引いても今の画面は幅の
 * [PredictiveBackSettings.maxDragFraction] までしかずれず、離すと残りを一気に抜ける)。
 *
 * NavDisplay は引いた量 p をそのまま遷移の進み具合として seek し、離した後は同じ遷移の残りを等速で再生する
 * (Nav3 1.2.0 の `NavDisplay` / Compose の `SeekableTransitionState.animateTo` のソースで確認。ドキュメントには無い)。
 * なので遷移 ([predictivePopTransition]) は直線で全部抜ける動きにし、引いている間だけ各画面に
 * 「目標の位置 − p × 幅」の補正を足して見た目を [dampedProgress] にする。離したら補正を NavDisplay の残りの再生と
 * 同じ時間・曲線で 0 に戻すので、見た目は今の位置から終点 (抜けきる / 元の位置) へ一直線に動く。
 * **Nav3 を上げたら、引いている間・離した後・取り消しの動きを実機で確かめる** (上の 2 つの挙動に依存している)。
 */

/**
 * 引いた量 [progress] (0〜1) に対する、今の画面のずれ (幅に対する割合)。log で伸び、0 で 0・1 で最大
 * ([PredictiveBackSettings.maxDragFraction])。`1 +` は log を原点に通すため (無いと小さい p で負に発散する)。
 */
internal fun PredictiveBackSettings.dampedProgress(progress: Float): Float =
    maxDragFraction * ln(1f + dragLogSteepness * progress) / ln(1f + dragLogSteepness)

/** 補正の状態。[QuasarNavHost] が 1 つ持ち、[rememberPredictiveBackDampingDecorator] で各画面に当てる。 */
@Stable
class PredictiveBackDamping {
    /** 設定。[QuasarNavHost] が設定の読み込み・変更に合わせて入れる (遷移 ([predictivePopTransition]) の長さもここから)。 */
    var settings: PredictiveBackSettings by mutableStateOf(PredictiveBackSettings())

    /** 引いている (または離した後の動きの途中の) 今の画面・戻り先の画面の contentKey。 */
    private var leavingKey: Any? by mutableStateOf(null)
    private var returningKey: Any? by mutableStateOf(null)

    /** 今の画面に足す補正 (幅に対する割合、0 以下)。戻り先は遷移のずれが 1/[UnderlapFraction] なので補正も同じ比。 */
    private val correction = Animatable(0f)

    /** 引いている途中に NavDisplay の onBack が呼ばれた (= 戻ることが決まった)。 */
    private var committed = false

    fun offsetFraction(contentKey: Any): Float = when (contentKey) {
        leavingKey -> correction.value
        returningKey -> correction.value / UnderlapFraction
        else -> 0f
    }

    /** NavDisplay の onBack から呼ぶ (ボタンでの「戻る」でも呼ばれるが、引いている間以外は見ない)。 */
    fun onBack() {
        committed = true
    }

    suspend fun run(dispatcher: NavigationEventDispatcher) {
        var progress = 0f
        dispatcher.transitionState.collectLatest { state ->
            when (state) {
                is NavigationEventTransitionState.InProgress -> {
                    if (leavingKey == null && !startTracking(dispatcher)) return@collectLatest
                    progress = state.latestEvent.progress
                    correction.snapTo(settings.dampedProgress(progress) - progress)
                }
                NavigationEventTransitionState.Idle -> {
                    if (leavingKey == null) return@collectLatest
                    try {
                        withFrameNanos {} // 戻る場合、onBack は Idle になるのと前後しうるので 1 フレーム待つ
                        // NavDisplay と同じ時間・曲線: 戻るなら残りを等速、取り消しなら tween の既定の曲線
                        val millis = settings.transitionMillis
                        val spec = if (committed) {
                            tween<Float>(((1f - progress) * millis).toInt(), easing = LinearEasing)
                        } else {
                            tween((progress * millis).toInt())
                        }
                        correction.animateTo(0f, spec)
                    } finally {
                        // 次のジェスチャで止められた場合も、その画面の補正は外す
                        leavingKey = null
                        returningKey = null
                    }
                }
            }
        }
    }

    /** 今のジェスチャを NavDisplay が受けているなら、動かす 2 画面を覚える。それ以外 (投稿画面・ビューア等) は無視。 */
    private fun startTracking(dispatcher: NavigationEventDispatcher): Boolean {
        val history = dispatcher.history.value
        val current = history.mergedHistory.getOrNull(history.currentIndex) as? SceneInfo<*> ?: return false
        val back = history.mergedHistory.getOrNull(history.currentIndex - 1) as? SceneInfo<*> ?: return false
        leavingKey = current.scene.entries.last().contentKey
        returningKey = back.scene.entries.last().contentKey
        committed = false
        return true
    }
}

/** [damping] の補正を各画面に当てる NavEntryDecorator。補正の追跡もここで始める。 */
@Composable
fun <T : Any> rememberPredictiveBackDampingDecorator(damping: PredictiveBackDamping): NavEntryDecorator<T> {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    if (dispatcher != null) LaunchedEffect(damping, dispatcher) { damping.run(dispatcher) }
    return remember(damping) {
        NavEntryDecorator { entry ->
            Box(
                Modifier.graphicsLayer { translationX = damping.offsetFraction(entry.contentKey) * size.width },
                propagateMinConstraints = true,
            ) {
                entry.Content()
            }
        }
    }
}
