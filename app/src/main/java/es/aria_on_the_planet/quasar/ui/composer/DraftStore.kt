package es.aria_on_the_planet.quasar.ui.composer

import es.aria_on_the_planet.quasar.data.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 今の書きかけの投稿 ([ComposerSession]) をアプリ全体で 1 つ持つ。クイック投稿バーとフルの投稿画面
 * (`ComposerActivity` を含む) はここから同じものを受け取る。
 *
 * 各 session は自分のコルーチンの範囲を持ち、画面が閉じても添付のアップロードは続く。プロセスが終わると消える
 * (保存はしない)。
 *
 * @param create 新しい session を作る (`AppContainer` がリポジトリを渡す)。
 */
class DraftStore(
    private val create: (Session, CoroutineScope) -> ComposerSession,
) {
    private var owner: Session? = null
    private val _current = MutableStateFlow<ComposerSession?>(null)

    /** 今の下書き。[open] するまでは `null`。 */
    val current: StateFlow<ComposerSession?> = _current.asStateFlow()

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

    private fun newDraft(session: Session): ComposerSession =
        create(session, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)).also { _current.value = it }
}
