package es.aria_on_the_planet.quasar.ui.mediaviewer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.ui.component.image.ImageKind
import es.aria_on_the_planet.quasar.ui.component.image.RemoteImage
import es.aria_on_the_planet.quasar.ui.component.player.FullControls
import es.aria_on_the_planet.quasar.ui.component.player.MediaOverlayColors
import es.aria_on_the_planet.quasar.ui.component.player.MediaPlayer
import es.aria_on_the_planet.quasar.ui.component.player.MediaPoster
import es.aria_on_the_planet.quasar.ui.component.player.PlaybackController

/**
 * 画像。元サイズの画像 (url → 読めなければ remote_url) を画面に収まるように拡大縮小して出す。
 * 読み込み中は一覧で読み込み済みのはずの preview (と BlurHash) を同じ位置に出しておく。
 * ピンチで拡大縮小でき ([zoomable])、ダブルタップで 130% に拡大 / 等倍に戻す。拡大しているかを [onZoomedChange] で
 * 知らせる (親はページ切り替え等を止める)。1 回タップはダブルタップでないと分かってから (少し遅れて) [onTap]。
 */
@Composable
fun ImagePage(
    media: MediaAttachment,
    onTap: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val urls = media.viewUrls
    var urlIndex by rememberSaveable(media.id) { mutableIntStateOf(0) }
    var loaded by remember(media.id) { mutableStateOf(false) }
    var failed by remember(media.id) { mutableStateOf(urls.isEmpty()) }
    val zoom = rememberZoomState(media.id, contentAspectRatio = media.meta?.aspectRatio)
    val currentOnTap by rememberUpdatedState(onTap)
    val scope = rememberCoroutineScope()
    val currentOnZoomedChange by rememberUpdatedState(onZoomedChange)
    LaunchedEffect(zoom) {
        snapshotFlow { zoom.isZoomed }.collect { currentOnZoomedChange(it) }
    }
    // ページから外れたら (拡大中はページを切り替えられないが、念のため) 親の状態を戻す
    DisposableEffect(zoom) { onDispose { currentOnZoomedChange(false) } }
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(zoom) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { position -> zoom.toggleZoom(position, scope) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().zoomable(zoom), contentAlignment = Alignment.Center) {
            if (!loaded) MediaPoster(media, ContentScale.Fit, Modifier.fillMaxSize())
            urls.getOrNull(urlIndex)?.let { url ->
                RemoteImage(
                    url = url,
                    kind = ImageKind.Other,
                    contentDescription = media.description,
                    contentScale = ContentScale.Fit,
                    onLoaded = { size ->
                        loaded = true
                        // meta に無い・不正確なこともあるので、実際の画像の縦横比で移動範囲を決める
                        if (size.width > 0f && size.height > 0f) zoom.contentAspectRatio = size.width / size.height
                    },
                    onError = {
                        if (urlIndex < urls.lastIndex) urlIndex++ else failed = true
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (failed && !loaded) {
            Icon(
                Icons.Outlined.BrokenImage,
                contentDescription = stringResource(R.string.media_viewer_load_error),
                tint = MediaOverlayColors.Content,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/**
 * video / gifv / audio。[playback] の持ち主がこのページ ([media]) のときだけ Player を描き、操作を出す。
 */
@Composable
fun PlayerPage(
    media: MediaAttachment,
    playback: PlaybackController,
    artworkUrl: String?,
    controlsVisible: Boolean,
    onControlsVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = playback.player?.takeIf { playback.isActive(media) }
    MediaPlayer(
        media = media,
        player = player,
        artworkUrl = artworkUrl,
        contentScale = ContentScale.Fit,
        modifier = modifier.fillMaxSize(),
    ) {
        if (player != null) {
            FullControls(
                player = player,
                media = media,
                visible = controlsVisible,
                onVisibleChange = onControlsVisibleChange,
            )
        }
        if (player != null && playback.failed) {
            Text(
                stringResource(R.string.player_error),
                color = MediaOverlayColors.Content,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** 種別不明 (unknown)。ビューアでは表示できないので、元のファイルをブラウザで開くボタンだけ出す。 */
@Composable
fun UnknownPage(media: MediaAttachment, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = null,
            tint = MediaOverlayColors.Content,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.media_viewer_unsupported),
            color = MediaOverlayColors.Content,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
        media.originalUrl?.let { url ->
            Button(
                onClick = { onOpen(url) },
                colors = ButtonDefaults.buttonColors(containerColor = MediaOverlayColors.Content, contentColor = MediaOverlayColors.Background),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.media_viewer_open_in_browser), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
