package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.cache.CachePolicy
import es.aria_on_the_planet.quasar.data.cache.EntityCache
import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.mapper.postVisibility
import es.aria_on_the_planet.quasar.data.mapper.toProfile
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.remote.dto.RelationshipDto
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Page
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.Relationship
import es.aria_on_the_planet.quasar.domain.model.User
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** ログイン中のアカウントと、その投稿の既定値。 */
data class CurrentAccount(
    val user: User,
    /** 投稿の既定の公開範囲 (`source.privacy`)。 */
    val defaultVisibility: PostVisibility,
    /** メディアを既定で閲覧注意にするか (`source.sensitive`)。 */
    val defaultSensitive: Boolean,
)

/** キャッシュから読んだ値。[isFresh] なら API を呼ばなくてよい (引っ張って更新したときを除く)。 */
data class CacheHit<T>(val value: T, val isFresh: Boolean)

/**
 * アカウント (プロフィール・関係・投稿一覧等)。取得・操作の結果はキャッシュ ([cache]) にも書き、
 * プロフィールと関係はキャッシュから先に読める ([cachedProfile] / [cachedRelationship])。
 */
class AccountRepository(
    private val api: MastodonApi,
    private val cache: EntityCache,
) {
    private val meMutex = Mutex()
    private var me: Pair<Session, CurrentAccount>? = null

    /** ログイン中のアカウント。セッションごとにメモリへキャッシュする。未認証なら `null`。 */
    suspend fun fetchMe(session: Session): CurrentAccount? {
        val token = session.accessToken ?: return null
        return meMutex.withLock {
            me?.takeIf { it.first == session }?.second
                ?: cache.issue().let { version ->
                    api.verifyCredentials(session.serverUrl, token).also { cache.putAccounts(listOf(it), version) }
                }.let { dto ->
                    CurrentAccount(
                        user = dto.toDomain(),
                        defaultVisibility = dto.source?.privacy?.let(::postVisibility) ?: PostVisibility.Public,
                        defaultSensitive = dto.source?.sensitive == true,
                    )
                }.also { me = session to it }
        }
    }

    /** [accountId] は [session] のサーバ内 ID (`User.inServerId`)。 */
    suspend fun fetchProfile(session: Session, accountId: String): Profile {
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
        session: Session,
        accountId: String,
        maxId: String? = null,
        limit: Int,
        onlyMedia: Boolean = false,
    ): List<Post> {
        val version = cache.issue()
        val statuses = api.accountStatuses(session.serverUrl, session.accessToken, accountId, maxId, limit, onlyMedia = onlyMedia)
        cache.putStatuses(statuses, version)
        return statuses.map { it.toDomain() }
    }

    /** [accountId] がフォローしているアカウント。`max_id` はフォロー関係の ID なので [Page.nextMaxId] を使う。 */
    suspend fun fetchFollowing(session: Session, accountId: String, maxId: String? = null): Page<User> =
        fetchRelations(session, accountId, "following", maxId)

    /** [accountId] のフォロワー。`max_id` はフォロー関係の ID なので [Page.nextMaxId] を使う。 */
    suspend fun fetchFollowers(session: Session, accountId: String, maxId: String? = null): Page<User> =
        fetchRelations(session, accountId, "followers", maxId)

    private suspend fun fetchRelations(session: Session, accountId: String, relation: String, maxId: String?): Page<User> {
        val version = cache.issue()
        val page = api.accountRelations(session.serverUrl, session.accessToken, accountId, relation, maxId)
        cache.putAccounts(page.items, version)
        return Page(page.items.map { it.toDomain() }, page.nextMaxId)
    }

    /** ログイン中ユーザーと [accountId] との関係。未認証なら `null`。 */
    suspend fun fetchRelationship(session: Session, accountId: String): Relationship? {
        val token = session.accessToken ?: return null
        val version = cache.issue()
        val relationship = api.relationships(session.serverUrl, token, listOf(accountId)).firstOrNull { it.id == accountId }
        relationship?.let { cache.putRelationships(listOf(it), version) }
        return relationship?.toDomain()
    }

    /** キャッシュにある [accountId] との関係。無ければ `null`。 */
    suspend fun cachedRelationship(accountId: String): CacheHit<Relationship>? =
        cache.relationship(accountId)?.let { CacheHit(it.value.toDomain(), it.isFresh(CachePolicy.RelationshipTtlMillis)) }

    /** [accountId] をフォローする (`follow = false` ならフォロー / リクエストをやめる)。要認証。更新後の関係を返す。 */
    suspend fun setFollowing(session: Session, accountId: String, follow: Boolean): Relationship {
        val token = checkNotNull(session.accessToken) { "Not logged in" }
        val version = cache.issue()
        val relationship = api.follow(session.serverUrl, token, accountId, follow)
        onRelationshipChanged(accountId, relationship, version)
        return relationship.toDomain()
    }

    /** [accountId] から自分へのフォローリクエストを承認する (`accept = false` なら拒否)。要認証。更新後の関係を返す。 */
    suspend fun answerFollowRequest(session: Session, accountId: String, accept: Boolean): Relationship {
        val token = checkNotNull(session.accessToken) { "Not logged in" }
        val version = cache.issue()
        val relationship = api.answerFollowRequest(session.serverUrl, token, accountId, accept)
        onRelationshipChanged(accountId, relationship, version)
        return relationship.toDomain()
    }

    /** 関係が変わったら、それを書き、相手のフォロー・フォロワー数は変わりうるので次に開いたとき取り直させる。 */
    private suspend fun onRelationshipChanged(accountId: String, relationship: RelationshipDto, version: Long) {
        cache.putRelationships(listOf(relationship), version)
        cache.invalidateAccount(accountId)
    }

    /** ピン留めされたポスト。サーバによっては多いため [MaxPinnedPosts] 件で打ち切る。 */
    suspend fun fetchPinnedPosts(session: Session, accountId: String): List<Post> {
        val version = cache.issue()
        val statuses = api.accountStatuses(session.serverUrl, session.accessToken, accountId, limit = MaxPinnedPosts, pinned = true)
            .take(MaxPinnedPosts)
        cache.putStatuses(statuses, version)
        return statuses.map { it.toDomain() }
    }

    /** ログイン中ユーザーがフォローしている人のうち、[accountId] をフォローしている人。未認証なら空。 */
    suspend fun fetchFamiliarFollowers(session: Session, accountId: String): List<User> {
        val token = session.accessToken ?: return emptyList()
        val version = cache.issue()
        val accounts = api.familiarFollowers(session.serverUrl, token, listOf(accountId))
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
