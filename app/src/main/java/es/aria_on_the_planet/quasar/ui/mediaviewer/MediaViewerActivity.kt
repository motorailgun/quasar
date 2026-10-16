package es.aria_on_the_planet.quasar.ui.mediaviewer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.aria_on_the_planet.quasar.QuasarApplication
import es.aria_on_the_planet.quasar.domain.model.MediaType
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.player.createMediaPlayer

/**
 * メディアビューア。[openMedia] で起動する。画面の中身・出入り・システムバー等は [MediaViewerScreen] が持つ。
 *
 * 透過 Activity (Theme.Quasar.MediaViewer) で、上下スワイプで閉じるときに元の画面が透けて見える。
 * そのため下の画面は ON_STOP にならない (インライン再生は開く側で止めている)。
 * manifest で画面サイズの変更 (configChanges) を自前で受ける (PiP 対応時に再生を途切れさせないため)。
 */
class MediaViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as QuasarApplication).container
        val args = intent.getStringExtra(EXTRA_ARGS)?.let(MediaViewerArgs::decode)
        if (args == null || args.items.isEmpty()) {
            finish()
            return
        }
        setContent {
            // ビューア自身は一覧を持たないのでインライン再生は不要
            QuasarAppContent(container, inlinePlayback = false) {
                val viewModel: MediaViewerViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            MediaViewerViewModel(args) { createMediaPlayer(applicationContext, container.okHttpClient) }
                        }
                    },
                )
                MediaViewerScreen(viewModel = viewModel, onDismissed = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_ARGS = "args"

        fun newIntent(context: Context, args: MediaViewerArgs): Intent =
            Intent(context, MediaViewerActivity::class.java).putExtra(EXTRA_ARGS, args.encode())
    }
}

/**
 * 添付メディアのタップ。unknown は元のファイルをブラウザで開き、それ以外はメディアビューアを開く。
 * 出入りのアニメーションはビューア側 (Compose の [DismissibleViewer]) で描くので、Activity の遷移アニメーションは付けない。
 */
fun Context.openMedia(click: MediaClick) {
    if (click.attachment.type == MediaType.Unknown) {
        click.attachment.originalUrl?.let { url ->
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        return
    }
    openMediaViewer(MediaViewerArgs.from(click))
}

/** メディアビューアを開く。ポストの添付以外 (プロフィールのアイコン等) はこちらを直接使う。 */
fun Context.openMediaViewer(args: MediaViewerArgs) {
    startActivity(MediaViewerActivity.newIntent(this, args))
}
