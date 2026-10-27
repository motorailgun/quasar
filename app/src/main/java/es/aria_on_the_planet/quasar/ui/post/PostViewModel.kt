package es.aria_on_the_planet.quasar.ui.post

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.remote.MastodonApiException
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.repository.PostThread
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.withPoll
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.ui.composer.ComposerSession
import es.aria_on_the_planet.quasar.ui.composer.EditTarget
import es.aria_on_the_planet.quasar.ui.feed.PostActionController
import es.aria_on_the_planet.quasar.ui.feed.submitVote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostScreenState(
    val thread: PostThread? = null,
    /** リプライを一列に並べた行。 */
    val replies: List<ReplyRow> = emptyList(),
    /** 取得中 (キャッシュを出している間の裏の取得を含む)。 */
    val isLoading: Boolean = false,
    /** 引っ張って更新した取得の最中 (インジケータを出す)。 */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    /** ログイン中のアカウントの ID。取得前・失敗時は `null`。 */
    val myAccountId: String? = null,
    /** 自分のポストへの操作 (編集の準備・削除) の最中。 */
    val ownPostBusy: Boolean = false,
) {
    /** 本体が自分のポストか (編集・削除のメニューを出す)。 */
    val isMine: Boolean
        get() = myAccountId != null && thread?.post?.author?.inServerId == myAccountId
}

/** 画面に一度だけ伝える出来事。 */
sealed interface PostScreenEvent {
    /** 本体を削除した (画面を閉じる)。 */
    data object Deleted : PostScreenEvent
    data class EditLoadFailed(val message: String) : PostScreenEvent
    data class DeleteFailed(val message: String) : PostScreenEvent
}

