package es.aria_on_the_planet.quasar.data.account

import es.aria_on_the_planet.quasar.data.auth.OAuthClient
import es.aria_on_the_planet.quasar.data.session.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 以前の平文の保存 (SharedPreferences `session`)。 */
data class LegacyPlaintextSession(val serverUrl: String, val accessToken: String?) {
    override fun toString(): String = "LegacyPlaintextSession(serverUrl=$serverUrl)"
}

/** 以前の平文の保存の読み出しと削除 (Context が要るので外から渡す)。 */
interface LegacyPlaintextSessionSource {
    fun read(): LegacyPlaintextSession?

    fun delete()
}

/**
 * ログインしているアカウントの一覧と使っているアカウント ([StoredAccounts]) を [storage] (SecureStorage = Keystore で暗号化)
 * に保持する。今は 1 つずつ使う (ログインで足して使う・ログアウトで使っているものを除く)。
 *
 * 最初の起動では以前の保存 (1 つのセッション) を 1 つ目のアカウントに移す ([load])。
 *
 * @param newKey 足すアカウントのキー (テストで決まった値にする)。
 */
class AccountStore(
    private val storage: KeyValueStore,
    legacyPlaintext: LegacyPlaintextSessionSource? = null,
    private val newKey: () -> AccountKey = AccountKey::random,
) {
    private val _state = MutableStateFlow(load(legacyPlaintext))

    /** 全体 (一覧 + 使っているアカウント)。 */
    val state: StateFlow<StoredAccounts> = _state.asStateFlow()

    private val _active = MutableStateFlow(_state.value.active)

    /** 使っているアカウント (ログアウト中は `null`)。表示用の情報が変わったときも流れる。 */
    val active: StateFlow<StoredAccount?> = _active.asStateFlow()

    /** ログインできたアカウントを足して (同じアカウントならトークンを差し替えて) 使うアカウントにする。 */
    fun addLogin(
        serverUrl: String,
        accessToken: String,
        client: OAuthClient?,
        accountId: String?,
        profile: StoredProfile?,
    ): LoginResult = synchronized(this) {
        val result = _state.value.withLogin(serverUrl, accessToken, client, accountId, profile, newKey())
        save(result.accounts)
        result
    }

    /** [key] を除いて、除いたものを返す。使っていたアカウントなら残りの先頭に切り替わる (無ければログアウト状態)。 */
    fun remove(key: AccountKey): StoredAccount? = synchronized(this) {
        val removed = _state.value.find(key) ?: return null
        save(_state.value.without(key))
        removed
    }

    /** `fetchMe` の結果で [key] の ID・表示用の情報を更新する。変わっていなければ書かない。 */
    fun updateProfile(key: AccountKey, accountId: String, profile: StoredProfile) {
        synchronized(this) {
            val current = _state.value.find(key) ?: return
            if (current.accountId == accountId && current.profile == profile) return
            save(_state.value.withProfile(key, accountId, profile))
        }
    }

    private fun save(accounts: StoredAccounts) {
        storage.put(AccountsKey, json.encodeToString(StoredAccounts.serializer(), accounts))
        _state.value = accounts
        _active.value = accounts.active
    }

    /**
     * 保存を読む。無ければ以前の保存から移す: SecureStorage の `session` (1 つのセッション) → さらに前の平文の保存。
     * 移したら新しい形で書いてから古いものを消す (途中で終わっても次の起動でやり直せる。書けていれば新しい形を読む)。
     */
    private fun load(legacyPlaintext: LegacyPlaintextSessionSource?): StoredAccounts {
        storage.get(AccountsKey)?.let { stored ->
            decodeAccounts(stored)?.let { return it }
        }
        val legacySession = storage.get(LegacySessionKey)
        val plaintext = legacyPlaintext?.read()
        val migrated = migrateLegacySession(legacySession, plaintext, newKey)
        if (migrated != null) storage.put(AccountsKey, json.encodeToString(StoredAccounts.serializer(), migrated))
        if (legacySession != null) storage.remove(LegacySessionKey)
        if (plaintext != null) legacyPlaintext.delete()
        return migrated ?: StoredAccounts()
    }

    companion object {
        private const val AccountsKey = "accounts"
        private const val LegacySessionKey = "session"

        private val json = Json { ignoreUnknownKeys = true }

        /** 読めない (形の変わった) 保存は無かったことにする。 */
        internal fun decodeAccounts(stored: String): StoredAccounts? =
            runCatching { json.decodeFromString(StoredAccounts.serializer(), stored) }.getOrNull()
                // 使っているアカウントが一覧に無ければ使わない
                ?.let { if (it.activeKey != null && it.active == null) it.copy(activeKey = null) else it }

        /**
         * 以前の保存 ([legacySession] = SecureStorage の `session` の JSON、無ければ [legacyPlaintext]) を、使っている
         * 1 つ目のアカウント (キーは [newKey]) にする。トークンの無いもの (最初期の未ログインでの利用) はログアウト状態 (空の一覧)。
         * 移すものが無ければ `null`。
         */
        internal fun migrateLegacySession(
            legacySession: String?,
            legacyPlaintext: LegacyPlaintextSession?,
            newKey: () -> AccountKey,
        ): StoredAccounts? {
            val legacy = legacySession?.let { runCatching { json.decodeFromString(LegacyStoredSession.serializer(), it) }.getOrNull() }
                ?: legacyPlaintext?.let { LegacyStoredSession(it.serverUrl, it.accessToken) }
                ?: return null
            val token = legacy.accessToken ?: return StoredAccounts()
            val key = newKey()
            val account = StoredAccount(key, legacy.serverUrl, token, legacy.client)
            return StoredAccounts(listOf(account), key)
        }
    }
}

/** 以前の SecureStorage の `session` の形 (`SessionStore` のもの)。 */
@Serializable
private data class LegacyStoredSession(
    val serverUrl: String,
    val accessToken: String?,
    val client: OAuthClient? = null,
)
