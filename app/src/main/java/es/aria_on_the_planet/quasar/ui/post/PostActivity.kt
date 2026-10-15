package es.aria_on_the_planet.quasar.ui.post

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
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.profile.openProfile
import es.aria_on_the_planet.quasar.ui.theme.QuasarTheme

/** ポスト 1 件とそのスレッド (祖先・リプライツリー) の画面。[openPost] で起動する。 */
class PostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as QuasarApplication).container
        val statusId = intent.getStringExtra(EXTRA_STATUS_ID)
        val session = container.sessionStore.session.value
        if (statusId == null || session == null) {
            finish()
            return
        }
        setContent {
            QuasarTheme {
                val viewModel: PostViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { PostViewModel(session, statusId, container.statusRepository) }
                    },
                )
                val uriHandler = LocalUriHandler.current
                PostScreen(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onUserClick = ::openProfile,
                    onPostClick = { post -> if (post.statusId != statusId) openPost(post) },
                    onMediaClick = { media -> runCatching { uriHandler.openUri(media.url) } },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_STATUS_ID = "status_id"

        fun newIntent(context: Context, statusId: String): Intent =
            Intent(context, PostActivity::class.java).putExtra(EXTRA_STATUS_ID, statusId)
    }
}

/** [post] の詳細画面を開く。ブーストの場合は中身のポストを開く。 */
fun Context.openPost(post: Post) {
    startActivity(PostActivity.newIntent(this, post.statusId))
}
