package es.aria_on_the_planet.quasar.ui.profile

import android.util.Log
import android.net.Uri
import es.aria_on_the_planet.quasar.data.media.ImageUploadLimits
import es.aria_on_the_planet.quasar.data.media.UploadSource
import es.aria_on_the_planet.quasar.data.media.profileImageLimits
import es.aria_on_the_planet.quasar.data.remote.userMessage
import es.aria_on_the_planet.quasar.data.repository.EmojiRepository
import es.aria_on_the_planet.quasar.data.repository.InstanceRepository
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.ProfileLimits
import es.aria_on_the_planet.quasar.domain.model.problems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.withPoll
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.ui.feed.PostActionController
import es.aria_on_the_planet.quasar.ui.component.account.AccountAction
import es.aria_on_the_planet.quasar.ui.component.account.AccountActionType
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.Relationship
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.feed.PostFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import es.aria_on_the_planet.quasar.ui.feed.TimelineItem
import es.aria_on_the_planet.quasar.ui.feed.submitVote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
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

/**
 * プロフィールの投稿一覧のタブ。[Favourites] / [Bookmarks] は自分のプロフィールだけ (他人のものを取る API は無い)。
 */
enum class ProfileTab {
    Posts,
    Media,
    Favourites,
    Bookmarks,
    ;

    /** 自分のプロフィールにだけ出すタブ。 */
    val ownOnly: Boolean get() = this == Favourites || this == Bookmarks
}

/** ヘッダ右側のボタン。 */
sealed interface ProfileButtonState {
    /** 出さない (関係の取得前 / 失敗)。 */
    data object None : ProfileButtonState

    /** 自分のプロフィール。押すと編集 ([ProfileEdit])。 */
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

/** プロフィールの編集 (自分のプロフィールだけ)。`null` なら編集していない。 */
sealed interface ProfileEdit {
    /** 編集用の原文を読んでいる。 */
    data object Loading : ProfileEdit

