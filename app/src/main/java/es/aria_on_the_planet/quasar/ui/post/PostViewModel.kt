package es.aria_on_the_planet.quasar.ui.post

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.PostThread
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.withPoll
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.ui.feed.PostActionController
import es.aria_on_the_planet.quasar.ui.feed.submitVote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostScreenState(
    val thread: PostThread? = null,
    /** リプライを一列に並べた行。 */
    val replies: List<ReplyRow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class PostViewModel(
    private val session: Session,
    private val statusId: String,
    private val repository: StatusRepository,
) : ViewModel() {

    /** 投票・ブースト・お気に入り・ブックマークができるか (要ログイン)。 */
    val canInteract: Boolean = session.accessToken != null

    private val _state = MutableStateFlow(PostScreenState())
    val state: StateFlow<PostScreenState> = _state.asStateFlow()

    init {
        refresh()
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

    fun refresh() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val thread = repository.fetchThread(session, statusId)
                _state.value = PostScreenState(thread = thread, replies = linearReplies(thread.descendants))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    private companion object {
        const val TAG = "PostViewModel"
    }
}
