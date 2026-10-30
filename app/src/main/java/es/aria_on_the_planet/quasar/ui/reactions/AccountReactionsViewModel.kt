package es.aria_on_the_planet.quasar.ui.reactions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.withPoll
import es.aria_on_the_planet.quasar.ui.feed.withPostChange
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.NotificationType
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.feed.PostActionController
import es.aria_on_the_planet.quasar.ui.feed.submitVote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountReactionsState(
    /** リアクションしたユーザー (プロフィール文込み)。取得前・失敗時は `null`。 */
    val account: User? = null,
    /** リアクションされたポスト。通知と同じ新しい順。 */
    val posts: List<Post> = emptyList(),
    val loaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

/**
 * 通知のユーザー単位のまとめ (1 人が複数のポストにした同じ種類のリアクション) の中身。
 * ポストは [statusIds] で引く (通知の一覧から受け取らないのは、プロセスの再生成でも開き直せるように)。通知を取ったときに
 * キャッシュに入っているので、普段は期限切れの分だけを取り直す。
 */
class AccountReactionsViewModel(
    private val session: Session,
    private val accountId: String,
    val type: NotificationType,
    private val statusIds: List<String>,
    private val accountRepository: AccountRepository,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    /** まとめられたポストの数 (読み込み前のタイトル用)。 */
    val postCount: Int = statusIds.size


    private val _state = MutableStateFlow(AccountReactionsState())
    val state: StateFlow<AccountReactionsState> = _state.asStateFlow()

    private var loading: Job? = null

    init {
        load(force = false)
        // 自分の編集・削除を当てる
        viewModelScope.launch {
            statusRepository.postChanges.collect { change -> _state.update { it.copy(posts = it.posts.withPostChange(change)) } }
        }
    }

    /** 引っ張って更新: キャッシュの期限に関わらず全部取り直す。 */
    fun refresh() = load(force = true)

    /**
     * ユーザーとポストを読む。キャッシュ (通知・タイムライン等で取ったもの) にあれば先に出し、期限切れ・無いものだけを
     * 取る ([force] なら全部)。取り直したのに返らなかったポスト (消された等) は、キャッシュにあっても出さない。
     */
    private fun load(force: Boolean) {
        if (loading?.isActive == true) return
        _state.update { it.copy(isRefreshing = force, error = null) }
        loading = viewModelScope.launch {
            // ユーザーは補助情報なので、取れなくてもポストは出す
            val account = async { loadAccount(force) }
            try {
                val cached = if (force) emptyMap() else statusRepository.cachedPosts(statusIds)
                if (cached.isNotEmpty()) {
                    _state.update { it.copy(posts = statusIds.mapNotNull { id -> cached[id]?.value }) }
                }
                val stale = statusIds.filter { cached[it]?.isFresh != true }
                val fetched = if (stale.isEmpty()) emptyMap() else statusRepository.fetchPosts(session, stale).associateBy { it.statusId }
                val posts = statusIds.mapNotNull { id -> if (id in stale) fetched[id] else cached[id]?.value }
                _state.update { it.copy(posts = posts, account = account.await() ?: it.account) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load posts", e)
                _state.update { it.copy(account = account.await() ?: it.account, error = e.message ?: e.javaClass.simpleName) }
            } finally {
                _state.update { it.copy(loaded = true, isRefreshing = false) }
            }
        }
    }

    /** ユーザー (キャッシュにあれば先に出し、期限内ならそれを使う)。取れなければ `null`。 */
    private suspend fun loadAccount(force: Boolean): User? = try {
        val cached = if (force) null else accountRepository.cachedProfile(accountId)
        cached?.let { hit -> _state.update { it.copy(account = hit.value.user) } }
        if (cached?.isFresh == true) cached.value.user else accountRepository.fetchProfile(session, accountId).user
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load account", e)
        null
    }

    suspend fun vote(post: Post, choices: List<Int>): Boolean =
        viewModelScope.submitVote(statusRepository, session, post, choices) { poll ->
            updatePosts { it.withPoll(poll) }
        }

    private val postActions = PostActionController(
        scope = viewModelScope,
        send = { statusId, action -> statusRepository.perform(session, statusId, action) },
        update = ::updatePosts,
        onError = { message, error -> Log.w(TAG, message, error) },
    )

    /** ブースト・お気に入り・ブックマーク (またはその取り消し) を行う。 */
    fun performAction(post: Post, action: PostAction) = postActions.perform(post, action)

    private fun updatePosts(transform: (Post) -> Post) {
        _state.update { it.copy(posts = it.posts.map(transform)) }
    }

    private companion object {
        const val TAG = "AccountReactionsVM"
    }
}
