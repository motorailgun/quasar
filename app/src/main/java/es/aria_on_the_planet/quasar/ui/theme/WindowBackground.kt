package es.aria_on_the_planet.quasar.ui.theme

import android.graphics.drawable.ColorDrawable
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb

/**
 * ウィンドウの背景をテーマの `background` に合わせる。Compose が描いていない所 (画面・ページの切り替えで半透明に
 * なった間など) に見えるのはこの色。テーマ (XML) の背景は Compose が描き始めるまでの近い色で、ここで正確な色
 * (dynamic color・ライト / ダーク) に揃える。`QuasarAppContent` が全 Activity で呼ぶので、各画面では背景を敷かない。
 */
@Composable
fun SyncWindowBackground() {
    val window = LocalActivity.current?.window
    val color = MaterialTheme.colorScheme.background
    SideEffect {
        window?.setBackgroundDrawable(ColorDrawable(color.toArgb()))
    }
}
