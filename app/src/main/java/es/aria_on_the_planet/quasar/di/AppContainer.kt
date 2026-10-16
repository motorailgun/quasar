package es.aria_on_the_planet.quasar.di

import android.content.Context
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.repository.EmojiRepository
import es.aria_on_the_planet.quasar.data.repository.InstanceRepository
import es.aria_on_the_planet.quasar.data.repository.NotificationRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.TimelineRepository
import es.aria_on_the_planet.quasar.data.session.SessionStore
import es.aria_on_the_planet.quasar.data.streaming.StreamingClient
import es.aria_on_the_planet.quasar.ui.settings.DisplaySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/** 手動 DI コンテナ。 */
class AppContainer(context: Context) {
    val okHttpClient: OkHttpClient = OkHttpClient()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val api = MastodonApi(okHttpClient, json)

    val sessionStore = SessionStore(context)

    /** 表示設定。今は既定値固定で、設定画面を作ったら永続化した値で更新する。 */
    val displaySettings = MutableStateFlow(DisplaySettings())
    val timelineRepository = TimelineRepository(api)
    val accountRepository = AccountRepository(api)
    val statusRepository = StatusRepository(api)
    val notificationRepository = NotificationRepository(api)
    val instanceRepository = InstanceRepository(api)
    val emojiRepository = EmojiRepository(api)
    val streamingClient = StreamingClient(okHttpClient, json, instanceRepository)
}
