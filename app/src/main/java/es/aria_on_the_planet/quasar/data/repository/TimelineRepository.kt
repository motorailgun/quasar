package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.cache.EntityCache
import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Page
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.TimelineType

/** タイムラインの取得。取得したポストはキャッシュ ([cache]) にも書く (ポスト画面・プロフィールですぐ出すため)。 */
class TimelineRepository(
    private val api: MastodonApi,
    private val cache: EntityCache,
) {
    /**
     * [maxId] より古い (または [sinceId] より新しい) ポストを取得する。どちらも `null` なら最新から。
     */
    suspend fun fetch(
        session: Session,
        type: TimelineType,
        maxId: String? = null,
        sinceId: String? = null,
    ): List<Post> {
        val (server, token) = session.serverUrl to session.accessToken
        val version = cache.issue()
        val statuses = when (type) {
            TimelineType.Home -> api.homeTimeline(server, token, maxId, sinceId = sinceId)
            TimelineType.Local -> api.publicTimeline(server, token, local = true, maxId = maxId, sinceId = sinceId)
            TimelineType.Global -> api.publicTimeline(server, token, local = false, maxId = maxId, sinceId = sinceId)
        }
        cache.putStatuses(statuses, version)
        return statuses.map { it.toDomain() }
    }

    /** ハッシュタグ [hashtag] (`#` 無し) を含む公開ポストのうち、[maxId] より古いものを取得する。 */
    suspend fun fetchHashtag(session: Session, hashtag: String, maxId: String? = null): List<Post> {
        val version = cache.issue()
        val statuses = api.hashtagTimeline(session.serverUrl, session.accessToken, hashtag, maxId)
        cache.putStatuses(statuses, version)
        return statuses.map { it.toDomain() }
    }

    /**
     * 非公開の返信 (ダイレクト) の会話を新しい順に、それぞれの最新のポストで返す (ポストの無い会話は除く)。
     * `max_id` は会話の ID なので [Page.nextMaxId] を使う。
     *
     * ポストの無い会話 (後始末を通らずに消えたポストを指したまま残ったもの) だけのページは、続きがあれば次のページを読む。
     * 空のページを返すと終端と見なされ、その先が読めなくなるため。
     */
    suspend fun fetchConversations(session: Session, maxId: String? = null): Page<Post> {
        var nextMaxId = maxId
        while (true) {
            val version = cache.issue()
            val page = api.conversations(session.serverUrl, session.accessToken, nextMaxId)
            val statuses = page.items.mapNotNull { it.lastStatus }
            cache.putStatuses(statuses, version)
            if (statuses.isNotEmpty() || page.nextMaxId == null) return Page(statuses.map { it.toDomain() }, page.nextMaxId)
            nextMaxId = page.nextMaxId
        }
    }
}
