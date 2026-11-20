package es.aria_on_the_planet.quasar.data.session

/**
 * ログイン中のアカウントのサーバとアクセストークン (トークンの無いセッションは無い)。保存は
 * `data/account/AccountStore` (アカウントの一覧)。
 */
data class Session(
    val serverUrl: String,
    val accessToken: String,
) {
    /** ログ・例外に出しても漏れないよう、トークンは出さない。 */
    override fun toString(): String = "Session(serverUrl=$serverUrl)"
}
