package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.NotificationGroup
import es.aria_on_the_planet.quasar.domain.model.asGroup

/** 通知の取得に使う API。 */
enum class NotificationApi {
    /** `GET /api/v2/notifications`。サーバがグルーピングする (Mastodon 4.3+)。 */
    Grouped,

    /** `GET /api/v1/notifications`。グルーピングしない (各通知を 1 件のグループとして扱う)。 */
    Ungrouped,
}

/**
 * 通知の取得。どちらの API でも [NotificationGroup] の一覧として返すので、UI は API の違いを意識しない。
 * [api] は今は固定値で、将来は設定画面やサーバのバージョン確認から切り替える想定。
 */
class NotificationRepository(
    private val mastodonApi: MastodonApi,
    var api: NotificationApi = NotificationApi.Grouped,
) {
    /** [maxId] より古い通知を取得する。要ログイン。 */
    suspend fun fetch(session: Session, maxId: String? = null): List<NotificationGroup> {
        val token = requireNotNull(session.accessToken) { "Notifications require login" }
        return when (api) {
            NotificationApi.Grouped ->
                mastodonApi.groupedNotifications(session.serverUrl, token, maxId).toDomain()
            NotificationApi.Ungrouped ->
                mastodonApi.notifications(session.serverUrl, token, maxId).map { it.toDomain().asGroup() }
        }
    }
}
