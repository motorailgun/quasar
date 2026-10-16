package es.aria_on_the_planet.quasar.ui.mediaviewer

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.MediaSaveFolder
import es.aria_on_the_planet.quasar.domain.model.MediaSaveTarget
import es.aria_on_the_planet.quasar.domain.model.SaveDirectory
import es.aria_on_the_planet.quasar.domain.model.MediaType
import es.aria_on_the_planet.quasar.ui.component.player.MediaOverlayColors
import es.aria_on_the_planet.quasar.ui.component.player.rememberIsPlaying
import kotlinx.coroutines.launch

/**
 * メディアビューア。同じポストのメディアを左右スワイプで切り替え、上下スワイプ・戻る・閉じるボタンで閉じる
 * (出入りのアニメーションは [DismissibleViewer])。
 *
 * 上部バー・再生操作・システムバーはタップで出し入れする。再生中は画面を消さず、背面に回ったら (ON_STOP) 一時停止する。
 *
 * @param onDismissed 閉じるアニメーションが終わった後 (Activity を finish する)。
 * @param onSave 保存。受け付けたら保存先、失敗なら `null` を返す (結果はスナックバーで知らせる)。`null` ならメニューに出さない。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel,
    onDismissed: () -> Unit,
    onSave: (suspend (MediaAttachment) -> MediaSaveTarget?)? = null,
) {
    val items = viewModel.items
    val pagerState = rememberPagerState(initialPage = viewModel.args.initialIndex) { items.size }
    LaunchedEffect(pagerState.settledPage) { viewModel.showPage(pagerState.settledPage) }
    // PiP 対応時に PiP 中 (ON_PAUSE のまま) は再生を続けられるよう、ON_STOP で止める
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    // 表示中の画像を拡大している間は、ページ切り替えと上下スワイプで閉じる操作を止める (1 本指は画像の移動に使う)
    var zoomed by remember { mutableStateOf(false) }
    SystemBarsVisibilityEffect(controlsVisible)
    val isPlaying by rememberIsPlaying(viewModel.playback.player)
    val uriHandler = LocalUriHandler.current
    val openUrl: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }

    val windowInfo = LocalWindowInfo.current
    val dismissState = rememberDismissibleViewerState(
        target = { viewModel.dismissTarget(pagerState.currentPage, windowInfo.containerSize) },
        onDismissed = onDismissed,
    )
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val save: ((MediaAttachment) -> Unit)? = onSave?.let { save ->
        { media ->
            scope.launch {
                val target = save(media)
                val message = if (target != null) {
                    resources.getString(R.string.media_viewer_saving, "${target.directory.folderName}/$MediaSaveFolder")
                } else {
                    resources.getString(R.string.media_viewer_save_failed)
                }
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
    }
    // 上下スワイプ・「戻る」の途中や閉じている間は上部バー・再生操作を隠す
    val showControls = controlsVisible && !dismissState.isActive
    DismissibleViewer(
        state = dismissState,
        swipeEnabled = !zoomed,
        // 再生中は画面を消さない
        modifier = if (isPlaying) Modifier.keepScreenOn() else Modifier,
    ) {
        HorizontalPager(
            state = pagerState,
            key = { items[it].id },
            userScrollEnabled = !zoomed,
        ) { page ->
            val media = items[page]
            when (media.type) {
                MediaType.Image -> ImagePage(
                    media = media,
                    onTap = { controlsVisible = !controlsVisible },
                    // 隣のページが合成・破棄されても上書きしないよう、表示中のページのものだけを使う
                    onZoomedChange = { if (page == pagerState.currentPage) zoomed = it },
                    onLoaded = { aspectRatio -> viewModel.onImageLoaded(media, aspectRatio) },
                )
                MediaType.Video, MediaType.Gifv, MediaType.Audio -> PlayerPage(
                    media = media,
                    playback = viewModel.playback,
                    artworkUrl = viewModel.args.artworkUrl,
                    controlsVisible = showControls,
                    onControlsVisibleChange = { controlsVisible = it },
                )
                MediaType.Unknown -> UnknownPage(media, onOpen = openUrl)
            }
        }
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopBar(
                media = items[pagerState.currentPage],
                position = pagerState.currentPage + 1,
                count = items.size,
                onClose = { scope.launch { dismissState.dismiss() } },
                onOpenUrl = openUrl,
                onSave = save,
            )
        }
        // 下端の再生操作に重ならない高さに出す。既定 (inverseSurface) はライトテーマで暗く、黒背景に埋もれるので surface 系にする
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                .padding(bottom = 80.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 端末上でのディレクトリ名 (Environment.DIRECTORY_*)。 */
private val SaveDirectory.folderName: String
    get() = when (this) {
        SaveDirectory.Pictures -> "Pictures"
        SaveDirectory.Music -> "Music"
        SaveDirectory.Downloads -> "Download"
    }

/** 操作部分と一緒にシステムバーも出し入れする (隠している間は端からのスワイプで一時的に出る)。 */
@Composable
private fun SystemBarsVisibilityEffect(visible: Boolean) {
    val window = LocalActivity.current?.window ?: return
    LaunchedEffect(window, visible) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopBar(
    media: MediaAttachment,
    position: Int,
    count: Int,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onSave: ((MediaAttachment) -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(MediaOverlayColors.GradientScrim, Color.Transparent)))
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, stringResource(R.string.media_viewer_close), tint = MediaOverlayColors.Content)
        }
        if (count > 1) {
            Text(
                stringResource(R.string.media_viewer_position, position, count),
                color = MediaOverlayColors.Content,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        MediaViewerMenu(media, onOpenUrl = onOpenUrl, onSave = onSave)
    }
}

/** 右上のメニュー。保存・ブラウザで開くはどちらも元ファイル ([MediaAttachment.originalUrl]、リモートの原本優先)。 */
@Composable
private fun MediaViewerMenu(
    media: MediaAttachment,
    onOpenUrl: (String) -> Unit,
    onSave: ((MediaAttachment) -> Unit)?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val originalUrl = media.originalUrl
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.media_viewer_menu), tint = MediaOverlayColors.Content)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onSave != null && originalUrl != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.media_viewer_save)) },
                    leadingIcon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSave(media)
                    },
                )
            }
            if (originalUrl != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.media_viewer_open_in_browser)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onOpenUrl(originalUrl)
                    },
                )
            }
        }
    }
}
