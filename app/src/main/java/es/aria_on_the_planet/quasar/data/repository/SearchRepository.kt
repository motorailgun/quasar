package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.cache.EntityCache
import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.remote.dto.SearchResultsDto
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Hashtag
import es.aria_on_the_planet.quasar.domain.model.Page
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User

/** URL・`user@domain` を接続中サーバで引いた結果。 */
sealed interface ResolvedLink {
    data class ToPost(val post: Post) : ResolvedLink
    data class ToAccount(val user: User) : ResolvedLink
    data class ToHashtag(val name: String) : ResolvedLink
    data object NotFound : ResolvedLink
}

/**
 * 検索 (`/api/v2/search`)。検索画面と、他のアプリから共有された URL を開くのに使う。
 * 投稿欄のメンションの補完は `/api/v1/accounts/search` ([suggestAccounts])。
 */
class SearchRepository(
    private val session: Session,
    private val api: MastodonApi,
    private val cache: EntityCache,
) {
    /**
     * [query] (ポスト・プロフィールの URL、`@user@domain`) を接続中サーバで引く (要認証)。サーバがまだ知らないものは
     * サーバが ActivityPub / WebFinger で取りに行く。見られないもの (フォロワー限定等)・取れないものは [ResolvedLink.NotFound]。
     */
    suspend fun resolve(query: String): ResolvedLink {
        val version = cache.issue()
        val results = api.search(session.serverUrl, session.accessToken, query, resolve = true, limit = 1)
        cache.putStatuses(results.statuses, version)
        cache.putAccounts(results.accounts, version)
        return results.statuses.firstOrNull()?.let { ResolvedLink.ToPost(it.toDomain()) }
            ?: results.accounts.firstOrNull()?.let { ResolvedLink.ToAccount(it.toDomain()) }
            ?: results.hashtags.firstOrNull()?.let { ResolvedLink.ToHashtag(it.name) }
            ?: ResolvedLink.NotFound
    }

    /**
     * アカウントの検索。続きは `offset` で取るので、次ページの「`max_id`」には次の offset を入れる ([searchPage])。
     * 最初のページだけ `resolve` する (`@user@domain`・URL でサーバがまだ知らないアカウントも引ける)。
     */
    suspend fun searchAccounts(query: String, offset: String?): Page<User> =
        searchPage(query, "accounts", offset) { results, version ->
            cache.putAccounts(results.accounts, version)
            results.accounts.map { it.toDomain() }
        }

    /** ポストの検索。全文検索はサーバの設定 (Elasticsearch 等) 次第で、無ければ自分のポストや URL だけが当たる。 */
    suspend fun searchPosts(query: String, offset: String?): Page<Post> =
        searchPage(query, "statuses", offset) { results, version ->
            cache.putStatuses(results.statuses, version)
            results.statuses.map { it.toDomain() }
        }

    suspend fun searchHashtags(query: String, offset: String?): Page<Hashtag> =
        searchPage(query, "hashtags", offset) { results, _ ->
            results.hashtags.map { Hashtag(it.name, it.url) }
        }

    /** 投稿欄のメンションの補完の候補 ([query] は `@` を除いた `user` / `user@dom`)。サーバが知っているアカウントだけ。 */
    suspend fun suggestAccounts(query: String, limit: Int): List<User> {
        val version = cache.issue()
        val accounts = api.searchAccounts(session.serverUrl, session.accessToken, query, limit)
        cache.putAccounts(accounts, version)
        return accounts.map { it.toDomain() }
    }

    private suspend fun <T> searchPage(
        query: String,
        type: String,
        offset: String?,
        items: suspend (SearchResultsDto, version: Long) -> List<T>,
    ): Page<T> {
        val start = offset?.toIntOrNull() ?: 0
        val version = cache.issue()
        val results = api.search(
            session.serverUrl,
            session.accessToken,
            query,
            resolve = start == 0,
            limit = SearchPageSize,
            type = type,
            offset = start,
        )
        val page = items(results, version)
        // 1 ページ埋まらなければ終わり
        return Page(page, if (page.size < SearchPageSize) null else (start + page.size).toString())
    }

    private companion object {
        const val SearchPageSize = 20
    }
}
