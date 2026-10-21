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
import es.aria_on_the_planet.quasar.domain.model.Relationship
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
    /** 取得中 (キャッシュを出している間の裏の取得を含む)。 */
    val isLoading: Boolean = false,
    /** 引っ張って更新した取得の最中 (インジケータを出す)。 */
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

/** プロフィールの補助情報。取得に失敗しても画面全体はエラーにせず、空として扱う。 */
data class ProfileExtras(
    val pinnedPosts: List<Post> = emptyList(),
    /** 自分がフォローしている人のうち、このアカウントをフォローしている人。 */
    val followersYouFollow: List<User> = emptyList(),
)

/** プロフィールの投稿一覧のタブ。 */
enum class ProfileTab { Posts, Media }

/** ヘッダ右側のボタン。 */
sealed interface ProfileButtonState {
    /** 出さない (未ログイン・関係の取得前 / 失敗)。 */
    data object None : ProfileButtonState

    /** 自分のプロフィール。編集画面は未実装なので押しても何もしない。 */
    data object EditProfile : ProfileButtonState

    /**
     * 他人のプロフィール。[isUpdating] はフォロー / 解除の送信中、[answering] は相手からのフォローリクエストへの
     * 返事の送信中 (`true` = 承認、`false` = 拒否、`null` = 送信していない)。
     */
    data class Follow(
        val relationship: Relationship,
        val isUpdating: Boolean = false,
        val answering: Boolean? = null,
    ) : ProfileButtonState
}

