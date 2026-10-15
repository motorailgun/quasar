package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Notification

class NotificationRepository(
    private val api: MastodonApi,
) {
    /** [maxId] より古い通知を取得する。要ログイン。 */
    suspend fun fetch(session: Session, maxId: String? = null): List<Notification> {
        val token = requireNotNull(session.accessToken) { "Notifications require login" }
        return api.notifications(session.serverUrl, token, maxId).map { it.toDomain() }
    }
}
