package es.aria_on_the_planet.quasar.ui.component.composer

import es.aria_on_the_planet.quasar.ui.component.image.LocalImage
import es.aria_on_the_planet.quasar.ui.component.player.MediaOverlayColors
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.DraftAttachment
import es.aria_on_the_planet.quasar.domain.model.UploadFailure
import es.aria_on_the_planet.quasar.domain.model.UploadState
import es.aria_on_the_planet.quasar.ui.component.modifier.horizontalFadingEdges
import es.aria_on_the_planet.quasar.ui.theme.QuasarTheme

/**
 * 添付予定のメディアの横並び。各サムネイルに削除ボタン・代替テキスト (ALT) ボタン・アップロード状態を重ね、
 * [canAddMore] なら末尾に追加ボタンを置く。はみ出す分は横スクロール (端は [MediaAttachments] と同様にフェード)。
 *
 * 添付が無いときは何も出さない (追加のタイルも出ない)。
 */
@Composable
fun AttachmentStrip(
    attachments: List<DraftAttachment>,
    onRemove: (DraftAttachment) -> Unit,
    onEditDescription: (DraftAttachment) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (DraftAttachment) -> Unit = {},
    canAddMore: Boolean = false,
    onAdd: () -> Unit = {},
    height: Dp = AttachmentHeight,
) {
    if (attachments.isEmpty()) return
    val scrollState = rememberScrollState()
    val startFade by animateFloatAsState(if (scrollState.canScrollBackward) 1f else 0f, label = "startFade")
    val endFade by animateFloatAsState(if (scrollState.canScrollForward) 1f else 0f, label = "endFade")
    Row(
        modifier = modifier
            .horizontalFadingEdges(width = FadingEdgeWidth, start = { startFade }, end = { endFade })
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            key(attachment.localId) {
                AttachmentThumbnail(
                    attachment = attachment,
                    onRemove = { onRemove(attachment) },
                    onEditDescription = { onEditDescription(attachment) },
                    onRetry = { onRetry(attachment) },
                    modifier = Modifier.size(height),
                )
            }
        }
        if (canAddMore) AddAttachmentTile(onClick = onAdd, modifier = Modifier.size(height))
    }
}

/**
 * 添付 1 件のサムネイル。アップロード中は進み具合 (分からなければ不定のインジケータ)、サーバの変換待ちは
 * 「Processing」、失敗は理由を重ねる。通信エラーで失敗したものはタップで [onRetry]。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AttachmentThumbnail(
    attachment: DraftAttachment,
    onRemove: () -> Unit,
    onEditDescription: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    Box(
        modifier
            .clip(ThumbnailShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        LocalImage(
            uri = attachment.previewUri,
            contentDescription = attachment.description.ifBlank { null },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (attachment.isVideoLike) {
            MediaKindBadge(
                gif = attachment.mimeType == "image/gif",
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
        }
        when (val state = attachment.state) {
            is UploadState.Pending -> Overlay { LoadingIndicator(color = MediaOverlayColors.Content) }

            is UploadState.Uploading -> Overlay {
                val progress = state.progress
                if (progress == null) {
                    LoadingIndicator(color = MediaOverlayColors.Content)
                } else {
                    val animated by animateFloatAsState(progress, label = "uploadProgress")
                    CircularProgressIndicator(
                        progress = { animated },
                        color = MediaOverlayColors.Content,
                        trackColor = MediaOverlayColors.ContentInactive,
                    )
                }
            }

            is UploadState.Processing -> Overlay {
                LoadingIndicator(color = MediaOverlayColors.Content)
                OverlayLabel(stringResource(R.string.composer_media_processing))
            }

            is UploadState.Failed -> {
                val retryable = state.reason == UploadFailure.Failed
                Overlay(onClick = if (retryable) onRetry else null) {
                    Icon(
                        if (retryable) Symbols.Outlined.Refresh else Symbols.Outlined.Error,
                        contentDescription = null,
                        tint = MediaOverlayColors.Content,
                    )
                    OverlayLabel(
                        stringResource(
                            when (state.reason) {
                                UploadFailure.TooLarge -> R.string.composer_media_too_large
                                UploadFailure.Unsupported -> R.string.composer_media_unsupported
                                UploadFailure.NotAllowed -> R.string.composer_media_not_allowed
                                UploadFailure.Rejected -> R.string.composer_media_rejected
                                UploadFailure.Failed -> R.string.composer_media_failed
                            },
                        ),
                    )
                }
            }

            is UploadState.Uploaded -> Unit
        }
        FilledTonalIconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MediaOverlayColors.Scrim,
                contentColor = MediaOverlayColors.Content,
            ),
        ) {
            Icon(
                Symbols.Filled.Close,
                contentDescription = stringResource(R.string.composer_media_remove),
                modifier = Modifier.size(18.dp),
            )
        }
        AltBadge(
            hasDescription = attachment.description.isNotBlank(),
            onClick = onEditDescription,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
        )
    }
}

/** サムネイルに重ねる半透明の面 (状態の表示)。[onClick] があればタップできる。 */
@Composable
private fun Overlay(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MediaOverlayColors.Scrim)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun OverlayLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MediaOverlayColors.Content,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/** 動画 / GIF の印 (右下)。 */
@Composable
private fun MediaKindBadge(gif: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(6.dp), color = MediaOverlayColors.Scrim, contentColor = MediaOverlayColors.Content) {
        if (gif) {
            Text("GIF", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        } else {
            Icon(Symbols.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(2.dp).size(16.dp))
        }
    }
}

/** 代替テキストの編集ボタン。設定済みなら塗りつぶしで示す。 */
@Composable
private fun AltBadge(hasDescription: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (hasDescription) MaterialTheme.colorScheme.primary else MediaOverlayColors.Scrim,
        contentColor = if (hasDescription) MaterialTheme.colorScheme.onPrimary else MediaOverlayColors.Content,
    ) {
        Text(
            stringResource(R.string.composer_media_alt),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AddAttachmentTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(ThumbnailShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ThumbnailShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Symbols.Outlined.AddPhotoAlternate,
            contentDescription = stringResource(R.string.composer_add_media),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** メディアの閲覧注意の切り替え (添付があるときだけ出す)。 */
@Composable
fun SensitiveMediaToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Checkbox)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(stringResource(R.string.composer_media_sensitive), style = MaterialTheme.typography.bodyMedium)
    }
}

private val AttachmentHeight = 160.dp
private val FadingEdgeWidth = 12.dp
private val ThumbnailShape = RoundedCornerShape(12.dp)

@Preview(showBackground = true)
@Composable
private fun AttachmentStripPreview() {
    QuasarTheme {
        Column(Modifier.padding(16.dp)) {
            AttachmentStrip(
                attachments = listOf(
                    DraftAttachment("1", null, "A cat", UploadState.Uploaded("m1")),
                    DraftAttachment("2", null, state = UploadState.Uploading(0.4f), mimeType = "video/mp4"),
                    DraftAttachment("3", null, state = UploadState.Failed(UploadFailure.Failed)),
                    DraftAttachment("4", null, state = UploadState.Failed(UploadFailure.TooLarge)),
                ),
                onRemove = {},
                onEditDescription = {},
                canAddMore = true,
            )
            Spacer(Modifier.height(8.dp))
            SensitiveMediaToggle(checked = true, onCheckedChange = {})
        }
    }
}
