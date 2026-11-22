package es.aria_on_the_planet.quasar

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.ui.account.AccountScope
import es.aria_on_the_planet.quasar.ui.account.AccountViewModelStores
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import es.aria_on_the_planet.quasar.ui.navigation.QuasarNavHost
import es.aria_on_the_planet.quasar.ui.openlink.isOpenLinkShare
import es.aria_on_the_planet.quasar.ui.openlink.readOpenLinkQuery
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * アプリの唯一の Activity。画面の行き来は [QuasarNavHost] (Navigation 3)。使っているアカウントが変わったら
 * [AccountScope] が画面全体を作り直す。
 * OAuth の認可からの戻り (`OAuthConfig.RedirectUri`) もここで受ける (manifest で singleTask。認可画面の
 * Custom Tab はこの Activity の上に積まれるので、戻ると閉じて [onNewIntent] に届く)。
 * 他のアプリから「Quasar で開く」(manifest の別名 `OpenLink`) に共有された URL もここで受け、今の画面の上に開く。
 */
class MainActivity : ComponentActivity() {
    private val container get() = (application as QuasarApplication).container

    /** アカウントごとの ViewModel の置き場 ([AccountScope])。 */
    private val accountStores: AccountViewModelStores by viewModels()

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
                val active by container.accountManager.active.collectAsStateWithLifecycle()
                val accounts by container.accountStore.state.collectAsStateWithLifecycle()
                val accountKeys = remember(accounts) { accounts.accounts.mapTo(HashSet()) { it.key.value } }
                // 使っているアカウントが変わったら画面を作り直す (タイムラインから。前のアカウントの画面・ViewModel は捨てる)
                AccountScope(active, accountKeys, accountStores) { account ->
                    QuasarNavHost(container, account, externalLinkFlow, onExit = ::finish)
                }
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
