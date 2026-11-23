package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.cache.CacheKind
import es.aria_on_the_planet.quasar.data.cache.CachePolicy
import es.aria_on_the_planet.quasar.data.cache.EntityCache
import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.mapper.postVisibility
import es.aria_on_the_planet.quasar.data.mapper.toProfile
import es.aria_on_the_planet.quasar.data.mapper.toProfileSource
import es.aria_on_the_planet.quasar.data.mapper.toUpdateCredentialsParams
import es.aria_on_the_planet.quasar.data.media.ProgressRequestBody
import es.aria_on_the_planet.quasar.data.media.UploadSource
import es.aria_on_the_planet.quasar.data.remote.LinkedPage
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto
import es.aria_on_the_planet.quasar.data.remote.dto.RelationshipDto
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Page
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.ProfileSource
import es.aria_on_the_planet.quasar.domain.model.Relationship
import es.aria_on_the_planet.quasar.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration
import java.time.Instant

/** ログイン中のアカウントと、その投稿の既定値。 */
data class CurrentAccount(
    val user: User,
    /** 投稿の既定の公開範囲 (`source.privacy`)。 */
    val defaultVisibility: PostVisibility,
    /** メディアを既定で閲覧注意にするか (`source.sensitive`)。 */
    val defaultSensitive: Boolean,
    /** 投稿の言語の設定 (`source.language`)。設定していなければ `null` (サーバは UI の言語を使う)。 */
    val defaultLanguage: String? = null,
    /** 自分へのフォローリクエストの件数 (`source.follow_requests_count`)。ドロワーのバッジに出す。 */
    val followRequestsCount: Int = 0,
)

/** 自分の操作で [accountId] との関係が [relationship] に変わった。 */
data class RelationshipChange(val accountId: String, val relationship: Relationship)

/** キャッシュから読んだ値。[isFresh] なら API を呼ばなくてよい (引っ張って更新したときを除く)。 */
data class CacheHit<T>(val value: T, val isFresh: Boolean)

/**
 * アカウント (プロフィール・関係・投稿一覧等)。取得・操作の結果はキャッシュ ([cache]) にも書き、
 * プロフィールと関係はキャッシュから先に読める ([cachedProfile] / [cachedRelationship])。
 */
