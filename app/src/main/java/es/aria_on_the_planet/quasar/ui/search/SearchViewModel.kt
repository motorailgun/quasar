package es.aria_on_the_planet.quasar.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.SearchRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.withPoll
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Hashtag
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.feed.FeedState
import es.aria_on_the_planet.quasar.ui.feed.PagedFeed
import es.aria_on_the_planet.quasar.ui.feed.PostActionController
import es.aria_on_the_planet.quasar.ui.feed.PostFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import es.aria_on_the_planet.quasar.ui.feed.appendDistinct
import es.aria_on_the_planet.quasar.ui.feed.submitVote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** 検索結果のタブ。 */
enum class SearchTab { Accounts, Posts, Hashtags }

data class SearchState(
    /** 検索した語。まだ検索していなければ `null`。 */
    val query: String? = null,
    val accounts: FeedState<User> = FeedState(),
    val posts: PostFeedState = PostFeedState(),
    val hashtags: FeedState<Hashtag> = FeedState(),
)

/**
 * 検索 (`/api/v2/search`)。種類ごとのタブで、それぞれ開いたときに初めて読み、`offset` で続きを読む。
 * 検索し直すたびに 3 つの一覧を作り直す (前の検索の読み込みは取りやめる)。
 */
class SearchViewModel(
    private val session: Session,
    private val repository: SearchRepository,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    private inner class Results(val query: String) {
        val job = SupervisorJob(viewModelScope.coroutineContext[Job])
        private val scope: CoroutineScope = viewModelScope + job

        // 「max_id」には次の offset が入る (SearchRepository)
        val accounts = PagedFeed<User, User>(
            scope = scope,
            append = appendDistinct(idOf = { it.inServerId }),
            fetchPage = { offset -> repository.searchAccounts(session, query, offset) },
        )
        val posts = PostFeed.linked(scope) { offset -> repository.searchPosts(session, query, offset) }
        val hashtags = PagedFeed<Hashtag, Hashtag>(
            scope = scope,
            append = appendDistinct(idOf = { it.name.lowercase() }),
            fetchPage = { offset -> repository.searchHashtags(session, query, offset) },
        )

        fun feedOf(tab: SearchTab): PagedFeed<*, *> = when (tab) {
            SearchTab.Accounts -> accounts
            SearchTab.Posts -> posts
            SearchTab.Hashtags -> hashtags
        }
    }

    private val results = MutableStateFlow<Results?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SearchState> = results
        .flatMapLatest { r ->
            if (r == null) {
                flowOf(SearchState())
            } else {
                combine(r.accounts.state, r.posts.state, r.hashtags.state) { a, p, h -> SearchState(r.query, a, p, h) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchState())

    init {
        // 自分の編集・削除を当てる
        viewModelScope.launch {
            statusRepository.postChanges.collect { change -> results.value?.posts?.applyChange(change) }
        }
    }

    /** [query] で検索し直す。空白だけなら何もしない。読むのはタブを開いたとき ([ensureLoaded])。 */
    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        results.value?.job?.cancel()
        results.value = Results(trimmed)
    }

    fun ensureLoaded(tab: SearchTab) = results.value?.feedOf(tab)?.ensureLoaded()

    fun refresh(tab: SearchTab) = results.value?.feedOf(tab)?.refresh()

    fun loadMore(tab: SearchTab) = results.value?.feedOf(tab)?.loadMore()

    suspend fun vote(post: Post, choices: List<Int>): Boolean =
        viewModelScope.submitVote(statusRepository, session, post, choices) { poll ->
            results.value?.posts?.updatePosts { it.withPoll(poll) }
        }

    private val postActions = PostActionController(
        scope = viewModelScope,
        send = { statusId, action -> statusRepository.perform(session, statusId, action) },
        update = { transform -> results.value?.posts?.updatePosts(transform) },
        onError = { message, error -> Log.w(TAG, message, error) },
    )

    /** ブースト・お気に入り・ブックマーク (またはその取り消し) を行う。 */
    fun performAction(post: Post, action: PostAction) = postActions.perform(post, action)

    private companion object {
        const val TAG = "SearchViewModel"
    }
}
