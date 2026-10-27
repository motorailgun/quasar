package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.cache.EntityCache
import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User

/** URL・`user@domain` を接続中サーバで引いた結果。 */
sealed interface ResolvedLink {
    data class ToPost(val post: Post) : ResolvedLink
    data class ToAccount(val user: User) : ResolvedLink
    data class ToHashtag(val name: String) : ResolvedLink
    data object NotFound : ResolvedLink
}

/** 検索 (`/api/v2/search`)。今は他のアプリから共有された URL を開くのに使うだけ。 */
class SearchRepository(
    private val api: MastodonApi,
    private val cache: EntityCache,
) {
    /**
     * [query] (ポスト・プロフィールの URL、`@user@domain`) を接続中サーバで引く (要認証)。サーバがまだ知らないものは
     * サーバが ActivityPub / WebFinger で取りに行く。見られないもの (フォロワー限定等)・取れないものは [ResolvedLink.NotFound]。
     */
    suspend fun resolve(session: Session, query: String): ResolvedLink {
        val version = cache.issue()
        val results = api.search(session.serverUrl, session.accessToken, query, resolve = true, limit = 1)
        cache.putStatuses(results.statuses, version)
        cache.putAccounts(results.accounts, version)
        return results.statuses.firstOrNull()?.let { ResolvedLink.ToPost(it.toDomain()) }
            ?: results.accounts.firstOrNull()?.let { ResolvedLink.ToAccount(it.toDomain()) }
            ?: results.hashtags.firstOrNull()?.let { ResolvedLink.ToHashtag(it.name) }
            ?: ResolvedLink.NotFound
    }
}
