package es.aria_on_the_planet.quasar.ui.post

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
import es.aria_on_the_planet.quasar.ui.mediaviewer.openMedia
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.profile.openProfile
import es.aria_on_the_planet.quasar.ui.QuasarAppContent

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
        // 下書きはタイムラインと共有 (メイン画面を経ずに開かれた場合もここで用意する)
        if (session.accessToken != null) container.draftStore.open(session)
        setContent {
            QuasarAppContent(container) {
                val viewModel: PostViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { PostViewModel(session, statusId, container.statusRepository) }
                    },
                )
                PostScreen(
                    viewModel = viewModel,
                    drafts = container.draftStore,
                    onBack = ::finish,
                    onUserClick = ::openProfile,
                    onPostClick = { post -> if (post.statusId != statusId) openPost(post) },
                    onMediaClick = ::openMedia,
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
