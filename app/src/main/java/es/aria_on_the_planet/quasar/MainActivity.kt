package es.aria_on_the_planet.quasar

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import es.aria_on_the_planet.quasar.ui.navigation.QuasarNavHost
import es.aria_on_the_planet.quasar.ui.openlink.isOpenLinkShare
import es.aria_on_the_planet.quasar.ui.openlink.readOpenLinkQuery
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * アプリの唯一の Activity。画面の行き来は [QuasarNavHost] (Navigation 3)。
 * OAuth の認可からの戻り (`OAuthConfig.RedirectUri`) もここで受ける (manifest で singleTask。認可画面の
 * Custom Tab はこの Activity の上に積まれるので、戻ると閉じて [onNewIntent] に届く)。
 * 他のアプリから「Quasar で開く」(manifest の別名 `OpenLink`) に共有された URL もここで受け、今の画面の上に開く。
 */
class MainActivity : ComponentActivity() {
    private val container get() = (application as QuasarApplication).container

    /** 共有された URL・`@user@domain`。画面ができる前 (起動時) に受けた分は溜めておく。 */
    private val externalLinks = Channel<String>(Channel.BUFFERED)
    private val externalLinkFlow = externalLinks.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 作り直し (回転等) では同じ intent が再び来るので、初回だけ見る
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            QuasarAppContent(container) {
                QuasarNavHost(container, externalLinkFlow, onExit = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.isOpenLinkShare()) {
            val query = intent.readOpenLinkQuery()
            if (query != null) {
                externalLinks.trySend(query)
            } else {
                Toast.makeText(this, R.string.open_link_no_link, Toast.LENGTH_SHORT).show()
            }
            return
        }
        intent.data?.let { container.oauthLogin.handleRedirect(it.toString()) }
    }
}
