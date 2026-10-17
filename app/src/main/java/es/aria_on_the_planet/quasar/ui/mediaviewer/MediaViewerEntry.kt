package es.aria_on_the_planet.quasar.ui.mediaviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import es.aria_on_the_planet.quasar.di.AppContainer
import es.aria_on_the_planet.quasar.domain.model.MediaType
import es.aria_on_the_planet.quasar.domain.model.mediaSaveTarget
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.player.createMediaPlayer
import es.aria_on_the_planet.quasar.ui.navigation.OverlaySceneStrategy
import es.aria_on_the_planet.quasar.ui.navigation.Route

/**
 * メディアビューアの画面 (下の画面に重ねる。`OverlaySceneStrategy`)。出入りのアニメーションは [MediaViewerScreen]
 * (`DismissibleViewer`) が描き、閉じ終わったら [onBack]。アプリ本体とデバッグ用ギャラリーで共通。
 */
fun EntryProviderScope<NavKey>.mediaViewerEntry(container: AppContainer, onBack: () -> Unit) {
    entry<Route.MediaViewer>(metadata = OverlaySceneStrategy.overlay()) { key ->
        val context = LocalContext.current.applicationContext
        val viewModel: MediaViewerViewModel = viewModel(
            factory = viewModelFactory {
                initializer { MediaViewerViewModel(key.args) { createMediaPlayer(context, container.okHttpClient) } }
            },
        )
        MediaViewerScreen(
            viewModel = viewModel,
            onDismissed = onBack,
            onSave = { media -> mediaSaveTarget(media)?.takeIf { container.mediaDownloader.save(it) } },
        )
    }
}

/**
 * 添付メディアのタップ。unknown は元のファイルをブラウザで開き、それ以外は [openViewer] (メディアビューア)。
 * 開く側の画面のインライン再生は、呼び出し元 (`MediaAttachments`) が止めている。
 */
fun openMedia(context: Context, click: MediaClick, openViewer: (MediaViewerArgs) -> Unit) {
    if (click.attachment.type == MediaType.Unknown) {
        click.attachment.originalUrl?.let { url ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        return
    }
    openViewer(MediaViewerArgs.from(click))
}
