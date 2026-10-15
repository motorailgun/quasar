package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.domain.model.toggleOn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** アクション行のボタンが押されたときに呼ばれる。どの操作 (する / 取り消す) かは [rememberPostActionHandler] が決める。 */
typealias OnPostAction = (Post, PostActionType) -> Unit

/**
 * 操作を送る前の確認の差し込み口。確認モーダル (「ブーストしますか?」等) を出す場合はここで表示して結果を待つ。
 * `null` を返すと取りやめ。操作を変えて返してもよい (将来ブーストの公開範囲を選ばせる等)。
 */
fun interface PostActionConfirmation {
    suspend fun confirm(post: Post, action: PostAction): PostAction?

    companion object {
        /** 確認せずにそのまま送る。 */
        val None = PostActionConfirmation { _, action -> action }
    }
}

/**
 * アクション行のボタンのハンドラ。押されたときのポストの状態から操作 (済みなら取り消し) を決め、
 * [confirmation] を通してから [perform] (ViewModel) に渡す。
 */
@Composable
fun rememberPostActionHandler(
    perform: (Post, PostAction) -> Unit,
    confirmation: PostActionConfirmation = PostActionConfirmation.None,
): OnPostAction {
    val scope = rememberCoroutineScope()
    val currentPerform by rememberUpdatedState(perform)
    val currentConfirmation by rememberUpdatedState(confirmation)
    return remember(scope) {
        { post, type ->
            // 確認なしならその場で送る (UNDISPATCHED なので、すぐ返る確認では次のフレームを待たない)
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val action = currentConfirmation.confirm(post, type.toggleOn(post)) ?: return@launch
                currentPerform(post, action)
            }
        }
    }
}
