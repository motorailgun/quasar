package es.aria_on_the_planet.quasar.ui.personal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.feed.FeedState
import es.aria_on_the_planet.quasar.ui.feed.PagedFeed
import es.aria_on_the_planet.quasar.ui.feed.appendDistinct
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FollowRequestsState(
    val requests: FeedState<User> = FeedState(),
    /** 送信中の返事 (アカウントの ID → 承認なら `true`)。 */
    val answering: Map<String, Boolean> = emptyMap(),
)

/** 自分へのフォローリクエストの一覧。返事をしたら一覧から除く。 */
class FollowRequestsViewModel(
    private val session: Session,
    private val repository: AccountRepository,
) : ViewModel() {

    // max_id はリクエストの ID なので、Link ヘッダから得た Page.nextMaxId でページングする
    private val requests = PagedFeed<User, User>(
        scope = viewModelScope,
        append = appendDistinct(idOf = { it.inServerId }),
        fetchPage = { maxId -> repository.fetchFollowRequests(session, maxId) },
    )

    private val answering = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val state: StateFlow<FollowRequestsState> = combine(requests.state, answering, ::FollowRequestsState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FollowRequestsState())

    init {
        refresh()
    }

    fun refresh() = requests.refresh()

    fun loadMore() = requests.loadMore()

    /** [user] からのリクエストを承認する (`accept = false` なら拒否)。失敗はログのみで、ボタンが押せる状態に戻る。 */
    fun answer(user: User, accept: Boolean) {
        val id = user.inServerId
        if (id in answering.value) return
        answering.update { it + (id to accept) }
        viewModelScope.launch {
            try {
                repository.answerFollowRequest(session, id, accept)
                requests.updateList { list -> list.filterNot { it.inServerId == id } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to ${if (accept) "accept" else "reject"} follow request", e)
            } finally {
                answering.update { it - id }
            }
        }
    }

    private companion object {
        const val TAG = "FollowRequestsViewModel"
    }
}
