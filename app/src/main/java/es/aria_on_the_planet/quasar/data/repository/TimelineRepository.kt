package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.TimelineType

class TimelineRepository(
    private val api: MastodonApi,
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
        val statuses = when (type) {
            TimelineType.Home -> api.homeTimeline(server, token, maxId, sinceId = sinceId)
            TimelineType.Local -> api.publicTimeline(server, token, local = true, maxId = maxId, sinceId = sinceId)
            TimelineType.Global -> api.publicTimeline(server, token, local = false, maxId = maxId, sinceId = sinceId)
        }
        return statuses.map { it.toDomain() }
    }
}
