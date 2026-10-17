package es.aria_on_the_planet.quasar.ui.hashtag

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
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import es.aria_on_the_planet.quasar.ui.composer.openComposer
import es.aria_on_the_planet.quasar.ui.mediaviewer.openMedia
import es.aria_on_the_planet.quasar.ui.post.openPost
import es.aria_on_the_planet.quasar.ui.profile.openProfile

/** ハッシュタグのタイムライン。[openHashtag] で起動する。 */
class HashtagActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as QuasarApplication).container
        val hashtag = intent.getStringExtra(EXTRA_HASHTAG)
        val session = container.sessionStore.session.value
        if (hashtag == null || session == null) {
            finish()
            return
        }
        setContent {
            QuasarAppContent(container) {
                val viewModel: HashtagViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            HashtagViewModel(session, hashtag, container.timelineRepository, container.statusRepository)
                        }
                    },
                )
                HashtagScreen(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onUserClick = ::openProfile,
                    onPostClick = ::openPost,
                    onMediaClick = ::openMedia,
                    // 同じハッシュタグからは遷移しない
                    onHashtagClick = { tag -> if (!tag.equals(hashtag, ignoreCase = true)) openHashtag(tag) },
                    // クイック投稿バーが無いので、返信は単独の投稿画面で書く (下書きは共有)
                    onReply = { post: Post ->
                        container.draftStore.open(session).startReply(post)
                        openComposer()
                    }.takeIf { viewModel.canInteract },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_HASHTAG = "hashtag"

        fun newIntent(context: Context, hashtag: String): Intent =
            Intent(context, HashtagActivity::class.java).putExtra(EXTRA_HASHTAG, hashtag)
    }
}

/** ハッシュタグ [hashtag] (`#` 無し) のタイムラインを開く。 */
fun Context.openHashtag(hashtag: String) {
    startActivity(HashtagActivity.newIntent(this, hashtag))
}
