package es.aria_on_the_planet.quasar.ui.composer

import es.aria_on_the_planet.quasar.domain.model.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 今の書きかけの投稿 ([ComposerSession]) をアカウントごとに 1 つ持つ (`AccountUiContext.draftStore`)。クイック投稿バーと
 * フルの投稿画面 (クイック投稿バーから開くもの・単独の投稿画面 `Route.Composer`) はここから同じものを受け取る。
 * 詳細のパネルの中の画面は別のインスタンス (`AccountUiContext.panelDraftStore`) を使う。
 *
 * 各 session は自分のコルーチンの範囲を持ち、画面が閉じても添付のアップロードは続く。プロセスが終わると消える
 * (保存はしない)。
 *
 * 投稿できたらここで下書きを終えて空のものに替え、[published] で知らせる。同じ下書きを複数の画面
 * (メイン画面とポスト画面等) が表示しているので、後始末は画面ではなくここで 1 回だけ行う。
 *
 * @param create 新しい session を作る (`AccountUiContext.composerSession`)。2 つ目の引数は投稿できたときに呼ぶ。
 */
class DraftStore(
    private val create: (CoroutineScope, (Post) -> Unit) -> ComposerSession,
) {
    private val _current = MutableStateFlow<ComposerSession?>(null)

    /** 今の下書き。[open] するまでは `null`。 */
    val current: StateFlow<ComposerSession?> = _current.asStateFlow()

    private val _published = MutableSharedFlow<Post>(extraBufferCapacity = 8)

    /** 投稿できたポスト (フル画面を閉じる・スレッドに足す等)。この時点で [current] は空の下書きに替わっている。 */
    val published: SharedFlow<Post> = _published.asSharedFlow()

    /** 今の下書きを返す。まだ無ければ作る。 */
    fun open(): ComposerSession = _current.value ?: newDraft()

    /** 投稿・破棄で今の下書きを終え、空の下書きに替える。 */
    fun finish() {
        val current = _current.value ?: return
        current.close()
        newDraft()
    }

    /** 今の下書きのアップロード等を止める (ログアウト)。以後は使わない。 */
    fun close() {
        _current.value?.close()
        _current.value = null
    }

    private fun newDraft(): ComposerSession {
        lateinit var draft: ComposerSession
        draft = create(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) { post ->
            // 投稿した下書きがまだ今のものなら終える (破棄等で替わっていれば何もしない)
            if (_current.value === draft) finish()
            _published.tryEmit(post)
        }
        _current.value = draft
        return draft
    }
}
