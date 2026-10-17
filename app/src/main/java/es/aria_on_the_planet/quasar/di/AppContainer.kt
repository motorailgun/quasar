package es.aria_on_the_planet.quasar.di

import android.content.Context
import android.net.Uri
import es.aria_on_the_planet.quasar.data.auth.OAuthLogin
import es.aria_on_the_planet.quasar.data.media.ContentUriUploadSource
import es.aria_on_the_planet.quasar.data.media.MediaDownloader
import es.aria_on_the_planet.quasar.data.media.SystemMediaDownloader
import es.aria_on_the_planet.quasar.data.media.UploadSource
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.repository.EmojiRepository
import es.aria_on_the_planet.quasar.data.repository.InstanceRepository
import es.aria_on_the_planet.quasar.data.repository.MediaRepository
import es.aria_on_the_planet.quasar.data.repository.NotificationRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.TimelineRepository
import es.aria_on_the_planet.quasar.data.session.SecureStorage
import es.aria_on_the_planet.quasar.data.session.SessionStore
import es.aria_on_the_planet.quasar.data.settings.SettingsStore
import es.aria_on_the_planet.quasar.data.streaming.StreamingClient
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.composer.ComposerSession
import es.aria_on_the_planet.quasar.ui.composer.EditTarget
import es.aria_on_the_planet.quasar.ui.composer.DraftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    private val secureStorage = SecureStorage(context)

    val sessionStore = SessionStore(context, secureStorage)

    /** アプリ全体のコルーチン (プロセスと同じ寿命)。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** OAuth でのログイン・ログアウト。 */
    val oauthLogin = OAuthLogin(api, secureStorage, sessionStore, appScope)

    /** 設定。ここで作った時点 (起動時) から読み込み始める。 */
    val settingsStore = SettingsStore(context, appScope)
    val timelineRepository = TimelineRepository(api)
    val accountRepository = AccountRepository(api)
    val statusRepository = StatusRepository(api)
    val notificationRepository = NotificationRepository(api)
    val instanceRepository = InstanceRepository(api)
    val emojiRepository = EmojiRepository(api)
    val mediaRepository = MediaRepository(api)

    private val contentResolver = context.applicationContext.contentResolver

    /** 端末のファイル (Photo Picker の結果等) をアップロード元にする。問い合わせを伴うので IO スレッドで呼ぶ。 */
    fun uploadSource(uri: Uri): UploadSource = ContentUriUploadSource(contentResolver, uri)

    /** メディアの保存 (システムの DownloadManager に任せる)。 */
    val mediaDownloader: MediaDownloader = SystemMediaDownloader(context)

    /** 書きかけの投稿 (クイック投稿バーと投稿画面で共有)。 */
    val draftStore = DraftStore { session, scope, onPublished -> composerSession(session, scope, null, onPublished) }

    /**
     * 投稿欄 1 つ分。[editing] が `null` なら新しい投稿 ([draftStore] が持つ)、あれば自分のポストの編集
     * (ポスト画面が持つ)。[onPublished] は投稿・保存できたとき。
     */
    fun composerSession(
        session: Session,
        scope: CoroutineScope,
        editing: EditTarget?,
        onPublished: (Post) -> Unit,
    ) = ComposerSession(
        session = session,
        scope = scope,
        accountRepository = accountRepository,
        instanceRepository = instanceRepository,
        emojiRepository = emojiRepository,
        statusRepository = statusRepository,
        mediaRepository = mediaRepository,
        defaultVisibilitySetting = settingsStore.loaded.map { it.defaultVisibility }.distinctUntilChanged(),
        openUploadSource = ::uploadSource,
        editing = editing,
        onPublished = onPublished,
    )

    val streamingClient = StreamingClient(okHttpClient, json, instanceRepository)
}