    /**
     * 編集中。[emojis] は表示名・プロフィール文の絵文字の補完用。[isSaving] は保存の送信中、[error] は直前の保存の
     * 失敗 (サーバの文言)。
     */
    data class Editing(
        val editor: ProfileEditor,
        val limits: ProfileLimits,
        val emojis: List<CustomEmoji>,
        val isSaving: Boolean = false,
        val error: String? = null,
    ) : ProfileEdit
}

data class ProfileScreenState(
    val profile: ProfileState = ProfileState(),
    val extras: ProfileExtras = ProfileExtras(),
    val posts: PostFeedState = PostFeedState(),
    val media: PostFeedState = PostFeedState(),
    val favourites: PostFeedState = PostFeedState(),
    val bookmarks: PostFeedState = PostFeedState(),
    val tab: ProfileTab = ProfileTab.Posts,
    val button: ProfileButtonState = ProfileButtonState.None,
    val edit: ProfileEdit? = null,
) {
    /** 自分のプロフィールか。 */
    val isMe: Boolean get() = button == ProfileButtonState.EditProfile

    /** 出すタブ。お気に入り・ブックマークは自分のプロフィールだけ。 */
    val tabs: List<ProfileTab> get() = if (isMe) ProfileTab.entries else ProfileTab.entries.filterNot { it.ownOnly }

    /** [tab] の一覧。 */
    fun feedOf(tab: ProfileTab): PostFeedState = when (tab) {
        ProfileTab.Posts -> posts
        ProfileTab.Media -> media
        ProfileTab.Favourites -> favourites
        ProfileTab.Bookmarks -> bookmarks
    }

    /** 選択中のタブの一覧。 */
    val selectedFeed: PostFeedState get() = feedOf(tab)
}

class ProfileViewModel(
    private val session: Session,
    private val accountId: String,
    private val repository: AccountRepository,
    private val statusRepository: StatusRepository,
    private val instanceRepository: InstanceRepository,
    private val emojiRepository: EmojiRepository,
    /** 端末のファイルをアップロード元にする (`AppContainer.uploadSource`)。IO スレッドで呼ぶ。 */
    private val openUploadSource: (Uri) -> UploadSource,
    /** 上限に合わない画像を作り直す (`ImageReencoder.prepare`)。プロフィールの画像は確かめずに常に通す。 */
    private val prepareImage: suspend (UploadSource, ImageUploadLimits) -> UploadSource = { source, _ -> source },
) : ViewModel() {

    private val profile = MutableStateFlow(ProfileState())
    private val extras = MutableStateFlow(ProfileExtras())
    private val tab = MutableStateFlow(ProfileTab.Posts)
    private val button = MutableStateFlow<ProfileButtonState>(ProfileButtonState.None)
    private val edit = MutableStateFlow<ProfileEdit?>(null)
    private var editJob: Job? = null

    private val _editErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** 編集を始められなかった (原文を読めなかった) ときの文言。画面が Toast で出す。 */
    val editErrors: SharedFlow<String> = _editErrors.asSharedFlow()

    // 前に開いたときの一覧を先に出す (showCachedPosts)
    private val posts = PostFeed(viewModelScope, firstPageLimit = InitialPostCount) { maxId ->
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

    /** 自分がお気に入りにしたポスト (自分のプロフィールのお気に入りのタブ)。初めて開いたときに読み込む。 */
    private val favourites = PostFeed.linked(viewModelScope) { maxId ->
        repository.fetchFavourites(session = session, maxId = maxId, limit = PageSize)
    }

    /** 自分がブックマークしたポスト (自分のプロフィールのブックマークのタブ)。初めて開いたときに読み込む。 */
    private val bookmarks = PostFeed.linked(viewModelScope) { maxId ->
        repository.fetchBookmarks(session = session, maxId = maxId, limit = PageSize)
    }

    private val feeds = ProfileTab.entries.associateWith { tab ->
        when (tab) {
            ProfileTab.Posts -> posts
            ProfileTab.Media -> media
            ProfileTab.Favourites -> favourites
            ProfileTab.Bookmarks -> bookmarks
        }
    }

    private fun feedOf(tab: ProfileTab): PostFeed = feeds.getValue(tab)

    val state: StateFlow<ProfileScreenState> = combine(
        combine(profile, extras, ::Pair),
        combine(posts.state, media.state, favourites.state, bookmarks.state) { p, m, f, b -> listOf(p, m, f, b) },
        tab,
        combine(button, edit, ::Pair),
    ) { (profile, extras), (posts, media, favourites, bookmarks), tab, (button, edit) ->
        ProfileScreenState(profile, extras, posts, media, favourites, bookmarks, tab, button, edit)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileScreenState())

    /** ピン留めを取得したか (取得の後に読み終わったキャッシュで上書きしない)。 */
    private var pinnedFetched = false

    init {
        showCachedPosts()
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
        for (feed in feeds.values) if (feed !== posts && feed.state.value.loaded) feed.refresh()
    }

    /** [tab] の一覧の続きを読む (ページャの各ページから呼ぶ)。 */
    fun loadMore(tab: ProfileTab) = feedOf(tab).loadMore()

    /** 表示中のタブ (ページャのページ)。初めて開いたタブは読み込む。 */
    fun selectTab(selected: ProfileTab) {
        tab.value = selected
        if (selected != ProfileTab.Posts) feedOf(selected).ensureLoaded()
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

    /** ブロック・ミュート・ブーストの非表示 (またはその取り消し) を行う。確認は画面側で済ませてから呼ぶ。 */
    fun performAccountAction(action: AccountAction) {
        val current = button.value as? ProfileButtonState.Follow ?: return
        if (current.isUpdating) return
        button.value = current.copy(isUpdating = true)
        viewModelScope.launch {
            try {
                val updated = when (action.type) {
                    AccountActionType.Block -> repository.setBlocking(session, accountId, block = !action.undo)
                    AccountActionType.Mute -> repository.setMuting(session, accountId, mute = !action.undo)
                    AccountActionType.HideBoosts -> repository.setShowingReblogs(session, accountId, show = action.undo)
                }
                button.update { (it as? ProfileButtonState.Follow)?.copy(relationship = updated, isUpdating = false) ?: it }
                // ブロックするとフォローが切れる。フォロワー数はプロフィールを読み直さずに手元で合わせる
                val delta = updated.following.toInt() - current.relationship.following.toInt()
                if (delta != 0) {
                    profile.update { s ->
                        s.copy(profile = s.profile?.let { it.copy(followersCount = (it.followersCount + delta).coerceAtLeast(0)) })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to ${action.type} (undo = ${action.undo})", e)
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

    /** プロフィールの編集を始める。編集用の原文 (と上限・絵文字) を読んでから [ProfileEdit.Editing] にする。 */
    fun startEdit() {
        if (button.value != ProfileButtonState.EditProfile || edit.value != null) return
        edit.value = ProfileEdit.Loading
        editJob = viewModelScope.launch {
            val limits = async { runOptional("instance") { instanceRepository.instance(session.serverUrl).profileLimits } }
            val emojis = async { runOptional("emojis") { emojiRepository.pickerEmojis(session.serverUrl) } }
            try {
                val source = repository.fetchProfileSource(session)
                edit.value = ProfileEdit.Editing(
                    editor = ProfileEditor(source),
                    limits = limits.await() ?: ProfileLimits(),
                    emojis = emojis.await().orEmpty(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load the profile source", e)
                edit.value = null
                _editErrors.tryEmit(e.userMessage())
            }
        }
    }

    /** 編集をやめる (入力は捨てる。確認は画面側で済ませてから呼ぶ)。保存の送信中なら何もしない。 */
    fun cancelEdit() {
        if ((edit.value as? ProfileEdit.Editing)?.isSaving == true) return
        editJob?.cancel()
        edit.value = null
    }

    /** 編集した内容を保存する。変わっていなければ送らずに閉じる。できたら表示を応答で置き換え、一覧も読み直す。 */
    fun saveEdit() {
        val current = edit.value as? ProfileEdit.Editing ?: return
        if (current.isSaving) return
        val editor = current.editor
        val source = editor.toSource()
        if (source.problems(current.limits).isNotEmpty()) return
        if (!editor.hasChanges) {
            edit.value = null
            return
        }
        edit.value = current.copy(isSaving = true, error = null)
        editJob = viewModelScope.launch {
            try {
                val (avatar, header) = withContext(Dispatchers.IO) {
                    editor.avatar?.let(openUploadSource) to editor.header?.let(openUploadSource)
                }
                // 上限 (2MB) を超える・受け付けない形式の画像は送る前に作り直す (WebP を使えるかは添付の形式から決める)
                val imageLimits = profileImageLimits(
                    runOptional("instance") { instanceRepository.instance(session.serverUrl) }?.composeLimits?.supportedMimeTypes,
                )
                val preparedAvatar = avatar?.let { prepareImage(it, imageLimits) }
                val preparedHeader = header?.let { prepareImage(it, imageLimits) }
                val updated = repository.updateProfile(session, source, preparedAvatar, preparedHeader)
                profile.update { it.copy(profile = updated) }
                edit.value = null
                // 自分のポストの名前・アイコンも変わるので、一覧を読み直す
                posts.refresh()
                for (feed in feeds.values) if (feed !== posts && feed.state.value.loaded) feed.refresh()
                loadExtras()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update the profile", e)
                edit.update { (it as? ProfileEdit.Editing)?.copy(isSaving = false, error = e.userMessage()) ?: it }
            }
        }
    }

    suspend fun vote(post: Post, choices: List<Int>): Boolean =
        viewModelScope.submitVote(statusRepository, session, post, choices) { poll ->
            feeds.values.forEach { feed -> feed.updatePosts { it.withPoll(poll) } }
            extras.update { e -> e.copy(pinnedPosts = e.pinnedPosts.map { it.withPoll(poll) }) }
        }

    private val postActions = PostActionController(
        scope = viewModelScope,
        send = { statusId, action -> statusRepository.perform(session, statusId, action) },
        update = { transform ->
            feeds.values.forEach { it.updatePosts(transform) }
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

    /**
     * 前に開いたときの投稿一覧の最初のページとピン留め (キャッシュ) を、取得が終わるまでの間だけ先に出す。取得の結果は
     * 読み進めた位置を保つよう当てる ([es.aria_on_the_planet.quasar.ui.feed.mergeRefreshedPosts])。
     * ピン留めは一覧の上に入るので、後から出ると先頭にいても見えない位置に入る。なので両方読んでから一緒に出す。
     */
    private fun showCachedPosts() {
        viewModelScope.launch {
            val pinnedRead = async { runOptional("cached pinned posts") { repository.cachedPinnedPosts(accountId) } }
            val cachedPosts = runOptional("cached posts") { repository.cachedPosts(accountId) }
            val cachedPinned = pinnedRead.await()
            if (cachedPinned != null && !pinnedFetched) extras.update { it.copy(pinnedPosts = cachedPinned) }
            cachedPosts?.let { list -> posts.showCached(list.map(TimelineItem::PostItem)) }
        }
    }

    private fun loadExtras() {
        viewModelScope.launch {
            val pinned = runOptional("pinned posts") { repository.fetchPinnedPosts(session, accountId) }
            pinned?.let { posts ->
                pinnedFetched = true
                extras.update { it.copy(pinnedPosts = posts) }
            }
        }
        viewModelScope.launch {
            val followers = runOptional("followers you follow") { repository.fetchFamiliarFollowers(session, accountId) }
            followers?.let { users -> extras.update { it.copy(followersYouFollow = users) } }
        }
    }

    /**
     * 自分のプロフィールか、相手との関係を読む。
     * 関係はキャッシュにあれば先に出し、期限内なら取りに行かない ([force] なら取り直す)。
     */
    private fun loadRelationship(force: Boolean) {
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
