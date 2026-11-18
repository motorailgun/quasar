package es.aria_on_the_planet.quasar.data.account

import es.aria_on_the_planet.quasar.data.auth.OAuthClient
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.User
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 保存しているアカウント 1 つ ([AccountStore])。
 *
 * @param client トークンを発行したクライアント (ログアウト時の無効化用。手入力のトークンの頃から移したものは無い)。
 * @param accountId サーバ内のアカウント ID。同じアカウントでのログインし直しを見分ける。以前の保存から移したものは
 *   最初の `fetchMe` まで無い。
 * @param profile ドロワー等に出す情報 (`fetchMe`・`myAccount` の結果)。通信なしで一覧を描くため。
 */
@Serializable
data class StoredAccount(
    val key: AccountKey,
    val serverUrl: String,
    val accessToken: String,
    val client: OAuthClient? = null,
    val accountId: String? = null,
    val profile: StoredProfile? = null,
) {
    val session: Session get() = Session(serverUrl, accessToken)

    /** サーバのホスト。 */
    val host: String get() = serverUrl.toHttpUrlOrNull()?.host ?: serverUrl

    /** `user@host` (表示用の情報がまだ無ければ `null`)。 */
    val fullAcct: String? get() = profile?.acct?.let { if ('@' in it) it else "$it@$host" }

    /** ログ・例外に出しても漏れないよう、トークン・クライアントは出さない。 */
    override fun toString(): String = "StoredAccount(key=$key, serverUrl=$serverUrl, accountId=$accountId)"
}

/** アカウントの表示用の情報。 */
@Serializable
data class StoredProfile(
    /** `acct` (ログイン中のアカウントなのでドメインは付かない)。 */
    val acct: String,
    val displayName: String,
    val avatarUrl: String,
    val avatarStaticUrl: String,
    val emojis: List<StoredEmoji> = emptyList(),
)

@Serializable
data class StoredEmoji(val shortcode: String, val url: String, val staticUrl: String)

/** [user] (`fetchMe` の結果) の表示用の情報。 */
fun StoredProfile(user: User): StoredProfile = StoredProfile(
    acct = user.screenName,
    displayName = user.displayName,
    avatarUrl = user.iconUrl,
    avatarStaticUrl = user.iconStaticUrl,
    emojis = user.emojis.map { StoredEmoji(it.shortcode, it.url, it.staticUrl) },
)

val StoredEmoji.customEmoji: CustomEmoji get() = CustomEmoji(shortcode, url, staticUrl)

/**
 * 保存の中身全体: アカウントの一覧と、使っているアカウント。SecureStorage の 1 つの項目に JSON で置く
 * (一覧と使っているアカウントを一緒に書き換えるため)。変更は純粋関数で、[AccountStore] が保存する。
 */
@Serializable
data class StoredAccounts(
    val accounts: List<StoredAccount> = emptyList(),
    val activeKey: AccountKey? = null,
) {
    /** 使っているアカウント。無い (ログアウト中) なら `null`。 */
    val active: StoredAccount? get() = activeKey?.let(::find)

    fun find(key: AccountKey): StoredAccount? = accounts.firstOrNull { it.key == key }

    /**
     * ログインできたアカウントを足して使うアカウントにする。同じアカウント (サーバと [accountId] が同じ。[accountId] が
     * どちらかに無ければ別のものとして扱う) が既にあれば、足さずにトークン・クライアントを差し替える (キーは同じまま)。
     *
     * @param newKey 足すときのキー (差し替えなら使わない)。
     */
    fun withLogin(
        serverUrl: String,
        accessToken: String,
        client: OAuthClient?,
        accountId: String?,
        profile: StoredProfile?,
        newKey: AccountKey,
    ): LoginResult {
        val existing = accountId?.let { id ->
            accounts.firstOrNull { it.accountId == id && sameServer(it.serverUrl, serverUrl) }
        }
        if (existing != null) {
            val updated = existing.copy(
                accessToken = accessToken,
                client = client,
                profile = profile ?: existing.profile,
            )
            return LoginResult(
                copy(accounts = accounts.map { if (it.key == existing.key) updated else it }, activeKey = existing.key),
                account = updated,
                replaced = existing,
            )
        }
        val added = StoredAccount(newKey, serverUrl, accessToken, client, accountId, profile)
        return LoginResult(copy(accounts = accounts + added, activeKey = newKey), account = added, replaced = null)
    }

    /** [key] を除く。使っていたアカウントなら、残りの先頭を使う (無ければログアウト状態)。 */
    fun without(key: AccountKey): StoredAccounts {
        val remaining = accounts.filterNot { it.key == key }
        val active = if (activeKey == key) remaining.firstOrNull()?.key else activeKey
        return StoredAccounts(remaining, active)
    }

    /** [key] の ID と表示用の情報を `fetchMe` の結果で置き換える。無いキーなら何もしない。 */
    fun withProfile(key: AccountKey, accountId: String, profile: StoredProfile): StoredAccounts =
        copy(accounts = accounts.map { if (it.key == key) it.copy(accountId = accountId, profile = profile) else it })
}

/**
 * [StoredAccounts.withLogin] の結果。[replaced] は差し替えた前の値 (古いトークンの無効化用。新しく足したなら `null`)。
 */
data class LoginResult(val accounts: StoredAccounts, val account: StoredAccount, val replaced: StoredAccount?)

/** サーバの URL の比較 (末尾の `/` と大文字小文字の違いは同じとみなす)。 */
private fun sameServer(a: String, b: String): Boolean = a.trimEnd('/').equals(b.trimEnd('/'), ignoreCase = true)
