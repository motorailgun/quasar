package es.aria_on_the_planet.quasar.ui.reactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Page
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.feed.FeedState
import es.aria_on_the_planet.quasar.ui.feed.PagedFeed
import es.aria_on_the_planet.quasar.ui.feed.appendDistinct
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** ポストのリアクションしたユーザーの一覧のタブ。 */
enum class PostReactionsTab { Boosts, Favourites }

data class PostReactionsState(
    val boosts: FeedState<User> = FeedState(),
    val favourites: FeedState<User> = FeedState(),
) {
    fun feed(tab: PostReactionsTab): FeedState<User> = when (tab) {
        PostReactionsTab.Boosts -> boosts
        PostReactionsTab.Favourites -> favourites
    }
}

/** [statusId] (ブーストなら中身) をブースト / お気に入りにしたユーザー。 */
class PostReactionsViewModel(
    private val session: Session,
    private val statusId: String,
    private val repository: StatusRepository,
) : ViewModel() {

    // max_id はブースト・お気に入りの ID なので、Link ヘッダから得た Page.nextMaxId でページングする
    private val boosts = userFeed { maxId -> repository.fetchRebloggedBy(session, statusId, maxId) }
    private val favourites = userFeed { maxId -> repository.fetchFavouritedBy(session, statusId, maxId) }

    val state: StateFlow<PostReactionsState> = combine(boosts.state, favourites.state, ::PostReactionsState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, PostReactionsState())

    /** タブを開いたときに呼ぶ。読み込み済みなら何もしない。 */
    fun ensureLoaded(tab: PostReactionsTab) = feedOf(tab).ensureLoaded()

    fun refresh(tab: PostReactionsTab) = feedOf(tab).refresh()

    fun loadMore(tab: PostReactionsTab) = feedOf(tab).loadMore()

    private fun feedOf(tab: PostReactionsTab) = when (tab) {
        PostReactionsTab.Boosts -> boosts
        PostReactionsTab.Favourites -> favourites
    }

    private fun userFeed(fetch: suspend (maxId: String?) -> Page<User>) =
        PagedFeed<User, User>(
            scope = viewModelScope,
            append = appendDistinct(idOf = { it.inServerId }),
            fetchPage = fetch,
        )
}
