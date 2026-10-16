package es.aria_on_the_planet.quasar.ui.component.composer

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import coil3.compose.AsyncImage
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.DraftAttachment
import es.aria_on_the_planet.quasar.domain.model.UploadState
import es.aria_on_the_planet.quasar.ui.component.modifier.horizontalFadingEdges
import es.aria_on_the_planet.quasar.ui.theme.QuasarTheme

/**
 * 添付予定のメディアの横並び。各サムネイルに削除ボタン・代替テキスト (ALT) ボタン・アップロード状態を重ね、
 * [canAddMore] なら末尾に追加ボタンを置く。はみ出す分は横スクロール (端は [MediaAttachments] と同様にフェード)。
 *
 * NOTE: 今はレイアウトのみ。端末のメディアを選ぶ処理・アップロード・代替テキストの編集は未実装。
 */
@Composable
fun AttachmentStrip(
    attachments: List<DraftAttachment>,
    onRemove: (DraftAttachment) -> Unit,
    onEditDescription: (DraftAttachment) -> Unit,
    modifier: Modifier = Modifier,
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
                    modifier = Modifier.size(height),
                )
            }
        }
        if (canAddMore) AddAttachmentTile(onClick = onAdd, modifier = Modifier.size(height))
    }
}

/** 添付 1 件のサムネイル。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AttachmentThumbnail(
    attachment: DraftAttachment,
    onRemove: () -> Unit,
    onEditDescription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(ThumbnailShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AsyncImage(
            model = attachment.previewUri,
            contentDescription = attachment.description.ifBlank { null },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        when (attachment.state) {
            is UploadState.Pending, is UploadState.Uploading -> Box(
                Modifier.fillMaxSize().background(Scrim),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator(color = Color.White) }

            UploadState.Failed -> Column(
                Modifier.fillMaxSize().background(Scrim),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color.White)
                Text(
                    stringResource(R.string.composer_media_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }

            is UploadState.Uploaded -> Unit
        }
        FilledTonalIconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Scrim,
                contentColor = Color.White,
            ),
        ) {
            Icon(
                Icons.Filled.Close,
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

/** 代替テキストの編集ボタン。設定済みなら塗りつぶしで示す。 */
@Composable
private fun AltBadge(hasDescription: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (hasDescription) MaterialTheme.colorScheme.primary else Scrim,
        contentColor = if (hasDescription) MaterialTheme.colorScheme.onPrimary else Color.White,
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
            Icons.Outlined.AddPhotoAlternate,
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

private val AttachmentHeight = 120.dp
private val FadingEdgeWidth = 12.dp
private val ThumbnailShape = RoundedCornerShape(12.dp)
private val Scrim = Color.Black.copy(alpha = 0.55f)

@Preview(showBackground = true)
@Composable
private fun AttachmentStripPreview() {
    QuasarTheme {
        Column(Modifier.padding(16.dp)) {
            AttachmentStrip(
                attachments = listOf(
                    DraftAttachment("1", null, "A cat", UploadState.Uploaded("m1")),
                    DraftAttachment("2", null, state = UploadState.Uploading(0.4f)),
                    DraftAttachment("3", null, state = UploadState.Failed),
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
