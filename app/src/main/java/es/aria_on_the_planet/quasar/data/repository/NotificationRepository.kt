package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.cache.EntityCache
import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Notification
import es.aria_on_the_planet.quasar.domain.model.NotificationGroup
import es.aria_on_the_planet.quasar.domain.model.asGroup
import es.aria_on_the_planet.quasar.domain.model.settings.NotificationApi

/**
 * 通知の取得。どちらの API でも [NotificationGroup] の一覧として返すので、UI は API の違いを意識しない。
 * どちらを使うかは設定 (`AppSettings.notificationApi`) で、呼び出し側が渡す。
 * 通知の一覧はキャッシュしないが、中のポスト・アカウントはキャッシュ ([cache]) に書く (まとめの中身・ポスト画面・
 * プロフィールですぐ出すため)。
 */
class NotificationRepository(
    private val mastodonApi: MastodonApi,
    private val cache: EntityCache,
) {
    /** [maxId] より古い (または [sinceId] より新しい) 通知を [api] で取得する。要ログイン。 */
    suspend fun fetch(
        session: Session,
        api: NotificationApi,
        maxId: String? = null,
        sinceId: String? = null,
    ): List<NotificationGroup> {
        val token = requireNotNull(session.accessToken) { "Notifications require login" }
        val version = cache.issue()
        return when (api) {
            NotificationApi.Grouped -> {
                val results = mastodonApi.groupedNotifications(session.serverUrl, token, maxId, sinceId = sinceId)
                cache.putStatuses(results.statuses, version, results.accounts)
                results.toDomain()
            }
            NotificationApi.Ungrouped -> {
                val notifications = mastodonApi.notifications(session.serverUrl, token, maxId, sinceId = sinceId)
                cache.putStatuses(notifications.mapNotNull { it.status }, version, notifications.map { it.account })
                notifications.map { it.toDomain().asGroup() }
            }
        }
    }

    /** ストリーミングで届いた通知を、[api] の一覧と同じキー体系のグループにする。 */
    fun toGroup(notification: Notification, api: NotificationApi): NotificationGroup =
        notification.asGroup(useGroupKey = api == NotificationApi.Grouped)
}
