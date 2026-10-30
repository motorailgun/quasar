package es.aria_on_the_planet.quasar.di

import android.content.Context
import android.net.Uri
import es.aria_on_the_planet.quasar.data.auth.OAuthLogin
import es.aria_on_the_planet.quasar.data.cache.CacheDatabase
import es.aria_on_the_planet.quasar.data.cache.EntityCache
import es.aria_on_the_planet.quasar.data.media.ContentUriUploadSource
import es.aria_on_the_planet.quasar.data.media.ImagePrepareOptions
import es.aria_on_the_planet.quasar.data.media.ImageReencoder
import es.aria_on_the_planet.quasar.data.media.toImageUploadLimits
import es.aria_on_the_planet.quasar.data.media.MediaDownloader
import es.aria_on_the_planet.quasar.data.media.SystemMediaDownloader
import es.aria_on_the_planet.quasar.data.media.UploadSource
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.repository.EmojiRepository
import es.aria_on_the_planet.quasar.data.repository.InstanceRepository
import es.aria_on_the_planet.quasar.data.repository.MediaRepository
import es.aria_on_the_planet.quasar.data.repository.NotificationRepository
import es.aria_on_the_planet.quasar.data.repository.SearchRepository
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
import es.aria_on_the_planet.quasar.ui.composer.PostEditStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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

    /** API の結果のキャッシュ (Room)。 */
    private val cache = EntityCache(CacheDatabase.create(context).cacheDao(), json)

    private val api = MastodonApi(okHttpClient, json)

    private val secureStorage = SecureStorage(context)

    val sessionStore = SessionStore(context, secureStorage)

    /** アプリ全体のコルーチン (プロセスと同じ寿命)。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** OAuth でのログイン・ログアウト。 */
    val oauthLogin = OAuthLogin(api, secureStorage, sessionStore, appScope)

    /** 設定。ここで作った時点 (起動時) から読み込み始める。 */
    val settingsStore = SettingsStore(context, appScope)
    val timelineRepository = TimelineRepository(api, cache)
    val accountRepository = AccountRepository(api, cache)
    val statusRepository = StatusRepository(api, cache)
    val notificationRepository = NotificationRepository(api, cache)
    val searchRepository = SearchRepository(api, cache)

    init {
        appScope.launch {
            // 起動時に上限を超えた古い分を消す
            cache.trim()
            // ログアウト・別のアカウントでのログインで消す (起動時の今のセッションでは消さない)。キャッシュの中身は
            // ログイン中のユーザーから見た状態 (お気に入りしたか・関係・非公開のポスト) なので、別のユーザーに見せない
            sessionStore.session
                .drop(1)
                .collect { cache.clear() }
        }
    }
    val instanceRepository = InstanceRepository(api)
    val emojiRepository = EmojiRepository(api)
    val mediaRepository = MediaRepository(api)

    private val contentResolver = context.applicationContext.contentResolver

    /** 端末のファイル (Photo Picker の結果等) をアップロード元にする。問い合わせを伴うので IO スレッドで呼ぶ。 */
    fun uploadSource(uri: Uri): UploadSource = ContentUriUploadSource(contentResolver, uri)

    /**
     * 送る前の画像の手直し。上限を超える画像の作り直し (投稿の添付は設定でオンのとき、プロフィールの画像は常に) と、
     * メタデータの除去 (投稿の添付で設定がオンのとき)。
     */
    val imageReencoder = ImageReencoder()

    /** メディアの保存 (システムの DownloadManager に任せる)。 */
    val mediaDownloader: MediaDownloader = SystemMediaDownloader(context)

    /** 書きかけの投稿 (クイック投稿バーと投稿画面で共有)。 */
    val draftStore = DraftStore { session, scope, onPublished -> composerSession(session, scope, null, onPublished) }

    /** 自分のポストの編集・削除 (どの画面のメニューからでも同じ流れ)。 */
    val postEditStore = PostEditStore(statusRepository) { session, scope, target, onSaved ->
        composerSession(session, scope, target, onSaved)
    }

    /**
     * 投稿欄 1 つ分。[editing] が `null` なら新しい投稿 ([draftStore] が持つ)、あれば自分のポストの編集
     * ([postEditStore] が持つ)。[onPublished] は投稿・保存できたとき。
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
        prepareUpload = { source, limits ->
            val settings = settingsStore.current()
            imageReencoder.prepare(
                source,
                limits.toImageUploadLimits(),
                ImagePrepareOptions(compressOversized = settings.compressOversizedImages, stripMetadata = settings.stripImageMetadata),
            )
        },
        onPublished = onPublished,
    )

    val streamingClient = StreamingClient(okHttpClient, json, instanceRepository, cache)
}
