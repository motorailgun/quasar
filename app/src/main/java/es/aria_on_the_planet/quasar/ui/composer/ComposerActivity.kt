package es.aria_on_the_planet.quasar.ui.composer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.QuasarApplication
import es.aria_on_the_planet.quasar.ui.QuasarAppContent

/**
 * 単独の投稿画面。[openComposer] で起動する。要ログイン。
 * 下書きはクイック投稿バーと共有する ([DraftStore])。メイン画面・ポスト画面ではバーから同じ画面内でフル画面に
 * 広げるので、これはバーの無い画面 (プロフィールからの返信等) 用。返信先は開く前に下書きへ設定しておく。
 * 閉じても下書きは残る (バーと同じ。共有の下書きなので、ここで破棄するとバーの書きかけまで消える)。
 */
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
        container.draftStore.open(session)
        setContent {
            QuasarAppContent(container, inlinePlayback = false) {
                val draft by container.draftStore.current.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    container.draftStore.published.collect { finish() }
                }
                draft?.let {
                    ComposerScreen(
                        session = it,
                        onClose = ::finish,
                        onFinish = {
                            container.draftStore.finish()
                            finish()
                        },
                        keepDraftOnClose = true,
                    )
                }
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
