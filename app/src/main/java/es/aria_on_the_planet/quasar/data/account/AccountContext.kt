package es.aria_on_the_planet.quasar.data.account

import es.aria_on_the_planet.quasar.data.cache.CacheDatabase
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
import es.aria_on_the_planet.quasar.data.settings.AccountSettingsStore
import es.aria_on_the_planet.quasar.data.streaming.StreamingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * アカウント 1 つ分のデータ層の依存 (セッション・キャッシュ・設定・Repository・ストリーミング)。Repository は [session] に
 * 結びついていて、メソッドではセッションを受けない (別のアカウントのセッションを渡せない)。
 *
 * ViewModel は作るときにこれを受け取り、寿命の間ずっと同じものを使う (「今のアカウント」をその都度読まない)。
 * 下書き・自分のポストの編集・メニューの操作 (UI 側) は `ui/account/AccountUiContext` が持つ。
 * 計画は docs/multi-account.md。
 *
 * サーバ単位でトークンの要らないもの (`InstanceRepository` / `EmojiRepository`) と [MastodonApi] はアプリ全体で共有する。
 *
 * @param database このアカウント専用のキャッシュの DB (`CacheDatabase.create(context, key)`)。これが持ち、[close] で閉じる。
 *   開いたら上限を超えた古い分を消す。
 * @param settings このアカウントの設定。DataStore は同じファイルに 1 つしか作れないので、`AppContainer` がキーごとに持つ
 *   (トークンの差し替えで作り直しても同じものを使う)。
 */
class AccountContext(
    val key: AccountKey,
    val session: Session,
    private val database: CacheDatabase,
    val settings: AccountSettingsStore,
    api: MastodonApi,
    okHttpClient: OkHttpClient,
    json: Json,
    instanceRepository: InstanceRepository,
) {
    /** このアカウントのコルーチン ([close] で止める)。 */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val cache = EntityCache(database.cacheDao(), json)
    val timelineRepository = TimelineRepository(session, api, cache)
    val accountRepository = AccountRepository(session, api, cache)
    val statusRepository = StatusRepository(session, api, cache)
    val notificationRepository = NotificationRepository(session, api, cache)
    val searchRepository = SearchRepository(session, api, cache)
    val mediaRepository = MediaRepository(session, api)
    val streamingClient = StreamingClient(session, okHttpClient, json, instanceRepository, cache)

    init {
        scope.launch { cache.trim() }
    }

    /**
     * このアカウントを使わなくなった (ログアウト・トークンの差し替え): コルーチンを止め、キャッシュの DB を閉じる。以後は使わない
     * (閉じた後に届いた結果のキャッシュへの書き込みは失敗し、ログに出るだけ)。
     */
    fun close() {
        scope.cancel()
        database.close()
    }
}
