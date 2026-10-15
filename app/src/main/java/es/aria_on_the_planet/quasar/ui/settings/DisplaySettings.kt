package es.aria_on_the_planet.quasar.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 表示に関するユーザー設定。今は既定値のみで、将来は設定画面から変更して永続化する
 * (`AppContainer.displaySettings` を差し替える)。画面からは [LocalDisplaySettings] で読む。
 */
@Immutable
data class DisplaySettings(
    /** タイムライン等でポスト本文を折りたたむ行数。これを超える分はフェードして「Show more」。`null` なら折りたたまない。 */
    val longPostMaxLines: Int? = DefaultLongPostMaxLines,
) {
    companion object {
        const val DefaultLongPostMaxLines = 8
    }
}

val LocalDisplaySettings = staticCompositionLocalOf { DisplaySettings() }
