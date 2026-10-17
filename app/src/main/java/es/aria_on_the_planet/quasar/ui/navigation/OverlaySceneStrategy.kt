package es.aria_on_the_planet.quasar.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * 下の画面を表示したまま、その上に重ねて出す画面 (メディアビューア)。今までの透過 Activity の代わり。
 *
 * * 下の画面はコンポジションに残り、STARTED のまま (透過 Activity と同じく ON_STOP にならない)。
 * * 出入りのアニメーションは重ねる画面が自分で描く (`NavDisplay` の遷移は付かない)。閉じ終わったら `back()` する。
 * * 「戻る」は重ねる画面の中の `NavigationBackHandler` が先に受ける。
 *
 * 重ねる画面のエントリには `metadata = OverlaySceneStrategy.overlay()` を付ける。
 */
class OverlaySceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        if (last.metadata[OverlayKey] != true) return null
        val below = entries.dropLast(1)
        return OverlayEntryScene(key = last.contentKey, entry = last, previousEntries = below, overlaidEntries = below)
    }

    companion object {
        /** このエントリを下の画面に重ねて出す。 */
        fun overlay() = metadata { put(OverlayKey, true) }

        private object OverlayKey : NavMetadataKey<Boolean>
    }
}

private class OverlayEntryScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
) : OverlayScene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable () -> Unit = { entry.Content() }
}
