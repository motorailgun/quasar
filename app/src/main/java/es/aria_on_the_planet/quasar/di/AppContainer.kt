package es.aria_on_the_planet.quasar.di

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
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
import es.aria_on_the_planet.quasar.data.translation.PostTranslator
import es.aria_on_the_planet.quasar.data.translation.TextClassifierLanguageDetector
import es.aria_on_the_planet.quasar.data.translation.TranslationModelFiles
import es.aria_on_the_planet.quasar.data.translation.createTranslationEngine
import es.aria_on_the_planet.quasar.ui.translation.PostTranslationStore
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.composer.ComposerSession
import es.aria_on_the_planet.quasar.ui.composer.EditTarget
import es.aria_on_the_planet.quasar.ui.composer.DraftStore
import es.aria_on_the_planet.quasar.ui.composer.PostEditStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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

    /** 詳細のパネル (タブレット表示) の中の書きかけの投稿。[draftStore] とは別で、パネルの中の画面だけで共有する。 */
    val panelDraftStore = DraftStore { session, scope, onPublished -> composerSession(session, scope, null, onPublished) }

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

    /**
     * ポストの訳 (端末内のモデル)。モデルを持たないビルド (lite) では null で、翻訳の表示・設定は出ない。
     * 状態はメインスレッドで持つ (一覧の各項目が読む)。モデルは最初に訳すときに読み込む。
     */
    val postTranslations: PostTranslationStore?

    init {
        val translationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val modelFiles = TranslationModelFiles(context, translationScope)
        val engine = createTranslationEngine(context, modelFiles.file, appScope)
        postTranslations = engine?.let {
            PostTranslationStore(
                translator = PostTranslator(it),
                model = modelFiles,
                settings = settingsStore.settings,
                scope = translationScope,
                languageDetector = TextClassifierLanguageDetector(context),
            ).also { store -> observeAppState(context, store) }
        }
        // lite: full から入れ替えたときに残ったモデル (数百 MB)・ダウンロードを消す
        if (engine == null) appScope.launch { modelFiles.delete() }
    }

    /**
     * 裏に回った・メモリが足りないときに翻訳のモデルを解放させ、言語が変わったら訳す先を合わせさせる
     * (Application を作った直後、メインスレッドで呼ぶ)。
     */
    private fun observeAppState(context: Context, translations: PostTranslationStore) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = translations.onForeground()
            override fun onStop(owner: LifecycleOwner) = translations.onBackground()
        })
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                // 裏に回ったこと (UI_HIDDEN) は onStop で扱う。前面での RUNNING_LOW / CRITICAL (Android 13 まで) と、
                // 裏で終わらされそうな BACKGROUND 以上で解放する
                @Suppress("DEPRECATION")
                if (level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    translations.onMemoryPressure()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = translations.onMemoryPressure()
            override fun onConfigurationChanged(newConfig: Configuration) = translations.onConfigurationChanged()
        })
    }
}
