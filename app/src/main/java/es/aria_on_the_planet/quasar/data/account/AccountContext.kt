package es.aria_on_the_planet.quasar.data.account

import es.aria_on_the_planet.quasar.data.cache.EntityCache
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.repository.InstanceRepository
import es.aria_on_the_planet.quasar.data.repository.MediaRepository
import es.aria_on_the_planet.quasar.data.repository.NotificationRepository
import es.aria_on_the_planet.quasar.data.repository.SearchRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.TimelineRepository
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.data.streaming.StreamingClient
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * アカウント 1 つ分のデータ層の依存 (セッション・キャッシュ・Repository・ストリーミング)。Repository は [session] に
 * 結びついていて、メソッドではセッションを受けない (別のアカウントのセッションを渡せない)。
 *
 * ViewModel は作るときにこれを受け取り、寿命の間ずっと同じものを使う (「今のアカウント」をその都度読まない)。
 * 下書き・自分のポストの編集・メニューの操作 (UI 側) は `ui/account/AccountUiContext` が持つ。
 * 計画は docs/multi-account.md。
 *
 * サーバ単位でトークンの要らないもの (`InstanceRepository` / `EmojiRepository`) と [MastodonApi] はアプリ全体で共有する。
 *
 * @param cache 今はアプリ全体で 1 つのもの (アカウントごとの DB は後の段階)。
 */
class AccountContext(
    val session: Session,
    val cache: EntityCache,
    api: MastodonApi,
    okHttpClient: OkHttpClient,
    json: Json,
    instanceRepository: InstanceRepository,
) {
    val timelineRepository = TimelineRepository(session, api, cache)
    val accountRepository = AccountRepository(session, api, cache)
    val statusRepository = StatusRepository(session, api, cache)
    val notificationRepository = NotificationRepository(session, api, cache)
    val searchRepository = SearchRepository(session, api, cache)
    val mediaRepository = MediaRepository(session, api)
    val streamingClient = StreamingClient(session, okHttpClient, json, instanceRepository, cache)
}
