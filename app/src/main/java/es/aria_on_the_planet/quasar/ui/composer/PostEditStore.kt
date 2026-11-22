package es.aria_on_the_planet.quasar.ui.composer

import android.util.Log
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.post.OwnPostActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 自分のポストの編集・削除の失敗 (Toast で知らせる)。 */
sealed interface PostEditFailure {
    val message: String

    data class EditLoad(override val message: String) : PostEditFailure
    data class Delete(override val message: String) : PostEditFailure
}

/**
 * 自分のポストの編集・削除をアカウントごとに 1 つ持つ (`AccountUiContext.postEditStore`)。どの画面のポストのメニューからでも同じ流れになるよう、
 * 編集の投稿欄 ([editor]) は画面ではなくここが持ち、`QuasarNavHost` が全画面の上に重ねて出す ([PostEditorHost])。
 * 書きかけの投稿 ([DraftStore]) とは別。プロセスが終わると消える。ポストのメニューの口 ([OwnPostActions]) もこれで、
 * `QuasarNavHost` が提供する。
 *
 * 保存・削除の結果は `StatusRepository.postChanges` に流れ、各画面の一覧はそれで差し替える・除く。
 *
 * @param create 編集用の投稿欄を作る (`AccountUiContext.composerSession`)。3 つ目の引数は保存できたときに呼ぶ。
 */
class PostEditStore(
    private val statusRepository: StatusRepository,
    private val create: (CoroutineScope, EditTarget, (Post) -> Unit) -> ComposerSession,
) : OwnPostActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _editor = MutableStateFlow<ComposerSession?>(null)

    /** 編集中の投稿欄。編集していなければ `null`。 */
    val editor: StateFlow<ComposerSession?> = _editor.asStateFlow()

    private val _busyStatusId = MutableStateFlow<String?>(null)

    /** 編集の準備 (原文の取得)・削除の最中のポスト。その間は次の操作を受けない。 */
    override val busyStatusId: StateFlow<String?> = _busyStatusId.asStateFlow()

    private val _failures = MutableSharedFlow<PostEditFailure>(extraBufferCapacity = 8)
    val failures: SharedFlow<PostEditFailure> = _failures.asSharedFlow()

    /** [post] (自分のポスト) の編集を始める。原文 (HTML ではない本文) を取ってから投稿欄を開く。 */
    override fun edit(post: Post) {
        if (_busyStatusId.value != null || _editor.value != null) return
        _busyStatusId.value = post.statusId
        scope.launch {
            try {
                val source = statusRepository.fetchSource(post.statusId)
                lateinit var editor: ComposerSession
                // ブーストとして載っていたものでも、編集するのは中身
                val target = EditTarget(post.copy(id = post.statusId, boostedBy = null), source)
                editor = create(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), target) {
                    if (_editor.value === editor) close()
                }
                _editor.value = editor
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load the post source", e)
                _failures.tryEmit(PostEditFailure.EditLoad(e.message ?: e.toString()))
            } finally {
                _busyStatusId.value = null
            }
        }
    }

    /** 編集をやめる (保存した後も)。 */
    fun close() {
        _editor.value?.close()
        _editor.value = null
    }

    /** 編集・削除の最中のものも含めて止める (ログアウト)。以後は使わない。 */
    fun dispose() {
        scope.cancel()
        close()
    }

    /** [post] (自分のポスト) を削除する。確認は呼び出し側で済ませてから呼ぶ。 */
    override fun delete(post: Post) {
        if (_busyStatusId.value != null) return
        _busyStatusId.value = post.statusId
        scope.launch {
            try {
                statusRepository.delete(post.statusId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete the post", e)
                _failures.tryEmit(PostEditFailure.Delete(e.message ?: e.toString()))
            } finally {
                _busyStatusId.value = null
            }
        }
    }

    private companion object {
        const val TAG = "PostEditStore"
    }
}
