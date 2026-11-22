package es.aria_on_the_planet.quasar.di

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import es.aria_on_the_planet.quasar.data.account.AccountContext
import es.aria_on_the_planet.quasar.data.account.AccountKey
import es.aria_on_the_planet.quasar.data.account.AccountStore
import es.aria_on_the_planet.quasar.data.account.LegacyPlaintextSession
import es.aria_on_the_planet.quasar.data.account.LegacyPlaintextSessionSource
import es.aria_on_the_planet.quasar.data.account.StoredAccount
import es.aria_on_the_planet.quasar.data.account.StoredProfile
import es.aria_on_the_planet.quasar.data.auth.OAuthLogin
import es.aria_on_the_planet.quasar.data.cache.CacheDatabase
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
import es.aria_on_the_planet.quasar.data.settings.AccountSettingsStore
import es.aria_on_the_planet.quasar.data.settings.DeviceSettingsStore
import es.aria_on_the_planet.quasar.data.settings.SettingsStore
import es.aria_on_the_planet.quasar.data.translation.PostTranslator
import es.aria_on_the_planet.quasar.data.translation.TextClassifierLanguageDetector
import es.aria_on_the_planet.quasar.data.translation.TranslationModelFiles
import es.aria_on_the_planet.quasar.data.translation.createTranslationEngine
import es.aria_on_the_planet.quasar.ui.translation.PostTranslationStore
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.account.AccountManager
import es.aria_on_the_planet.quasar.ui.account.AccountUiContext
import es.aria_on_the_planet.quasar.ui.composer.ComposerSession
import es.aria_on_the_planet.quasar.ui.composer.EditTarget
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.OkHttpClient

