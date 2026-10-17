package es.aria_on_the_planet.quasar.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import es.aria_on_the_planet.quasar.domain.model.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * アプリの設定 (Preferences DataStore)。読み込みは非同期なので、[settings] は読み込み前 `null`。
 * 起動時 ([scope] の作成時) から読み始めるので、普通は最初の画面の描画までに読み終わる。
 *
 * * UI は `QuasarAppContent` が読み込みを待ってから中身を組み立てる (既定値で一瞬描いてから切り替わることはない)。
 * * UI 以外 (ストリーミング・下書き・通知の取得) は [current] / [loaded] で読み込みを待つ。
 */
class SettingsStore(context: Context, private val scope: CoroutineScope) {
    private val dataStore = PreferenceDataStoreFactory.create(
        // 壊れていたら既定値からやり直す (設定が消えるだけで、アプリは動き続ける)
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        // ファイルの読み書きは IO スレッド (DataStore の推奨)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.applicationContext.preferencesDataStoreFile("settings") },
    )

    val settings: StateFlow<AppSettings?> = dataStore.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.w(TAG, "Failed to read settings", e)
            emit(emptyPreferences())
        }
        .map { it.toAppSettings() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** 読み込み後の設定の流れ (`null` を飛ばす)。 */
    val loaded = settings.filterNotNull()

    /** 読み込みを待って、今の設定を返す。 */
    suspend fun current(): AppSettings = loaded.first()

    /** 設定を変える。変わった項目だけが保存される。 */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.updateData { prefs ->
            val before = prefs.toAppSettings()
            prefs.toMutablePreferences().also { writeSettingsChanges(it, before, transform(before)) }
        }
    }

    /** [update] を待たずに行う (設定画面の操作用。画面を閉じても書き込みは止まらない)。 */
    fun launchUpdate(transform: (AppSettings) -> AppSettings) {
        scope.launch { update(transform) }
    }

    private companion object {
        const val TAG = "SettingsStore"
    }
}
