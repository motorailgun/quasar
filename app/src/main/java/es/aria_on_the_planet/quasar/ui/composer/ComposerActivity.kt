package es.aria_on_the_planet.quasar.ui.composer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.aria_on_the_planet.quasar.QuasarApplication
import es.aria_on_the_planet.quasar.ui.QuasarAppContent

/** 投稿画面。[openComposer] で起動する。要ログイン。 */
class ComposerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as QuasarApplication).container
        val session = container.sessionStore.session.value
        if (session?.accessToken == null) {
            finish()
            return
        }
        setContent {
            QuasarAppContent(container, inlinePlayback = false) {
                val viewModel: ComposerViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            ComposerViewModel(
                                session,
                                container.accountRepository,
                                container.instanceRepository,
                                container.emojiRepository,
                                container.statusRepository,
                            )
                        }
                    },
                )
                ComposerScreen(viewModel = viewModel, onClose = ::finish)
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, ComposerActivity::class.java)
    }
}

/** 投稿画面を開く。 */
fun Context.openComposer() {
    startActivity(ComposerActivity.newIntent(this))
}