class AccountRepository(
    private val session: Session,
    private val api: MastodonApi,
    private val cache: EntityCache,
) {
    private val changes = MutableSharedFlow<RelationshipChange>(extraBufferCapacity = 16)

    /**
     * 自分の操作 (フォロー・ブロック・ミュート・ブーストの非表示等) で変わった関係。ブロック・ミュートした相手のポストを
     * 各画面の一覧から消すのに使う ([es.aria_on_the_planet.quasar.domain.model.isHiddenBy])。
     */
    val relationshipChanges: SharedFlow<RelationshipChange> = changes.asSharedFlow()

    private val meMutex = Mutex()
    private val me = MutableStateFlow<CurrentAccount?>(null)

    /** [me] の版 ([EntityCache.issue]。リクエストを出した時点)。先に出した取得の応答が後から届いても巻き戻さない。 */
    private var meVersion = Long.MIN_VALUE

    /** [me] を取りに行った時刻 (端末の時計)。これより後のフォロー・フォローリクエストの通知は件数に入っていない。 */
    private var meFetchedAt: Instant? = null

    /** 取り直しのきっかけにした通知のうち最も新しいものの日時 (同じ通知で何度も取り直さない)。 */
    private var followNotificationHandledAt: Instant? = null

    /** 取得済みならログイン中のアカウント ([fetchMe] のキャッシュ)。待たずに返す。 */
    fun cachedMe(): CurrentAccount? = me.value

    /**
     * ログイン中のアカウント。メモリへキャッシュし、取得済みなら取りに行かない (この Repository は 1 つのセッションに
     * 結びついている)。取り直すのは [refreshMe] / [refreshMeAfterFollowNotification]。
     */
    suspend fun fetchMe(): CurrentAccount =
        meMutex.withLock { cachedMe() ?: fetchMeLocked() }

    /** ログイン中のアカウントを取り直す (フォローリクエストの件数等)。結果は [myAccount] に流れる。 */
    suspend fun refreshMe(): CurrentAccount = meMutex.withLock { fetchMeLocked() }

    /**
     * [at] に届いたフォロー・フォローリクエストの通知を見た。最後に取ったときより後のものなら、フォローリクエストの件数が
     * 変わっているかもしれないので取り直す。まだ一度も取っていなければ何もしない (取るときに入る)。
     */
    suspend fun refreshMeAfterFollowNotification(at: Instant) {
        if (!needsRefreshForFollowNotification(at, meFetchedAt, followNotificationHandledAt)) return
        meMutex.withLock {
            // 待っている間にほかが取り直していれば取らない
            if (!needsRefreshForFollowNotification(at, meFetchedAt, followNotificationHandledAt)) return
            followNotificationHandledAt = at
            fetchMeLocked()
        }
    }

    private suspend fun fetchMeLocked(): CurrentAccount {
        val version = cache.issue()
        val fetchedAt = Instant.now()
        val dto = api.verifyCredentials(session.serverUrl, session.accessToken)
        cache.putAccounts(listOf(dto), version)
        return rememberMe(dto, version, fetchedAt)
    }

    /**
     * ログイン中のアカウントの取得済みの値と、その後の変化 (プロフィールの編集)。投稿欄・タイムラインのヘッダの
     * アイコン等が追従するのに使う。取得前は何も流れない。
     */
    fun myAccount(): Flow<CurrentAccount> =
        me.filterNotNull().distinctUntilChanged()

    /** [dto] (`verify_credentials` / `update_credentials` の応答) を [me] にする。[version] が古ければ捨てる。 */
    private fun rememberMe(dto: AccountDto, version: Long, fetchedAt: Instant): CurrentAccount {
        val account = CurrentAccount(
            user = dto.toDomain(),
            defaultVisibility = dto.source?.privacy?.let(::postVisibility) ?: PostVisibility.Public,
            defaultSensitive = dto.source?.sensitive == true,
            defaultLanguage = dto.source?.language?.takeIf { it.isNotBlank() },
            followRequestsCount = dto.source?.followRequestsCount ?: 0,
        )
        synchronized(me) {
            if (version < meVersion) return me.value ?: account
            meVersion = version
            meFetchedAt = fetchedAt
            me.value = account
        }
        return account
    }

    /**
     * 自分のプロフィールの編集用の原文。表示中のプロフィールは HTML なので、`verify_credentials` の `source` を
     * 取り直す (編集の直前に読むので、ほかの端末で変えた分も入る)。`source` を返さないサーバでは例外。
     */
    suspend fun fetchProfileSource(): ProfileSource {
        val version = cache.issue()
        val fetchedAt = Instant.now()
        val dto = api.verifyCredentials(session.serverUrl, session.accessToken)
        cache.putAccounts(listOf(dto), version)
        rememberMe(dto, version, fetchedAt)
        return dto.toProfileSource() ?: throw IOException("The server did not return the profile source")
    }

    /**
     * 自分のプロフィールを [source] の内容にする。[avatar] / [header] があれば画像も替える (`null` ならそのまま)。
     * 応答のプロフィールを返し、キャッシュと [myAccount] にも反映する。
     */
    suspend fun updateProfile(
        source: ProfileSource,
        avatar: UploadSource?,
        header: UploadSource?,
    ): Profile {
        val images = listOfNotNull(
            avatar?.let { Triple("avatar", it.fileName, ProgressRequestBody(it) {}) },
            header?.let { Triple("header", it.fileName, ProgressRequestBody(it) {}) },
        )
        val version = cache.issue()
        val fetchedAt = Instant.now()
        val dto = api.updateCredentials(session.serverUrl, session.accessToken, source.toUpdateCredentialsParams(), images)
        cache.putAccounts(listOf(dto), version)
        rememberMe(dto, version, fetchedAt)
        return dto.toProfile()
    }

    /** [accountId] は接続中サーバ内の ID (`User.inServerId`)。 */
    suspend fun fetchProfile(accountId: String): Profile {
        val version = cache.issue()
        val account = api.account(session.serverUrl, session.accessToken, accountId)
        cache.putAccounts(listOf(account), version)
        return account.toProfile()
    }

    /** キャッシュにあるプロフィール (ポストの投稿者・通知等で取ったものを含む)。無ければ `null`。 */
    suspend fun cachedProfile(accountId: String): CacheHit<Profile>? =
        cache.account(accountId)?.let { CacheHit(it.value.toProfile(), it.isFresh(CachePolicy.AccountTtlMillis)) }

    /** @param onlyMedia `true` ならメディア付きのポストのみ (プロフィールのメディア一覧)。 */
    suspend fun fetchPosts(
        accountId: String,
        maxId: String? = null,
        limit: Int,
        onlyMedia: Boolean = false,
    ): List<Post> {
        val version = cache.issue()
        val statuses = api.accountStatuses(session.serverUrl, session.accessToken, accountId, maxId, limit, onlyMedia = onlyMedia)
        // 投稿一覧の最初のページは並びも持ち、次に開いたときに先に出す ([cachedPosts])
        if (maxId == null && !onlyMedia) {
            cache.putPostList(CacheKind.AccountPosts, accountId, statuses, version)
        } else {
            cache.putStatuses(statuses, version)
        }
        return statuses.map { it.toDomain() }
    }

    /** 前に開いたときの [accountId] の投稿一覧の最初のページ (キャッシュ)。無ければ `null`。 */
    suspend fun cachedPosts(accountId: String): List<Post>? = cachedPostList(CacheKind.AccountPosts, accountId)

    /** 前に開いたときの [accountId] のピン留めポスト (キャッシュ)。無ければ `null`。 */
    suspend fun cachedPinnedPosts(accountId: String): List<Post>? = cachedPostList(CacheKind.PinnedPosts, accountId)

    // Room と JSON の変換を何度も行き来するので、全体を Main の外で行う (Main に戻るたびに画面の組み立てを待つと、
    // ローカルの API より遅くなった)
    private suspend fun cachedPostList(kind: CacheKind, accountId: String): List<Post>? = withContext(Dispatchers.Default) {
        cache.postList(kind, accountId)?.value?.map { it.toDomain() }
    }

    /** ログイン中のユーザーがお気に入りにしたポスト。`max_id` はお気に入りの ID なので [Page.nextMaxId] を使う。 */
    suspend fun fetchFavourites(maxId: String? = null, limit: Int): Page<Post> =
        fetchOwnStatuses { api.favourites(session.serverUrl, session.accessToken, maxId, limit) }

    /** ログイン中のユーザーがブックマークしたポスト。`max_id` はブックマークの ID なので [Page.nextMaxId] を使う。 */
    suspend fun fetchBookmarks(maxId: String? = null, limit: Int): Page<Post> =
        fetchOwnStatuses { api.bookmarks(session.serverUrl, session.accessToken, maxId, limit) }

    private suspend fun fetchOwnStatuses(fetch: suspend () -> LinkedPage<StatusDto>): Page<Post> {
        val version = cache.issue()
        val page = fetch()
        cache.putStatuses(page.items, version)
        return Page(page.items.map { it.toDomain() }, page.nextMaxId)
    }

    /** 自分へのフォローリクエストを送ってきたアカウント。`max_id` はリクエストの ID なので [Page.nextMaxId] を使う。 */
    suspend fun fetchFollowRequests(maxId: String? = null): Page<User> {
        val version = cache.issue()
        val page = api.followRequests(session.serverUrl, session.accessToken, maxId)
        cache.putAccounts(page.items, version)
        return Page(page.items.map { it.toDomain() }, page.nextMaxId)
    }

    /** [accountId] がフォローしているアカウント。`max_id` はフォロー関係の ID なので [Page.nextMaxId] を使う。 */
    suspend fun fetchFollowing(accountId: String, maxId: String? = null): Page<User> =
        fetchRelations(accountId, "following", maxId)

    /** [accountId] のフォロワー。`max_id` はフォロー関係の ID なので [Page.nextMaxId] を使う。 */
    suspend fun fetchFollowers(accountId: String, maxId: String? = null): Page<User> =
        fetchRelations(accountId, "followers", maxId)

    private suspend fun fetchRelations(accountId: String, relation: String, maxId: String?): Page<User> {
        val version = cache.issue()
        val page = api.accountRelations(session.serverUrl, session.accessToken, accountId, relation, maxId)
        cache.putAccounts(page.items, version)
        return Page(page.items.map { it.toDomain() }, page.nextMaxId)
    }

    /** ログイン中ユーザーと [accountId] との関係。 */
    suspend fun fetchRelationship(accountId: String): Relationship? {
        val version = cache.issue()
        val relationship = api.relationships(session.serverUrl, session.accessToken, listOf(accountId)).firstOrNull { it.id == accountId }
        relationship?.let { cache.putRelationships(listOf(it), version) }
        return relationship?.toDomain()
    }

    /** キャッシュにある [accountId] との関係。無ければ `null`。 */
    suspend fun cachedRelationship(accountId: String): CacheHit<Relationship>? =
        cache.relationship(accountId)?.let { CacheHit(it.value.toDomain(), it.isFresh(CachePolicy.RelationshipTtlMillis)) }

    /** [accountId] をフォローする (`follow = false` ならフォロー / リクエストをやめる)。更新後の関係を返す。 */
    suspend fun setFollowing(accountId: String, follow: Boolean): Relationship =
        changeRelationship(accountId) { api.follow(session.serverUrl, session.accessToken, accountId, follow) }

    /** [accountId] から自分へのフォローリクエストを承認する (`accept = false` なら拒否)。更新後の関係を返す。 */
    suspend fun answerFollowRequest(accountId: String, accept: Boolean): Relationship {
        val version = cache.issue()
        val relationship = api.answerFollowRequest(session.serverUrl, session.accessToken, accountId, accept)
        onRelationshipChanged(accountId, relationship, version)
        // 件数は取り直さずに手元で減らす。返事より前に出した取得の応答が後から届いても戻さない (版を進める)
        synchronized(me) {
            val current = me.value
            if (current != null && version >= meVersion) {
                meVersion = version
                me.value = current.copy(followRequestsCount = (current.followRequestsCount - 1).coerceAtLeast(0))
            }
        }
        return relationship.toDomain()
    }

    /** [accountId] をブロックする (`block = false` なら解除)。ブロックするとフォロー・フォローされている関係も切れる。 */
    suspend fun setBlocking(accountId: String, block: Boolean): Relationship =
        changeRelationship(accountId) { api.block(session.serverUrl, session.accessToken, accountId, block) }

    /** [accountId] をミュートする (`mute = false` なら解除)。期限なし・通知もミュート。 */
    suspend fun setMuting(accountId: String, mute: Boolean): Relationship =
        changeRelationship(accountId) { api.mute(session.serverUrl, session.accessToken, accountId, mute) }

    /** フォロー中の [accountId] のブーストを出すか。フォローしていないときは呼ばない (フォローになってしまう)。 */
    suspend fun setShowingReblogs(accountId: String, show: Boolean): Relationship =
        changeRelationship(accountId) { api.setShowingReblogs(session.serverUrl, session.accessToken, accountId, show) }

    private suspend fun changeRelationship(accountId: String, request: suspend () -> RelationshipDto): Relationship {
        val version = cache.issue()
        val relationship = request()
        onRelationshipChanged(accountId, relationship, version)
        return relationship.toDomain()
    }

    /** 関係が変わったら、それを書き、相手のフォロー・フォロワー数は変わりうるので次に開いたとき取り直させる。 */
    private suspend fun onRelationshipChanged(accountId: String, relationship: RelationshipDto, version: Long) {
        cache.putRelationships(listOf(relationship), version)
        cache.invalidateAccount(accountId)
        changes.tryEmit(RelationshipChange(accountId, relationship.toDomain()))
    }

    /** ピン留めされたポスト。サーバによっては多いため [MaxPinnedPosts] 件で打ち切る。 */
    suspend fun fetchPinnedPosts(accountId: String): List<Post> {
        val version = cache.issue()
        val statuses = api.accountStatuses(session.serverUrl, session.accessToken, accountId, limit = MaxPinnedPosts, pinned = true)
            .take(MaxPinnedPosts)
        cache.putPostList(CacheKind.PinnedPosts, accountId, statuses, version)
        return statuses.map { it.toDomain() }
    }

    /** ログイン中ユーザーがフォローしている人のうち、[accountId] をフォローしている人。 */
    suspend fun fetchFamiliarFollowers(accountId: String): List<User> {
        val version = cache.issue()
        val accounts = api.familiarFollowers(session.serverUrl, session.accessToken, listOf(accountId))
            .firstOrNull { it.id == accountId }
            ?.accounts
            .orEmpty()
        cache.putAccounts(accounts, version)
        return accounts.map { it.toDomain() }
    }

    companion object {
        const val MaxPinnedPosts = 20
    }
}

/**
 * 端末とサーバの時計のずれの許容。通知の日時はサーバの時計、取りに行った時刻は端末の時計なので、取りに行った少し前の
 * 通知でも取り直す (多めに取り直すだけで、取りこぼすよりよい)。
 */
private val ClockSkewAllowance: Duration = Duration.ofMinutes(1)

/**
 * [at] のフォロー・フォローリクエストの通知を見て、ログイン中のアカウントを取り直すか。[meFetchedAt] (最後に取りに
 * 行った時刻、まだなら `null` = 取らない) の少し前より後で、[handledAt] (前に取り直すきっかけにした通知) より新しいもの。
 */
internal fun needsRefreshForFollowNotification(at: Instant, meFetchedAt: Instant?, handledAt: Instant?): Boolean =
    meFetchedAt != null &&
        at > meFetchedAt - ClockSkewAllowance &&
        (handledAt == null || at > handledAt)
