package es.aria_on_the_planet.quasar.di

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import es.aria_on_the_planet.quasar.data.account.AccountContext
import es.aria_on_the_planet.quasar.data.auth.OAuthLogin
import es.aria_on_the_planet.quasar.data.cache.CacheDatabase
import es.aria_on_the_planet.quasar.data.cache.EntityCache
import es.aria_on_the_planet.quasar.data.licenses.LicenseRepository
import es.aria_on_the_planet.quasar.data.media.ContentUriUploadSource
import es.aria_on_the_planet.quasar.data.media.ImagePrepareOptions
import es.aria_on_the_planet.quasar.data.media.ImageReencoder
import es.aria_on_the_planet.quasar.data.media.toImageUploadLimits
import es.aria_on_the_planet.quasar.data.media.MediaDownloader
import es.aria_on_the_planet.quasar.data.media.SystemMediaDownloader
import es.aria_on_the_planet.quasar.data.media.UploadSource
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.repository.EmojiRepository
import es.aria_on_the_planet.quasar.data.repository.InstanceRepository
import es.aria_on_the_planet.quasar.data.session.SecureStorage
import es.aria_on_the_planet.quasar.data.session.SessionStore
import es.aria_on_the_planet.quasar.data.settings.SettingsStore
import es.aria_on_the_planet.quasar.data.translation.PostTranslator
import es.aria_on_the_planet.quasar.data.translation.TextClassifierLanguageDetector
import es.aria_on_the_planet.quasar.data.translation.TranslationModelFiles
import es.aria_on_the_planet.quasar.data.translation.createTranslationEngine
import es.aria_on_the_planet.quasar.ui.translation.PostTranslationStore
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.account.AccountUiContext
import es.aria_on_the_planet.quasar.ui.composer.ComposerSession
import es.aria_on_the_planet.quasar.ui.composer.EditTarget
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * 手動 DI コンテナ。アプリ全体で共有するもの (通信・サーバ単位の Repository・設定・翻訳等) を持ち、ログイン中の
 * アカウントの分は [currentAccount] ([AccountUiContext] / [AccountContext]) が持つ。
 */
class AppContainer(context: Context) {
    val okHttpClient: OkHttpClient = OkHttpClient()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** API の結果のキャッシュ (Room)。今はアプリ全体で 1 つで、各 [AccountContext] に渡す。 */
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

    /** 設定の「ライセンス」の中身。 */
    val licenseRepository = LicenseRepository(context)

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

    /**
     * [session] のアカウント 1 つ分の依存を作る。[currentAccount] が使うほか、ギャラリーが `.invalid` のサーバの
     * 投稿欄を作るのにも使う (使い終わったら [AccountUiContext.close])。
     */
    fun createAccount(session: Session): AccountUiContext = AccountUiContext(
        AccountContext(
            session = session,
            cache = cache,
            api = api,
            okHttpClient = okHttpClient,
            json = json,
            instanceRepository = instanceRepository,
        ),
        createComposer = ::composerSession,
    )

    private val _currentAccount = MutableStateFlow(sessionStore.session.value?.let(::createAccount))

    /**
     * ログイン中のアカウント ([SessionStore.session] から作る。ログアウト中は `null`)。セッションが変わったら作り直し、
     * 前のものは閉じる (下書きのアップロード等を止める)。
     */
    val currentAccount: StateFlow<AccountUiContext?> = _currentAccount.asStateFlow()

    init {
        // 書きかけの投稿等は Main で動くので、作り直しも Main で行う
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            sessionStore.session.collect { session ->
                val previous = _currentAccount.value
                if (previous?.account?.session == session) return@collect
                _currentAccount.value = session?.let(::createAccount)
                previous?.close()
            }
        }
    }

    /** [account] の投稿欄 1 つ分 ([AccountUiContext.composerSession])。 */
    private fun composerSession(
        account: AccountContext,
        scope: CoroutineScope,
        editing: EditTarget?,
        onPublished: (Post) -> Unit,
    ) = ComposerSession(
        account = account,
        scope = scope,
        instanceRepository = instanceRepository,
        emojiRepository = emojiRepository,
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
