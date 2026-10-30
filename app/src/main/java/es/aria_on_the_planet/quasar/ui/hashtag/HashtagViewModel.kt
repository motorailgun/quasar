package es.aria_on_the_planet.quasar.ui.hashtag

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.TimelineRepository
import es.aria_on_the_planet.quasar.data.repository.withPoll
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.ui.feed.PostActionController
import es.aria_on_the_planet.quasar.ui.feed.PostFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import es.aria_on_the_planet.quasar.ui.feed.submitVote
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** ハッシュタグ [hashtag] (`#` 無し) のタイムライン。ストリーミングは受けず、PTR と追加読み込みのみ。 */
class HashtagViewModel(
    private val session: Session,
    val hashtag: String,
    repository: TimelineRepository,
    private val statusRepository: StatusRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val posts = PostFeed(viewModelScope) { maxId -> repository.fetchHashtag(session, hashtag, maxId) }

    val state: StateFlow<PostFeedState> = posts.state

    init {
        refresh()
        // ブロック・ミュートしたら、その相手のポストを一覧から除く
        viewModelScope.launch {
            accountRepository.relationshipChanges.collect(posts::hidePostsBy)
        }
        // 自分の編集・削除を当てる
        viewModelScope.launch {
            statusRepository.postChanges.collect(posts::applyChange)
        }
    }

    fun refresh() = posts.refresh()

    fun loadMore() = posts.loadMore()

    suspend fun vote(post: Post, choices: List<Int>): Boolean =
        viewModelScope.submitVote(statusRepository, session, post, choices) { poll ->
            posts.updatePosts { it.withPoll(poll) }
        }

    private val postActions = PostActionController(
        scope = viewModelScope,
        send = { statusId, action -> statusRepository.perform(session, statusId, action) },
        update = posts::updatePosts,
        onError = { message, error -> Log.w(TAG, message, error) },
    )

    /** ブースト・お気に入り・ブックマーク (またはその取り消し) を行う。 */
    fun performAction(post: Post, action: PostAction) = postActions.perform(post, action)

    private companion object {
        const val TAG = "HashtagViewModel"
    }
}