/**
 * 手動 DI コンテナ。アプリ全体で共有するもの (通信・サーバ単位の Repository・端末共通の設定・翻訳等) を持ち、ログイン中の
 * アカウントの分は [accountManager] ([AccountUiContext] / [AccountContext] をアカウントごとに持つ) が持つ。
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val okHttpClient: OkHttpClient = OkHttpClient()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val api = MastodonApi(okHttpClient, json)

    private val secureStorage = SecureStorage(context)

    /** ログインしているアカウントの一覧と使っているアカウント。最初の起動で以前の保存 (1 つのセッション) を移す。 */
    val accountStore = AccountStore(secureStorage, LegacyPlaintextSessionPrefs(appContext))

    /** アプリ全体のコルーチン (プロセスと同じ寿命)。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** OAuth でのログイン・ログアウト。 */
    val oauthLogin = OAuthLogin(api, secureStorage, accountStore, appScope)

    /** 端末共通の設定。ここで作った時点 (起動時) から読み込み始める。アカウントごとの設定は [AccountContext.settings]。 */
    val deviceSettings: DeviceSettingsStore = SettingsStore.device(context, appScope)

    init {
        // 一覧に無いアカウントのファイル (ログアウトの途中で終わった分) と、アカウントごとに分ける前のキャッシュを消す
        val keys = accountStore.state.value.accounts.mapTo(HashSet()) { it.key.value }
        CacheDatabase.deleteOthers(context, keep = keys)
        SettingsStore.accountNames(context).filter { it !in keys }.forEach { SettingsStore.deleteAccountFiles(context, it) }
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

    /** アカウントごとの設定 (キーごとに 1 つ。DataStore は同じファイルに 1 つしか作れない)。値はそのコルーチンと組。 */
    private val accountSettings = HashMap<AccountKey, Pair<AccountSettingsStore, CoroutineScope>>()

    /**
     * アカウント [key] の設定。無ければ作る。
     *
     * @param migrateLegacy 分ける前の設定のアカウントごとの項目を移すか (ログインしているアカウント。移し終えた後は何もしない)。
     */
    private fun accountSettingsStore(key: AccountKey, migrateLegacy: Boolean): AccountSettingsStore = synchronized(accountSettings) {
        accountSettings.getOrPut(key) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            SettingsStore.account(appContext, key.value, scope, legacy = deviceSettings.takeIf { migrateLegacy }) to scope
        }.first
    }

    /**
     * アカウント [key] ([session]) 1 つ分の依存を作る。[accountManager] が使うほか、ギャラリーが `.invalid` のサーバの
     * 投稿欄・設定を作るのにも使う (使い終わったら [AccountUiContext.close])。キャッシュの DB を開くので、同じキーのものを
     * 同時に 2 つ作らない。
     *
     * @param migrateLegacySettings 分ける前の設定のアカウントごとの項目を移すか ([accountSettingsStore])。
     */
    fun createAccount(key: AccountKey, session: Session, migrateLegacySettings: Boolean = false): AccountUiContext {
        val account = AccountContext(
            key = key,
            session = session,
            database = CacheDatabase.create(appContext, key.value),
            settings = accountSettingsStore(key, migrateLegacySettings),
            api = api,
            okHttpClient = okHttpClient,
            json = json,
            instanceRepository = instanceRepository,
        )
        return AccountUiContext(account) { scope, editing, onPublished -> composerSession(account, scope, editing, onPublished) }
    }

    /**
     * アカウント [key] のファイル (キャッシュの DB・設定) を消す。ログアウトで、そのアカウントの [AccountContext] を閉じた後に呼ぶ。
     */
    fun deleteAccountData(key: AccountKey) {
        val settings = synchronized(accountSettings) { accountSettings.remove(key) }
        // 書き込みを止めてから消す (止めるのは待たない。書きかけで残った書き込みの途中のファイルも消す。それでも後から書かれて
        // 残った分は次の起動で消える。キーは使い回さないので、残っていても他のアカウントが読むことはない)
        settings?.second?.cancel()
        appScope.launch(Dispatchers.IO) {
            CacheDatabase.delete(appContext, key.value)
            SettingsStore.deleteAccountFiles(appContext, key.value)
        }
    }

    /**
     * 保存しているアカウント [stored] の依存。開いたら `fetchMe` を呼び、その結果 (とプロフィールの編集) で一覧の表示用の
     * 情報と ID を埋める (ID の無いアカウント = 以前の保存から移したものもここで埋まる)。
     */
    private fun openStoredAccount(stored: StoredAccount): AccountUiContext =
        createAccount(stored.key, stored.session, migrateLegacySettings = true).also { ui ->
            val account = ui.account
            account.scope.launch {
                account.accountRepository.myAccount().collect { me ->
                    // 書けなくても (Keystore の失敗等) 落とさず、購読も続ける。次の fetchMe でまた書く
                    try {
                        accountStore.updateProfile(stored.key, me.user.inServerId, StoredProfile(me.user))
                    } catch (e: Exception) {
                        Log.w(TAG, "Saving the account profile failed: ${e.javaClass.simpleName}")
                    }
                }
            }
            account.scope.launch {
                try {
                    account.accountRepository.fetchMe()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 通信の失敗等。次に使うとき (投稿欄・プロフィール) にまた取りに行く
                    Log.w(TAG, "fetchMe on open failed: ${e.javaClass.simpleName}")
                }
            }
        }

    /**
     * ログインしているアカウントの依存と、使っているアカウント ([AccountManager.active])。切り替えても依存はログアウトまで持つ。
     * 書きかけの投稿等は Main で動くので、作り直しも Main で行う。
     */
    val accountManager = AccountManager(
        store = accountStore,
        open = ::openStoredAccount,
        deleteData = ::deleteAccountData,
        logoutAccount = oauthLogin::logout,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

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
        defaultVisibilitySetting = account.settings.loaded.map { it.defaultVisibility }.distinctUntilChanged(),
        openUploadSource = ::uploadSource,
        editing = editing,
        prepareUpload = { source, limits ->
            val settings = account.settings.current()
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
                settings = deviceSettings.settings,
                scope = translationScope,
                languageDetector = TextClassifierLanguageDetector(context),
            ).also { store ->
                observeAppState(context, store)
                // 訳・言語の推定の結果は statusId (サーバの中でしか一意でない) がキーなので、切り替えたら捨てる
                translationScope.launch {
                    accountManager.active.map { it?.key }.distinctUntilChanged().drop(1).collect { store.clearResults() }
                }
            }
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

private const val TAG = "AppContainer"

/** 以前の平文の保存 (SharedPreferences `session`)。[AccountStore] が最初の起動で移して消す。 */
private class LegacyPlaintextSessionPrefs(private val context: Context) : LegacyPlaintextSessionSource {
    override fun read(): LegacyPlaintextSession? {
        val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", null) ?: return null
        return LegacyPlaintextSession(serverUrl, prefs.getString("access_token", null))
    }

    override fun delete() {
        context.deleteSharedPreferences(PrefsName)
    }

    private companion object {
        const val PrefsName = "session"
    }
}
