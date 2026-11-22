package es.aria_on_the_planet.quasar.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * アカウントごとの `ViewModelStore` (Activity の ViewModel に置く。回転等の作り直しでは残り、Activity が終わると全部消える)。
 * キーは [AccountScope] の作り直しのキー (アカウントのキー + [ActiveAccount.generation])。
 */
class AccountViewModelStores : ViewModel() {
    private val stores = HashMap<String, ViewModelStore>()

    fun store(key: String): ViewModelStore = stores.getOrPut(key) { ViewModelStore() }

    fun clear(key: String) {
        stores.remove(key)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach(ViewModelStore::clear)
        stores.clear()
    }
}

/**
 * 使っているアカウント [active] ごとに [content] (画面全体) を作り直す。切り替えたら前のアカウントの画面・ViewModel・保存した
 * 状態を捨て、新しいアカウントの画面はタイムラインから始まる。計画と確かめた結果は docs/multi-account.md の「切り替え」。
 *
 * * `key()` で中身を作り直し、アカウントごとの `ViewModelStoreOwner` を渡す (前のアカウントの ViewModel が残らない)。
 *   owner は [stores] (Activity の ViewModel) に置くので、Activity の作り直し (回転等) では ViewModel が残る。消すのは切り替えの
 *   ときだけで、Activity の破棄では消さない (lifecycle が DESTROYED。ライブラリの `rememberViewModelStoreProvider` と同じ判定)。
 * * 保存した状態 (バックスタック・`rememberSaveable`) は外側の `SaveableStateHolder` でアカウントごとに分け、使っていない
 *   アカウントの分は捨てる。捨てないと、Activity が止まっている間に使うアカウントが変わってから復元したとき、前のプロセスで
 *   保存した別のアカウントの状態が残り、そのアカウントに切り替えると古いバックスタックがよみがえる。
 * * 保存のキーはアカウントのキー (String。`hashCode` がプロセスをまたいで変わらない)。トークンの差し替え (同じアカウントでの
 *   ログインし直し) ではキーが同じまま依存が新しくなるので、`key()` には [ActiveAccount.generation] も入れて作り直す
 *   (このプロセスで差し替えた後にプロセスが終わると、保存した状態は復元されずタイムラインから始まる)。
 *
 * @param accountKeys 保存しているアカウントのキー (使っていないものの保存した状態を捨てる)。
 */
@Composable
fun AccountScope(
    active: ActiveAccount?,
    accountKeys: Set<String>,
    stores: AccountViewModelStores,
    content: @Composable (AccountUiContext?) -> Unit,
) {
    val stateKey = active?.key?.value ?: LoggedOutStateKey
    val generation = active?.generation ?: 0
    val stateHolder = rememberSaveableStateHolder()
    // 使っていないアカウント (ログアウトしたものを含む) の保存した状態を捨てる。前のアカウントの分は、ここに来た時点で
    // 前の画面が破棄で保存し終えている
    val seenKeys = remember { HashSet<String>() }
    LaunchedEffect(stateKey) {
        seenKeys += stateKey
        (seenKeys + accountKeys + LoggedOutStateKey - stateKey).forEach(stateHolder::removeState)
    }
    val defaults = LocalViewModelStoreOwner.current as HasDefaultViewModelProviderFactory
    val storeKey = "$stateKey#$generation"
    key(stateKey, generation) {
        val owner = remember { AccountViewModelStoreOwner(stores.store(storeKey), defaults) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(Unit) {
            onDispose {
                // 切り替えのときだけ消す。Activity の作り直し (回転等) では DESTROYED になっているので消さない
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) stores.clear(storeKey)
            }
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            stateHolder.SaveableStateProvider(stateKey) { content(active?.context) }
        }
    }
}

/** ログアウト中の画面 (ログイン) の保存した状態のキー。 */
private const val LoggedOutStateKey = ""

/** アカウントごとの owner。既定の factory・extras は Activity のもの (Application 等) を使う。 */
private class AccountViewModelStoreOwner(
    override val viewModelStore: ViewModelStore,
    private val defaults: HasDefaultViewModelProviderFactory,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory get() = defaults.defaultViewModelProviderFactory
    override val defaultViewModelCreationExtras: CreationExtras get() = defaults.defaultViewModelCreationExtras
}
