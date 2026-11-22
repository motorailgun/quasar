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

    /** 文言に出すときの名前: `@user@host` (表示用の情報がまだ無ければサーバのホスト)。 */
    val label: String get() = fullAcct?.let { "@$it" } ?: host

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
    /** ボットのアカウントか (名前の横の印。足す前の保存は次の `fetchMe` で埋まる)。 */
    val bot: Boolean = false,
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
    bot = user.bot,
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
     * ログインできたアカウントを足して使うアカウントにする。同じアカウント (サーバと [accountId] が同じ) が既にあれば、足さずに
     * トークン・クライアントを差し替える (キーは同じまま)。ログインで足すアカウントは必ず ID を持つ。同じサーバの ID の無い
     * アカウント ([withoutIdOn]) は、呼ぶ前に ID を埋めておく (でないと同じアカウントを見分けられない)。
     *
     * @param newKey 足すときのキー (差し替えなら使わない)。
     */
    fun withLogin(
        serverUrl: String,
        accessToken: String,
        client: OAuthClient?,
        accountId: String,
        profile: StoredProfile?,
        newKey: AccountKey,
    ): LoginResult {
        val existing = accounts.firstOrNull { it.accountId == accountId && sameServer(it.serverUrl, serverUrl) }
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

    /**
     * 保存しているアカウント [key] にログインし直した結果を当てる: 同じキーのままトークン・クライアント (・表示用の情報・ID) を
     * 差し替え、使うアカウントにする (設定・キャッシュ・下書きはキーに結びついているので残る)。
     *
     * ログインし直したのが別のアカウントなら差し替えない: サーバが違う・保存している ID と違う ([ReloginResult.DifferentAccount])、
     * ID の無い (以前の保存から移した) アカウントで、その ID が他に保存しているアカウントのもの ([ReloginResult.AlreadyAdded]。
     * 差し替えると同じアカウントが 2 つになる)。[key] がもう無い (認可の間にログアウトした) なら [ReloginResult.Missing]。
     */
    fun withRelogin(
        key: AccountKey,
        serverUrl: String,
        accessToken: String,
        client: OAuthClient?,
        accountId: String,
        profile: StoredProfile?,
    ): ReloginResult {
        val target = find(key) ?: return ReloginResult.Missing
        if (!sameServer(target.serverUrl, serverUrl)) return ReloginResult.DifferentAccount
        if (target.accountId != null && target.accountId != accountId) return ReloginResult.DifferentAccount
        if (accounts.any { it.key != key && it.accountId == accountId && sameServer(it.serverUrl, serverUrl) }) {
            return ReloginResult.AlreadyAdded
        }
        val updated = target.copy(
            accessToken = accessToken,
            client = client,
            accountId = accountId,
            profile = profile ?: target.profile,
        )
        return ReloginResult.Replaced(
            copy(accounts = accounts.map { if (it.key == key) updated else it }, activeKey = key),
            replaced = target,
        )
    }

    /** [key] を除く。使っていたアカウントなら、残りの先頭を使う (無ければログアウト状態)。 */
    fun without(key: AccountKey): StoredAccounts {
        val remaining = accounts.filterNot { it.key == key }
        val active = if (activeKey == key) remaining.firstOrNull()?.key else activeKey
        return StoredAccounts(remaining, active)
    }

    /** [key] に切り替える。一覧に無いキーなら何もしない。 */
    fun withActive(key: AccountKey): StoredAccounts = if (find(key) == null) this else copy(activeKey = key)

    /** [key] の ID と表示用の情報を `fetchMe` の結果で置き換える。無いキーなら何もしない。 */
    fun withProfile(key: AccountKey, accountId: String, profile: StoredProfile): StoredAccounts =
        copy(accounts = accounts.map { if (it.key == key) it.copy(accountId = accountId, profile = profile) else it })

    /**
     * [serverUrl] でログインする前に ID を確かめておくアカウント: 同じサーバで ID の無いもの (以前の保存から移したものだけ。
     * ログインで足すものは必ず ID を持つ)。ID を埋めておけば [withLogin] が同じアカウントを見分けられ、同じアカウントが
     * 2 つ並ぶことは無い。
     */
    fun withoutIdOn(serverUrl: String): List<StoredAccount> =
        accounts.filter { it.accountId == null && sameServer(it.serverUrl, serverUrl) }
}

/**
 * [StoredAccounts.withLogin] の結果。[replaced] は差し替えた前の値 (古いトークンの無効化用。新しく足したなら `null`)。
 */
data class LoginResult(val accounts: StoredAccounts, val account: StoredAccount, val replaced: StoredAccount?)

/** [StoredAccounts.withRelogin] の結果。 */
sealed interface ReloginResult {
    /** 差し替えた。[replaced] は差し替える前の値 (古いトークンの無効化用)。 */
    data class Replaced(val accounts: StoredAccounts, val replaced: StoredAccount) : ReloginResult

    /** ログインし直したのが別のアカウントだった (サーバか ID が違う)。 */
    data object DifferentAccount : ReloginResult

    /** ID の無いアカウントにログインし直したら、その ID は他に保存しているアカウントのものだった。 */
    data object AlreadyAdded : ReloginResult

    /** ログインし直すアカウントがもう無い。 */
    data object Missing : ReloginResult
}

/** サーバの URL の比較 (末尾の `/` と大文字小文字の違いは同じとみなす)。 */
private fun sameServer(a: String, b: String): Boolean = a.trimEnd('/').equals(b.trimEnd('/'), ignoreCase = true)