data class ProfileScreenState(
    val profile: ProfileState = ProfileState(),
    val extras: ProfileExtras = ProfileExtras(),
    val posts: PostFeedState = PostFeedState(),
    val media: PostFeedState = PostFeedState(),
    val tab: ProfileTab = ProfileTab.Posts,
    val button: ProfileButtonState = ProfileButtonState.None,
) {
    /** 選択中のタブの一覧。 */
    val selectedFeed: PostFeedState get() = if (tab == ProfileTab.Media) media else posts
}

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
    private val tab = MutableStateFlow(ProfileTab.Posts)
    private val button = MutableStateFlow<ProfileButtonState>(ProfileButtonState.None)

    private val posts = PostFeed(viewModelScope) { maxId ->
        repository.fetchPosts(
            session = session,
            accountId = accountId,
            maxId = maxId,
            limit = if (maxId == null) InitialPostCount else PageSize,
        )
    }

    /** メディア付きのポストのみ (メディアのタブ)。初めて開いたときに読み込む。 */
    private val media = PostFeed(viewModelScope) { maxId ->
        repository.fetchPosts(session = session, accountId = accountId, maxId = maxId, limit = PageSize, onlyMedia = true)
    }

    val state: StateFlow<ProfileScreenState> = combine(
        combine(profile, extras, ::Pair),
        posts.state,
        media.state,
        tab,
        button,
    ) { (profile, extras), posts, media, tab, button -> ProfileScreenState(profile, extras, posts, media, tab, button) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileScreenState())

    init {
        load(force = false)
    }

    /** 引っ張って更新: プロフィール・関係もキャッシュの期限に関わらず取り直す。 */
    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        loadProfile(force)
        loadExtras()
        loadRelationship(force)
        posts.refresh()
        // 開いたことのないタブは読まない
        if (media.state.value.loaded) media.refresh()
    }

    /** [tab] の一覧の続きを読む (ページャの各ページから呼ぶ)。 */
    fun loadMore(tab: ProfileTab) = if (tab == ProfileTab.Media) media.loadMore() else posts.loadMore()

    /** 表示中のタブ (ページャのページ)。初めて開いたタブは読み込む。 */
    fun selectTab(selected: ProfileTab) {
        tab.value = selected
        if (selected == ProfileTab.Media) media.ensureLoaded()
    }

    /** フォローする (`follow = false` ならフォロー / リクエストをやめる)。確認は画面側で済ませてから呼ぶ。 */
    fun setFollowing(follow: Boolean) {
        val current = button.value as? ProfileButtonState.Follow ?: return
        if (current.isUpdating) return
        button.value = current.copy(isUpdating = true)
        viewModelScope.launch {
            try {
                val updated = repository.setFollowing(session, accountId, follow)
                // フォローリクエストへの返事を送信中でも、その状態は残す
                button.update { (it as? ProfileButtonState.Follow)?.copy(relationship = updated, isUpdating = false) ?: it }
                // フォロワー数はプロフィールを読み直さずに手元で合わせる (リクエスト中は数えない)
                val delta = updated.following.toInt() - current.relationship.following.toInt()
                if (delta != 0) {
                    profile.update { s ->
                        s.copy(profile = s.profile?.let { it.copy(followersCount = (it.followersCount + delta).coerceAtLeast(0)) })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to ${if (follow) "follow" else "unfollow"}", e)
                button.update { (it as? ProfileButtonState.Follow)?.copy(isUpdating = false) ?: it }
            }
        }
    }

    /** 相手から自分へのフォローリクエストを承認する (`accept = false` なら拒否)。確認は画面側で済ませてから呼ぶ。 */
    fun answerFollowRequest(accept: Boolean) {
        val current = button.value as? ProfileButtonState.Follow ?: return
        if (current.answering != null || !current.relationship.requestedBy) return
        button.value = current.copy(answering = accept)
        viewModelScope.launch {
            try {
                val updated = repository.answerFollowRequest(session, accountId, accept)
                button.update { (it as? ProfileButtonState.Follow)?.copy(relationship = updated, answering = null) ?: it }
                // 承認したら相手のフォロー数が 1 増える (プロフィールを読み直さずに手元で合わせる)
                if (accept && updated.followedBy && !current.relationship.followedBy) {
                    profile.update { s -> s.copy(profile = s.profile?.let { it.copy(followingCount = it.followingCount + 1) }) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to ${if (accept) "accept" else "reject"} follow request", e)
                button.update { (it as? ProfileButtonState.Follow)?.copy(answering = null) ?: it }
            }
        }
    }

    suspend fun vote(post: Post, choices: List<Int>): Boolean =
        viewModelScope.submitVote(statusRepository, session, post, choices) { poll ->
            posts.updatePosts { it.withPoll(poll) }
            media.updatePosts { it.withPoll(poll) }
            extras.update { e -> e.copy(pinnedPosts = e.pinnedPosts.map { it.withPoll(poll) }) }
        }

    private val postActions = PostActionController(
        scope = viewModelScope,
        send = { statusId, action -> statusRepository.perform(session, statusId, action) },
        update = { transform ->
            posts.updatePosts(transform)
            media.updatePosts(transform)
            extras.update { e -> e.copy(pinnedPosts = e.pinnedPosts.map(transform)) }
        },
        onError = { message, error -> Log.w(TAG, message, error) },
    )

    /** ブースト・お気に入り・ブックマーク (またはその取り消し) を行う。 */
    fun performAction(post: Post, action: PostAction) = postActions.perform(post, action)

    /**
     * プロフィールを読む。初めて開いたときはキャッシュにあるもの (ポストの投稿者・通知等で取ったものを含む) を先に出し、
     * 期限内なら取りに行かない ([force] なら取り直す)。
     */
    private fun loadProfile(force: Boolean) {
        if (profile.value.isLoading) return
        profile.update { it.copy(isLoading = true, isRefreshing = force, error = null) }
        viewModelScope.launch {
            try {
                val cached = if (profile.value.profile == null) repository.cachedProfile(accountId) else null
                if (cached != null) profile.update { it.copy(profile = cached.value) }
                if (cached?.isFresh == true && !force) {
                    profile.update { it.copy(isLoading = false, isRefreshing = false) }
                    return@launch
                }
                val loaded = repository.fetchProfile(session, accountId)
                profile.update { it.copy(profile = loaded, isLoading = false, isRefreshing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                profile.update { it.copy(isLoading = false, isRefreshing = false, error = e.message ?: e.toString()) }
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

    /**
     * 自分のプロフィールか、相手との関係を読む。未ログインなら何もしない (ボタンを出さない)。
     * 関係はキャッシュにあれば先に出し、期限内なら取りに行かない ([force] なら取り直す)。
     */
    private fun loadRelationship(force: Boolean) {
        if (!canInteract) return
        viewModelScope.launch {
            val me = runOptional("current account") { repository.fetchMe(session) } ?: return@launch
            if (me.user.inServerId == accountId) {
                button.value = ProfileButtonState.EditProfile
                return@launch
            }
            if (!force) {
                val cached = runOptional("cached relationship") { repository.cachedRelationship(accountId) }
                cached?.let { showRelationship(it.value) }
                if (cached?.isFresh == true) return@launch
            }
            val relationship = runOptional("relationship") { repository.fetchRelationship(session, accountId) }
                ?: return@launch
            showRelationship(relationship)
        }
    }

    /** 関係を出す。フォロー・フォローリクエストへの返事の送信中なら、その応答で上書きされるので触らない。 */
    private fun showRelationship(relationship: Relationship) {
        button.update {
            if (it is ProfileButtonState.Follow && (it.isUpdating || it.answering != null)) it else ProfileButtonState.Follow(relationship)
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

private fun Boolean.toInt() = if (this) 1 else 0
