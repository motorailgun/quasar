package es.aria_on_the_planet.quasar.ui.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.ui.component.dialog.LocalActionConfirmer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** 送信ボタンのハンドラ。設定で投稿前の確認がオンなら、確認してから [ComposerSession.submit] する。 */
@Composable
fun rememberConfirmedSubmit(session: ComposerSession): () -> Unit {
    val scope = rememberCoroutineScope()
    val confirmer = LocalActionConfirmer.current
    return remember(session, scope, confirmer) {
        {
            // 確認しないならその場で送る (UNDISPATCHED なので次のフレームを待たない)
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (confirmer.confirm(ConfirmTarget.Post)) session.submit()
            }
        }
    }
}
