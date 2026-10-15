package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.mapper.toProfile
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.User

class AccountRepository(
    private val api: MastodonApi,
) {
    /** [accountId] は [session] のサーバ内 ID (`User.inServerId`)。 */
    suspend fun fetchProfile(session: Session, accountId: String): Profile =
        api.account(session.serverUrl, session.accessToken, accountId).toProfile()

    suspend fun fetchPosts(session: Session, accountId: String, maxId: String? = null, limit: Int): List<Post> =
        api.accountStatuses(session.serverUrl, session.accessToken, accountId, maxId, limit).map { it.toDomain() }

    /** ピン留めされたポスト。サーバによっては多いため [MaxPinnedPosts] 件で打ち切る。 */
    suspend fun fetchPinnedPosts(session: Session, accountId: String): List<Post> =
        api.accountStatuses(session.serverUrl, session.accessToken, accountId, limit = MaxPinnedPosts, pinned = true)
            .take(MaxPinnedPosts)
            .map { it.toDomain() }

    /** ログイン中ユーザーがフォローしている人のうち、[accountId] をフォローしている人。未認証なら空。 */
    suspend fun fetchFamiliarFollowers(session: Session, accountId: String): List<User> {
        val token = session.accessToken ?: return emptyList()
        return api.familiarFollowers(session.serverUrl, token, listOf(accountId))
            .firstOrNull { it.id == accountId }
            ?.accounts
            ?.map { it.toDomain() }
            .orEmpty()
    }

    companion object {
        const val MaxPinnedPosts = 20
    }
}
