package es.aria_on_the_planet.quasar.ui.composer

import es.aria_on_the_planet.quasar.data.session.Session
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
 * 今の書きかけの投稿 ([ComposerSession]) をアプリ全体で 1 つ持つ。クイック投稿バーとフルの投稿画面
 * (`ComposerActivity` を含む) はここから同じものを受け取る。
 *
 * 各 session は自分のコルーチンの範囲を持ち、画面が閉じても添付のアップロードは続く。プロセスが終わると消える
 * (保存はしない)。
 *
 * 投稿できたらここで下書きを終えて空のものに替え、[published] で知らせる。同じ下書きを複数の画面
 * (メイン画面とポスト画面等) が表示しているので、後始末は画面ではなくここで 1 回だけ行う。
 *
 * @param create 新しい session を作る (`AppContainer` がリポジトリを渡す)。3 つ目の引数は投稿できたときに呼ぶ。
 */
class DraftStore(
    private val create: (Session, CoroutineScope, (Post) -> Unit) -> ComposerSession,
) {
    private var owner: Session? = null
    private val _current = MutableStateFlow<ComposerSession?>(null)

    /** 今の下書き。[open] するまでは `null`。 */
    val current: StateFlow<ComposerSession?> = _current.asStateFlow()

    private val _published = MutableSharedFlow<Post>(extraBufferCapacity = 8)

    /** 投稿できたポスト (フル画面を閉じる・スレッドに足す等)。この時点で [current] は空の下書きに替わっている。 */
    val published: SharedFlow<Post> = _published.asSharedFlow()

    /** [session] の下書きを返す。別のアカウントの下書きがあれば捨てて作り直す。 */
    fun open(session: Session): ComposerSession {
        _current.value?.takeIf { owner == session }?.let { return it }
        _current.value?.close()
        owner = session
        return newDraft(session)
    }

    /** 投稿・破棄で今の下書きを終え、同じアカウントの空の下書きに替える。 */
    fun finish() {
        val session = owner ?: return
        _current.value?.close()
        newDraft(session)
    }

    private fun newDraft(session: Session): ComposerSession {
        lateinit var draft: ComposerSession
        draft = create(session, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) { post ->
            // 投稿した下書きがまだ今のものなら終える (アカウントの切り替え等で替わっていれば何もしない)
            if (_current.value === draft) finish()
            _published.tryEmit(post)
        }
        _current.value = draft
        return draft
    }
}
