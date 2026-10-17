package es.aria_on_the_planet.quasar.ui.component.blur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings
import es.aria_on_the_planet.quasar.ui.settings.surfaceBlurRadius

/**
 * 浮かぶ UI の背景をぼかすか。`QuasarAppContent` が [rememberSurfaceBlurEnabled] の値を提供する。
 * 切り替わるのは設定の変更・バッテリーセーバーの出入りだけなので static。
 */
val LocalSurfaceBlurEnabled = staticCompositionLocalOf { true }

/** 設定 (`DisplaySettings.surfaceBlur`) がオンで、バッテリーセーバー中でないときだけぼかす。 */
@Composable
fun rememberSurfaceBlurEnabled(): Boolean = LocalDisplaySettings.current.surfaceBlur && !rememberPowerSaveMode()

/** バッテリーセーバー中か。切り替わったら追従する。 */
@Composable
private fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(PowerManager::class.java) }
    var powerSave by remember { mutableStateOf(powerManager.isPowerSaveMode) }
    DisposableEffect(context, powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                powerSave = powerManager.isPowerSaveMode
            }
        }
        // システムのブロードキャストは NOT_EXPORTED でも届く
        context.registerReceiver(
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        // 登録までの間に切り替わっていた分
        powerSave = powerManager.isPowerSaveMode
        onDispose { context.unregisterReceiver(receiver) }
    }
    return powerSave
}

/**
 * 浮かぶ UI の背景。ぼかすときは Haze のスタイル ([haze])、ぼかさないとき ([LocalSurfaceBlurEnabled] がオフ) は
 * [haze] が `null` で、ぼかしの見た目に近い不透明な色 ([solidColor] = 色味を下地に重ねた色) で塗る。
 */
@Immutable
class SurfaceBlurStyle internal constructor(val haze: HazeBlurStyle?, val solidColor: Color)

/**
 * 一覧の上に浮かぶ UI の背景のぼかし (Haze)。半径と色味の濃さは [LocalDisplaySettings] の
 * `surfaceBlurRadius` / `surfaceBlurTintAlpha` で、アプリ全体で揃える。描くのは [surfaceBlur]。
 *
 * 下地 (`backgroundColor`) に surface を必ず敷く: 一覧そのものは背景が透明 (Scaffold が塗っている) なので、
 * 下地が無いとぼかした結果が透けて後ろの実物がくっきり見えてしまう。
 *
 * @param tintColor 重ねる色味 (不透明度は設定値で上書きする)。部品ごとに面の役割に合わせて選ぶ。
 */
@Composable
fun rememberSurfaceBlurStyle(tintColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh): SurfaceBlurStyle {
    val settings = LocalDisplaySettings.current
    val enabled = LocalSurfaceBlurEnabled.current
    val radius = settings.surfaceBlurRadius
    val background = MaterialTheme.colorScheme.surface
    val tint = tintColor.copy(alpha = settings.surfaceBlurTintAlpha)
    return remember(enabled, radius, background, tint) {
        SurfaceBlurStyle(
            haze = if (enabled) {
                HazeBlurStyle {
                    blurRadius(radius)
                    backgroundColor(background)
                    colorEffects(listOf(HazeColorEffect.tint(tint)))
                }
            } else {
                null
            },
            solidColor = tint.compositeOver(background),
        )
    }
}

/** 浮かぶ面の背景を [style] で描く (ぼかす / 不透明に塗る)。形で切るなら、この前に `clip` を付ける。 */
fun Modifier.surfaceBlur(state: HazeState, style: SurfaceBlurStyle): Modifier {
    val haze = style.haze
    return if (haze != null) hazeBlur(input = HazeInput.Sources(state), style = haze) else background(style.solidColor)
}

/**
 * ぼかしの元 (浮かぶ UI の後ろの一覧) に付ける。ぼかさない間 ([enabled] = [LocalSurfaceBlurEnabled] がオフ) は
 * 何もしない (一覧をレイヤに描き直す分を省く)。
 */
fun Modifier.surfaceBlurSource(state: HazeState, enabled: Boolean): Modifier =
    if (enabled) hazeSource(state) else this

/**
 * ぼかした面 ([rememberSurfaceBlurStyle]) の縁。後ろの一覧から浮いて見えるよう、薄い outlineVariant の 1dp。
 * タイムラインのヘッダのタブ・クイック投稿バーで共通。
 */
@Composable
fun surfaceBlurBorder(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
