package es.aria_on_the_planet.quasar.ui.feed

import androidx.compose.runtime.Immutable
import es.aria_on_the_planet.quasar.data.remote.MastodonApiException
import es.aria_on_the_planet.quasar.data.remote.userMessage

/**
 * 読み込みの失敗。[message] は画面に出す文言 (サーバの `error` の文言等。`userMessage`)。[needsLogin] はトークンが使えない (401) か、
 * サーバがこのアカウントでの利用を断っている (403: 無効・承認待ち・権限の不足等) ので、ログインし直すよう促すか。
 */
@Immutable
data class LoadError(val message: String, val needsLogin: Boolean = false)

fun Exception.toLoadError(): LoadError =
    LoadError(userMessage(), needsLogin = (this as? MastodonApiException)?.code.needsLogin())

/** ログインし直すよう促す HTTP のステータス (401 / 403)。 */
fun Int?.needsLogin(): Boolean = this == 401 || this == 403
