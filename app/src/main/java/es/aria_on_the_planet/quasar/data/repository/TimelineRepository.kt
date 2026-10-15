package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.TimelineType

class TimelineRepository(
    private val api: MastodonApi,
) {
    /** [maxId] より古いポストを取得する。`null` なら最新から。 */
    suspend fun fetch(session: Session, type: TimelineType, maxId: String? = null): List<Post> {
        val statuses = when (type) {
            TimelineType.Home -> api.homeTimeline(session.serverUrl, session.accessToken, maxId)
            TimelineType.Local -> api.publicTimeline(session.serverUrl, session.accessToken, local = true, maxId = maxId)
            TimelineType.Global -> api.publicTimeline(session.serverUrl, session.accessToken, local = false, maxId = maxId)
        }
        return statuses.map { it.toDomain() }
    }
}
