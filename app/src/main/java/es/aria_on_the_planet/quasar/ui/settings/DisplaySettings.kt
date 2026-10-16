package es.aria_on_the_planet.quasar.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 表示に関するユーザー設定。今は既定値のみで、将来は設定画面から変更して永続化する
 * (`AppContainer.displaySettings` を差し替える)。画面からは [LocalDisplaySettings] で読む。
 */
@Immutable
data class DisplaySettings(
    /** タイムライン等でポスト本文を折りたたむ行数。これを超える分はフェードして「Show more」。`null` なら折りたたまない。 */
    val longPostMaxLines: Int? = DefaultLongPostMaxLines,
    /**
     * 一覧の上に浮かぶ UI (タイムラインのヘッダ・クイック投稿バー・プロフィールのタブ) の背景のぼかしの半径。
     * `rememberSurfaceBlurStyle` が使う。閲覧注意メディアのぼかしは別 (隠すためのものなので対象外)。
     */
    val surfaceBlurRadius: Dp = DefaultSurfaceBlurRadius,
    /** 同じぼかしに重ねる色味の不透明度 (0〜1)。大きいほど後ろが見えにくい。 */
    val surfaceBlurTintAlpha: Float = DefaultSurfaceBlurTintAlpha,
) {
    companion object {
        const val DefaultLongPostMaxLines = 8
        val DefaultSurfaceBlurRadius = 6.dp
        const val DefaultSurfaceBlurTintAlpha = 0.8f
    }
}

val LocalDisplaySettings = staticCompositionLocalOf { DisplaySettings() }
