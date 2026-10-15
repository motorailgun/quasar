package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.domain.model.withAction
import es.aria_on_the_planet.quasar.domain.model.withInteractionsOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ブースト・お気に入り・ブックマークの送信と、画面の状態への反映。ポスト一覧を持つ ViewModel が 1 つずつ持つ。
 *
 * 1. 見込みで [update] に反映する (タップへの即時の反応)。
 * 2. [send] でサーバに送り、返ったポストの件数・状態で上書きする。
 * 3. 失敗したら見込みの反映を巻き戻し、[onError] に報告する。
 *
 * 同じポスト・同じ種類の操作が送信中の間は、次の操作を受け付けない (送信順が入れ替わって状態が食い違わないように)。
 * 送信・反映は [scope] (viewModelScope) 上で行うので、画面を閉じても完了する。メインスレッドからのみ呼ぶ。
 *
 * @param update 読み込み済みの全ポストに変換をかける。同じポストが複数の一覧 (カラム・引用先等) に載っていても全てに当てる。
 */
class PostActionController(
    private val scope: CoroutineScope,
    private val send: suspend (statusId: String, action: PostAction) -> Post,
    private val update: ((Post) -> Post) -> Unit,
    private val onError: (message: String, error: Throwable) -> Unit,
) {
    private val inFlight = HashSet<Pair<String, PostActionType>>()

    fun perform(post: Post, action: PostAction) {
        val statusId = post.statusId
        val key = statusId to action.type
        if (!inFlight.add(key)) return
        update { it.withAction(statusId, action) }
        scope.launch {
            try {
                val result = send(statusId, action)
                update { it.withInteractionsOf(result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update { it.withAction(statusId, action.inverse()) }
                onError("Failed to ${action.type}${if (action.undo) " (undo)" else ""} $statusId", e)
            } finally {
                inFlight.remove(key)
            }
        }
    }
}
