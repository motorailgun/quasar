package es.aria_on_the_planet.quasar.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.withPoll
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.ui.feed.PostActionController
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.feed.PostFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import es.aria_on_the_planet.quasar.ui.feed.submitVote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val profile: Profile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** プロフィールの補助情報。取得に失敗しても画面全体はエラーにせず、空として扱う。 */
data class ProfileExtras(
    val pinnedPosts: List<Post> = emptyList(),
    /** 自分がフォローしている人のうち、このアカウントをフォローしている人。 */
    val followersYouFollow: List<User> = emptyList(),
)

data class ProfileScreenState(
    val profile: ProfileState = ProfileState(),
    val extras: ProfileExtras = ProfileExtras(),
    val posts: PostFeedState = PostFeedState(),
)

class ProfileViewModel(
    private val session: Session,
    private val accountId: String,
    private val repository: AccountRepository,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    /** 投票・ブースト・お気に入り・ブックマークができるか (要ログイン)。 */
    val canInteract: Boolean = session.accessToken != null

    private val profile = MutableStateFlow(ProfileState())
    private val extras = MutableStateFlow(ProfileExtras())

    private val posts = PostFeed(viewModelScope) { maxId ->
        repository.fetchPosts(
            session = session,
            accountId = accountId,
            maxId = maxId,
            limit = if (maxId == null) InitialPostCount else PageSize,
        )
    }

    val state: StateFlow<ProfileScreenState> = combine(profile, extras, posts.state, ::ProfileScreenState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileScreenState())

    init {
        refresh()
    }

    fun refresh() {
        loadProfile()
        loadExtras()
        posts.refresh()
    }

    fun loadMore() = posts.loadMore()

    suspend fun vote(post: Post, choices: List<Int>): Boolean =
        viewModelScope.submitVote(statusRepository, session, post, choices) { poll ->
            posts.updatePosts { it.withPoll(poll) }
            extras.update { e -> e.copy(pinnedPosts = e.pinnedPosts.map { it.withPoll(poll) }) }
        }

    private val postActions = PostActionController(
        scope = viewModelScope,
        send = { statusId, action -> statusRepository.perform(session, statusId, action) },
        update = { transform ->
            posts.updatePosts(transform)
            extras.update { e -> e.copy(pinnedPosts = e.pinnedPosts.map(transform)) }
        },
        onError = { message, error -> Log.w(TAG, message, error) },
    )

    /** ブースト・お気に入り・ブックマーク (またはその取り消し) を行う。 */
    fun performAction(post: Post, action: PostAction) = postActions.perform(post, action)

    private fun loadProfile() {
        if (profile.value.isLoading) return
        profile.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val loaded = repository.fetchProfile(session, accountId)
                profile.update { it.copy(profile = loaded, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                profile.update { it.copy(isLoading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    private fun loadExtras() {
        viewModelScope.launch {
            val pinned = runOptional("pinned posts") { repository.fetchPinnedPosts(session, accountId) }
            pinned?.let { posts -> extras.update { it.copy(pinnedPosts = posts) } }
        }
        viewModelScope.launch {
            val followers = runOptional("followers you follow") { repository.fetchFamiliarFollowers(session, accountId) }
            followers?.let { users -> extras.update { it.copy(followersYouFollow = users) } }
        }
    }

    private suspend fun <T> runOptional(label: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load $label", e)
        null
    }

    private companion object {
        const val TAG = "ProfileViewModel"

        const val InitialPostCount = 15
        const val PageSize = 40
    }
}