class PostViewModel(
    private val session: Session,
    private val statusId: String,
    private val repository: StatusRepository,
    private val accountRepository: AccountRepository,
    /** 編集用の投稿欄を作る (`AppContainer.composerSession`)。3 つ目の引数は保存できたときに呼ぶ。 */
    private val createEditor: (CoroutineScope, EditTarget, (Post) -> Unit) -> ComposerSession,
) : ViewModel() {

    private val _state = MutableStateFlow(PostScreenState())
    val state: StateFlow<PostScreenState> = _state.asStateFlow()

    private val _editor = MutableStateFlow<ComposerSession?>(null)

    /** 本体の編集中の投稿欄 (フルの投稿画面を重ねて出す)。編集していなければ `null`。 */
    val editor: StateFlow<ComposerSession?> = _editor.asStateFlow()

    private val _events = Channel<PostScreenEvent>(Channel.BUFFERED)
    val events: Flow<PostScreenEvent> = _events.receiveAsFlow()

    init {
        load(force = false)
        viewModelScope.launch {
            val me = try {
                accountRepository.fetchMe(session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 取れなければ自分のポストのメニューを出さないだけ
                Log.w(TAG, "Failed to load current account", e)
                null
            }
            _state.update { it.copy(myAccountId = me?.user?.inServerId) }
        }
    }

    /** 本体 (自分のポスト) の編集を始める。原文 (HTML ではない本文) を取ってから投稿欄を開く。 */
    fun startEdit() {
        val post = _state.value.thread?.post ?: return
        if (_state.value.ownPostBusy || _editor.value != null) return
        _state.update { it.copy(ownPostBusy = true) }
        viewModelScope.launch {
            try {
                val source = repository.fetchSource(session, post.statusId)
                val scope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext.job))
                _editor.value = createEditor(scope, EditTarget(post, source)) { edited ->
                    // ブーストの外側 (ID・ブーストした人) は保つ
                    updatePosts { if (it.statusId == edited.statusId) edited.copy(id = it.id, boostedBy = it.boostedBy) else it }
                    closeEditor()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load the post source", e)
                _events.trySend(PostScreenEvent.EditLoadFailed(e.message ?: e.toString()))
            } finally {
                _state.update { it.copy(ownPostBusy = false) }
            }
        }
    }

    /** 編集をやめる (保存した後も)。 */
    fun closeEditor() {
        _editor.value?.close()
        _editor.value = null
    }

    /** 本体 (自分のポスト) を削除する。確認は画面側で済ませてから呼ぶ。 */
    fun delete() {
        val post = _state.value.thread?.post ?: return
        if (_state.value.ownPostBusy) return
        _state.update { it.copy(ownPostBusy = true) }
        viewModelScope.launch {
            try {
                repository.delete(session, post.statusId)
                _events.trySend(PostScreenEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete the post", e)
                _events.trySend(PostScreenEvent.DeleteFailed(e.message ?: e.toString()))
            } finally {
                _state.update { it.copy(ownPostBusy = false) }
            }
        }
    }

    suspend fun vote(post: Post, choices: List<Int>): Boolean =
        viewModelScope.submitVote(repository, session, post, choices) { poll ->
            updatePosts { it.withPoll(poll) }
        }

    private val postActions = PostActionController(
        scope = viewModelScope,
        send = { id, action -> repository.perform(session, id, action) },
        update = ::updatePosts,
        onError = { message, error -> Log.w(TAG, message, error) },
    )

    /** ブースト・お気に入り・ブックマーク (またはその取り消し) を行う。 */
    fun performAction(post: Post, action: PostAction) = postActions.perform(post, action)

    /** スレッド中の全ポスト (祖先・本体・リプライ) を書き換える。 */
    private fun updatePosts(transform: (Post) -> Post) {
        _state.update { s ->
            val thread = s.thread ?: return@update s
            val updated = thread.copy(
                ancestors = thread.ancestors.map(transform),
                post = transform(thread.post),
                descendants = thread.descendants.map(transform),
            )
            s.copy(thread = updated, replies = linearReplies(updated.descendants))
        }
    }

    /**
     * 投稿したリプライをスレッドに足し、返信先のリプライ数を 1 増やす。このスレッドへの返信でなければ何もしない。
     * (ポスト画面はストリーミングを受けないので、自分の投稿は手元で足す。)
     */
    fun addPublishedReply(reply: Post) {
        _state.update { s ->
            val thread = s.thread ?: return@update s
            val descendants = insertReply(thread.descendants, thread.post.statusId, reply) ?: return@update s
            if (descendants === thread.descendants) return@update s
            val bump = { post: Post ->
                if (post.statusId == reply.inReplyToId) post.copy(repliesCount = post.repliesCount + 1) else post
            }
            val updated = thread.copy(post = bump(thread.post), descendants = descendants.map(bump))
            s.copy(thread = updated, replies = linearReplies(updated.descendants))
        }
    }

    /** 引っ張って更新: 本体もスレッドも取り直す。 */
    fun refresh() = load(force = true)

    /**
     * スレッドを読む。初めて開いたときはキャッシュにあるもの (タイムライン等で取った本体・前に開いたときのスレッド) を
     * 先に出し、本体が期限内なら本体は取らずにスレッド (`/context`) だけを取る ([force] なら両方)。
     */
    private fun load(force: Boolean) {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, isRefreshing = force, error = null) }
        viewModelScope.launch {
            try {
                val cached = if (_state.value.thread == null) repository.cachedThread(statusId) else null
                if (cached != null) {
                    _state.update { it.copy(thread = cached.thread, replies = linearReplies(cached.thread.descendants)) }
                }
                val reuse = cached?.takeIf { it.isPostFresh && !force }?.thread?.post
                val fetched = repository.fetchThread(session, statusId, post = reuse)
                _state.update { s ->
                    // 本体を取らなかったときは、取得中に行った操作 (お気に入り等) の反映を残す
                    val thread = if (reuse != null) fetched.copy(post = s.thread?.post ?: reuse) else fetched
                    s.copy(thread = thread, replies = linearReplies(thread.descendants), isLoading = false, isRefreshing = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 消された・見られなくなったなら、キャッシュから出していた分も消す
                val gone = e is MastodonApiException && (e.code == 404 || e.code == 410)
                _state.update {
                    it.copy(
                        thread = if (gone) null else it.thread,
                        replies = if (gone) emptyList() else it.replies,
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: e.toString(),
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "PostViewModel"
    }
}
