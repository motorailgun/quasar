package es.aria_on_the_planet.quasar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import es.aria_on_the_planet.quasar.ui.navigation.QuasarNavHost

/**
 * アプリの唯一の Activity。画面の行き来は [QuasarNavHost] (Navigation 3)。
 * OAuth の認可からの戻り (`OAuthConfig.RedirectUri`) もここで受ける (manifest で singleTask。認可画面の
 * Custom Tab はこの Activity の上に積まれるので、戻ると閉じて [onNewIntent] に届く)。
 */
class MainActivity : ComponentActivity() {
    private val container get() = (application as QuasarApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 作り直し (回転等) では同じ intent が再び来るので、初回だけ見る
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            QuasarAppContent(container) {
                QuasarNavHost(container, onExit = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.data?.let { container.oauthLogin.handleRedirect(it.toString()) }
    }
}
