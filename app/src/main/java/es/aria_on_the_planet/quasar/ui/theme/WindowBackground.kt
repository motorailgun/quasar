package es.aria_on_the_planet.quasar.ui.theme

import android.graphics.drawable.ColorDrawable
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb

/**
 * ウィンドウの背景をテーマの `background` に合わせる。Compose が描いていない所 (画面・ページの切り替えで半透明に
 * なった間など) に見えるのはこの色。テーマ (XML) の背景は Compose が描き始めるまでの近い色で、ここで正確な色
 * (dynamic color・ライト / ダーク) に揃える。`QuasarAppContent` が全 Activity で呼ぶので、各画面では背景を敷かない。
 *
 * 透過ウィンドウ (`windowIsTranslucent`、メディアビューア) は後ろの画面を見せるためのものなので触らない。
 */
@Composable
fun SyncWindowBackground() {
    val activity = LocalActivity.current
    val translucent = remember(activity) {
        activity?.theme?.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent))
            ?.use { it.getBoolean(0, false) } ?: true
    }
    val color = MaterialTheme.colorScheme.background
    SideEffect {
        if (!translucent) activity?.window?.setBackgroundDrawable(ColorDrawable(color.toArgb()))
    }
}
