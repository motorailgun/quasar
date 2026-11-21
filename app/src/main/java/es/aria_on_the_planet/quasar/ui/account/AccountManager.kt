package es.aria_on_the_planet.quasar.ui.account

import es.aria_on_the_planet.quasar.data.account.AccountKey
import es.aria_on_the_planet.quasar.data.account.AccountStore
import es.aria_on_the_planet.quasar.data.account.StoredAccount
import es.aria_on_the_planet.quasar.data.account.StoredAccounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 使っているアカウント ([ActiveAccount.context]) と、そのアカウントの依存を作ったもの ([generation]) の組。
 *
 * @param generation このプロセスで [context] のキーの依存を作り直した回数 (最初は 0)。トークンの差し替えではキーが同じまま
 *   依存が新しくなるので、画面の作り直しのキーに入れる (`AccountScope`)。
 */
class ActiveAccount(val context: AccountUiContext, val generation: Int) {
    val key: AccountKey get() = context.account.key
}

/**
 * ログインしているアカウントの依存 ([AccountUiContext]) をキーごとに持ち、使っているアカウント ([active]) を出す。
 * 保存 (一覧・使っているアカウント) は [AccountStore] で、ここはその変化を見て依存を作る・閉じる。
 *
 * * 依存は使うときに作り (今は使っているアカウントになったとき)、**切り替えても閉じずにログアウトまで持つ** (書きかけの投稿が
 *   切り替えて戻っても残る)。使っていないアカウントは画面が無いので、通信 (ストリーミング等) はしない。
 * * 一覧から消えた (ログアウト・ログインの前に確かめたらトークンが無効だった) キーは、依存を閉じてから [deleteData] でファイル (キャッシュ・設定) を消す。
 * * トークンが変わった (同じアカウントでログインし直した) キーは、前の依存を閉じてから作り直す (同じキーの DB を 2 つ開かない)。
 *   下書きも前の依存と一緒に閉じるので消える (下書きの永続化は TODO)。
 * * Main で動かす ([scope] は Main)。
 *
 * @param open 保存しているアカウントの依存を作る (`AppContainer`)。
 * @param logout アカウントのログアウト (`OAuthLogin.logout`: 一覧から除いてトークンを無効化)。
 */
class AccountManager(
    private val store: AccountStore,
    private val open: (StoredAccount) -> AccountUiContext,
    private val deleteData: (AccountKey) -> Unit,
    private val logout: (AccountKey) -> Unit,
    scope: CoroutineScope,
) {
    private val contexts = HashMap<AccountKey, AccountUiContext>()
    private val generations = HashMap<AccountKey, Int>()

    /** 最後に見た一覧のキー (一覧から消えたキーを見つける)。 */
    private var knownKeys: Set<AccountKey> = store.state.value.accounts.mapTo(HashSet()) { it.key }

    private val _active = MutableStateFlow(store.state.value.active?.let(::activate))

    /** 使っているアカウント (ログアウト中は `null`)。切り替え・トークンの差し替えで新しい値になる。 */
    val active: StateFlow<ActiveAccount?> = _active.asStateFlow()

    init {
        scope.launch { store.state.collect(::sync) }
    }

    /** [key] に切り替える。画面は `AccountScope` が [active] を見て作り直す。 */
    fun switchTo(key: AccountKey) = store.setActive(key)

    /** 使っているアカウントからログアウトする (残りがあればその先頭に切り替わる)。 */
    fun logoutActive() {
        active.value?.key?.let(logout)
    }

    private fun sync(state: StoredAccounts) {
        // 消えたもの・トークンが変わったものを閉じる。新しいものを開く前に閉じる (同じキーの DB を 2 つ開かない)
        for ((key, context) in contexts.toList()) {
            val stored = state.find(key)
            if (stored == null || stored.session != context.account.session) {
                contexts.remove(key)
                context.close()
            }
        }
        // 一覧から消えたキーのファイルを消す (このプロセスで開いていないものも)
        val keys = state.accounts.mapTo(HashSet()) { it.key }
        (knownKeys - keys).forEach(deleteData)
        knownKeys = keys

        val stored = state.active
        val current = _active.value
        if (stored == null) {
            if (current != null) _active.value = null
            return
        }
        if (current != null && contexts[stored.key] === current.context) return
        _active.value = activate(stored)
    }

    /** [stored] の依存 (無ければ作る) を使っているアカウントにする値。 */
    private fun activate(stored: StoredAccount): ActiveAccount {
        val existing = contexts[stored.key]
        if (existing != null) return ActiveAccount(existing, generations.getValue(stored.key))
        val generation = generations[stored.key]?.plus(1) ?: 0
        generations[stored.key] = generation
        return ActiveAccount(open(stored).also { contexts[stored.key] = it }, generation)
    }
}
