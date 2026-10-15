package es.aria_on_the_planet.quasar.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.aria_on_the_planet.quasar.QuasarApplication
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.post.openPost
import es.aria_on_the_planet.quasar.ui.theme.QuasarTheme

/** アカウントのプロフィール画面。[openProfile] で起動する。 */
class ProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as QuasarApplication).container
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)
        val session = container.sessionStore.session.value
        if (accountId == null || session == null) {
            finish()
            return
        }
        setContent {
            QuasarTheme {
                val viewModel: ProfileViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { ProfileViewModel(session, accountId, container.accountRepository, container.statusRepository) }
                    },
                )
                val uriHandler = LocalUriHandler.current
                ProfileScreen(
                    viewModel = viewModel,
                    onBack = ::finish,
                    // 同じアカウントのポスト (自分の投稿) からは遷移しない
                    onUserClick = { user -> if (user.inServerId != accountId) openProfile(user) },
                    onPostClick = ::openPost,
                    onMediaClick = { media -> runCatching { uriHandler.openUri(media.url) } },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_ACCOUNT_ID = "account_id"

        fun newIntent(context: Context, accountId: String): Intent =
            Intent(context, ProfileActivity::class.java).putExtra(EXTRA_ACCOUNT_ID, accountId)
    }
}

/** [user] のプロフィール画面を開く。 */
fun Context.openProfile(user: User) {
    startActivity(ProfileActivity.newIntent(this, user.inServerId))
}
