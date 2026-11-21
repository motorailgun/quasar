package es.aria_on_the_planet.quasar.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import es.aria_on_the_planet.quasar.domain.model.settings.AccountSettings
import es.aria_on_the_planet.quasar.domain.model.settings.DeviceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** 端末共通の設定 (今の `settings` のファイル)。 */
typealias DeviceSettingsStore = SettingsStore<DeviceSettings>

/** アカウント 1 つ分の設定 (`settings_<AccountKey>` のファイル)。 */
typealias AccountSettingsStore = SettingsStore<AccountSettings>

/**
 * 設定 (Preferences DataStore のファイル 1 つ)。読み込みは非同期なので、[settings] は読み込み前 `null`。
 * 作った時点から読み始めるので、普通は最初の画面の描画までに読み終わる。
 *
 * * UI は `QuasarAppContent` が端末共通の設定の読み込みを待ってから中身を組み立てる (既定値で一瞬描いてから切り替わることはない)。
 * * UI 以外 (ストリーミング・下書き・通知の取得) は [current] / [loaded] で読み込みを待つ。
 * * DataStore は 1 つのファイルにプロセスで 1 つしか作れない。同じファイルのものを作り直さないよう、アカウントごとのものは
 *   `AppContainer` がキーごとに 1 つ持つ。
 *
 * @param scope 読み書きのコルーチン。止めると以後は読み書きしない (ログアウトでファイルを消す前に止める)。
 */
class SettingsStore<S> private constructor(
    internal val dataStore: DataStore<Preferences>,
    private val schema: SettingsSchema<S>,
    private val scope: CoroutineScope,
) {
    val settings: StateFlow<S?> = dataStore.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.w(TAG, "Failed to read settings", e)
            emit(emptyPreferences())
        }
        .map { schema.read(it) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** 読み込み後の設定の流れ (`null` を飛ばす)。 */
    val loaded = settings.filterNotNull()

    /** 読み込みを待って、今の設定を返す。 */
    suspend fun current(): S = loaded.first()

    /** 設定を変える。変わった項目だけが保存される。 */
    suspend fun update(transform: (S) -> S) {
        dataStore.updateData { prefs ->
            val before = schema.read(prefs)
            prefs.toMutablePreferences().also { schema.writeChanges(it, before, transform(before)) }
        }
    }

    /** [update] を待たずに行う (設定画面の操作用。画面を閉じても書き込みは止まらない)。 */
    fun launchUpdate(transform: (S) -> S) {
        scope.launch { update(transform) }
    }

    companion object {
        private const val TAG = "SettingsStore"
        private const val DeviceFileName = "settings"
        private const val AccountFilePrefix = "settings_"

        /** 端末共通の設定。[scope] はアプリ全体のもの。 */
        fun device(context: Context, scope: CoroutineScope): DeviceSettingsStore =
            SettingsStore(open(context, DeviceFileName, emptyList(), scope), DeviceSettingsSchema, scope)

        /**
         * アカウント [name] (`AccountKey`) の設定。[scope] はこのアカウントのもの (ログアウトで止める)。
         *
         * @param legacy 分ける前の設定 (= [device]) からアカウントごとの項目を移すか (以前の保存から移したアカウント・
         *   ログインした最初のアカウント)。移したら端末共通のファイルからは消す。
         */
        fun account(context: Context, name: String, scope: CoroutineScope, legacy: DeviceSettingsStore?): AccountSettingsStore {
            val migrations = listOfNotNull(legacy?.let { LegacyAccountSettingsMigration(it.dataStore) })
            return SettingsStore(open(context, AccountFilePrefix + name, migrations, scope), AccountSettingsSchema, scope)
        }

        /**
         * DataStore が書き込みの途中に使うファイルの名前の末尾 (`<ファイル>.tmp` に書いてから置き換える。DataStore 1.2.1 の
         * `FileStorageConnection`)。書き込みの途中で止めると残る。
         */
        private const val ScratchSuffix = ".tmp"

        /** アカウント [name] の設定のファイル。 */
        private fun accountFile(context: Context, name: String): File =
            context.applicationContext.preferencesDataStoreFile(AccountFilePrefix + name)

        /** アカウント [name] の設定のファイルと、書き込みの途中のファイルを消す (ログアウト・起動時の後始末)。 */
        fun deleteAccountFiles(context: Context, name: String) {
            val file = accountFile(context, name)
            file.delete()
            File(file.path + ScratchSuffix).delete()
        }

        /**
         * 保存してあるアカウントごとの設定の名前 (`AccountKey`)。書き込みの途中のファイルだけが残っているものも含む。
         * 残ったもの (ログアウトの途中で終わった等) を消すのに使う。
         */
        fun accountNames(context: Context): List<String> {
            val directory = accountFile(context, "x").parentFile ?: return emptyList()
            val suffix = ".preferences_pb"
            return directory.listFiles().orEmpty().map { it.name }
                .filter { it.startsWith(AccountFilePrefix) }
                .mapNotNull { name ->
                    when {
                        name.endsWith(suffix) -> name.removeSuffix(suffix)
                        name.endsWith(suffix + ScratchSuffix) -> name.removeSuffix(suffix + ScratchSuffix)
                        else -> null
                    }?.removePrefix(AccountFilePrefix)
                }
                .distinct()
        }

        private fun open(
            context: Context,
            fileName: String,
            migrations: List<DataMigration<Preferences>>,
            scope: CoroutineScope,
        ) =
            PreferenceDataStoreFactory.create(
                // 壊れていたら既定値からやり直す (設定が消えるだけで、アプリは動き続ける)
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                migrations = migrations,
                // ファイルの読み書きは IO スレッド (DataStore の推奨)。[scope] を止めると一緒に止まる
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob(scope.coroutineContext[Job])),
                produceFile = { context.applicationContext.preferencesDataStoreFile(fileName) },
            )
    }
}

/**
 * 分ける前の設定 (1 つのファイル。今は端末共通のファイル [device]) に残っているアカウントごとの項目を、このアカウントの
 * ファイルへ移す。DataStore の移行の仕組みで、写す ([migrate]) → 書けたら元から消す ([cleanUp])。途中で終わっても
 * 次に開いたときにやり直し、写した項目は上書きしない。移し終えると [shouldMigrate] が偽になるので、後から足した
 * アカウントは既定値から始まる。
 */
private class LegacyAccountSettingsMigration(private val device: DataStore<Preferences>) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = device.data.first().hasAccountSettings()

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.withLegacyAccountSettings(device.data.first())

    override suspend fun cleanUp() {
        device.updateData { it.withoutAccountSettings() }
    }
}
