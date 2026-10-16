package es.aria_on_the_planet.quasar.ui.follows

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Page
import es.aria_on_the_planet.quasar.domain.model.Profile
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
import kotlinx.coroutines.launch

/** フォロー / フォロワー一覧のタブ。 */
enum class FollowListTab { Following, Followers }

data class FollowListState(
    /** トップバーのタイトル用。取得前 / 失敗時は `null`。 */
    val profile: Profile? = null,
    val following: FeedState<User> = FeedState(),
    val followers: FeedState<User> = FeedState(),
) {
    fun feed(tab: FollowListTab): FeedState<User> = when (tab) {
        FollowListTab.Following -> following
        FollowListTab.Followers -> followers
    }
}

class FollowListViewModel(
    private val session: Session,
    private val accountId: String,
    private val repository: AccountRepository,
) : ViewModel() {

    private val profile = MutableStateFlow<Profile?>(null)

    // max_id はフォロー関係の ID なので、Link ヘッダから得た Page.nextMaxId でページングする
    private val following = userFeed { maxId -> repository.fetchFollowing(session, accountId, maxId) }
    private val followers = userFeed { maxId -> repository.fetchFollowers(session, accountId, maxId) }

    val state: StateFlow<FollowListState> = combine(profile, following.state, followers.state, ::FollowListState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FollowListState())

    init {
        viewModelScope.launch {
            try {
                profile.value = repository.fetchProfile(session, accountId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load profile", e)
            }
        }
    }

    /** タブを開いたときに呼ぶ。読み込み済みなら何もしない。 */
    fun ensureLoaded(tab: FollowListTab) = feedOf(tab).ensureLoaded()

    fun refresh(tab: FollowListTab) = feedOf(tab).refresh()

    fun loadMore(tab: FollowListTab) = feedOf(tab).loadMore()

    private fun feedOf(tab: FollowListTab) = when (tab) {
        FollowListTab.Following -> following
        FollowListTab.Followers -> followers
    }

    private fun userFeed(fetch: suspend (maxId: String?) -> Page<User>) =
        PagedFeed<User, User>(
            scope = viewModelScope,
            append = appendDistinct(idOf = { it.inServerId }),
            fetchPage = fetch,
        )

    private companion object {
        const val TAG = "FollowListViewModel"
    }
}
