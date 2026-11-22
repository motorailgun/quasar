package es.aria_on_the_planet.quasar.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import es.aria_on_the_planet.quasar.QuasarApplication
import es.aria_on_the_planet.quasar.data.account.AccountKey
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.ui.account.AccountUiContext

/** ギャラリーのアカウントのキー。アカウントの一覧には無いので、ファイル (キャッシュ・設定) は次の起動で消える。 */
private val GalleryAccountKey = AccountKey("gallery")

/**
 * ギャラリー用のアカウント (サーバは `.invalid` なので通信はすべて失敗する)。投稿欄 (quick-post-bar) とアカウントごとの
 * 設定 (timeline-settings) に使う。本物のアカウントのキャッシュ・設定には触れない。画面を離れたら閉じる。
 */
@Composable
internal fun rememberGalleryAccount(): AccountUiContext {
    val context = LocalContext.current
    val account = remember {
        val container = (context.applicationContext as QuasarApplication).container
        container.createAccount(GalleryAccountKey, Session(serverUrl = "https://gallery.invalid", accessToken = "gallery"))
    }
    DisposableEffect(account) {
        onDispose { account.close() }
    }
    return account
}
