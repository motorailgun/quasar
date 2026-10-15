package es.aria_on_the_planet.quasar.ui.component.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.MediaType
import es.aria_on_the_planet.quasar.ui.component.image.rememberBlurHashPainter
import es.aria_on_the_planet.quasar.ui.component.modifier.horizontalFadingEdges

/**
 * 添付メディアの並び。全アイテムを [height] で揃え、幅はアスペクト比から決める。
 * 合計幅が収まらない場合は横スクロールし、まだスクロールできる側の端をフェードさせる。
 */
@Composable
fun MediaAttachments(
    attachments: List<MediaAttachment>,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: (MediaAttachment) -> Unit = {},
) {
    if (attachments.isEmpty()) return
    val scrollState = rememberScrollState()
    val startFade by animateFloatAsState(if (scrollState.canScrollBackward) 1f else 0f, label = "startFade")
    val endFade by animateFloatAsState(if (scrollState.canScrollForward) 1f else 0f, label = "endFade")
    Row(
        modifier = modifier
            .horizontalFadingEdges(width = FadingEdgeWidth, start = { startFade }, end = { endFade })
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        attachments.forEach { attachment ->
            key(attachment.id) {
                MediaAttachmentItem(
                    attachment = attachment,
                    height = height,
                    onClick = { onClick(attachment) },
                )
            }
        }
    }
}

/** 添付メディア 1 件。高さ固定で、幅はアスペクト比に従う。 */
@Composable
fun MediaAttachmentItem(
    attachment: MediaAttachment,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .height(height)
            .width(height * displayAspectRatio(attachment))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val placeholder = rememberBlurHashPainter(attachment.blurhash)
        if (attachment.previewUrl != null) {
            AsyncImage(
                model = attachment.previewUrl,
                contentDescription = attachment.description,
                placeholder = placeholder,
                error = placeholder,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = if (attachment.type == MediaType.Audio) Icons.Outlined.AudioFile else Icons.Outlined.BrokenImage,
                contentDescription = attachment.description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        when (attachment.type) {
            MediaType.Video -> PlayBadge()
            MediaType.Gifv -> TypeLabel("GIF", Modifier.align(Alignment.BottomStart))
            MediaType.Audio -> if (attachment.previewUrl != null) PlayBadge()
            MediaType.Image, MediaType.Unknown -> Unit
        }
    }
}

@Composable
private fun PlayBadge() {
    Icon(
        Icons.Filled.PlayArrow,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier
            .size(40.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            .padding(6.dp),
    )
}

@Composable
private fun TypeLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .padding(6.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** 極端に細長いメディアで行が崩れないよう、表示上のアスペクト比は一定範囲に収める。 */
internal fun displayAspectRatio(attachment: MediaAttachment): Float =
    (attachment.meta?.aspectRatio ?: 1f).coerceIn(MinAspectRatio, MaxAspectRatio)

private val FadingEdgeWidth = 12.dp
private const val MinAspectRatio = 1f / 3f
private const val MaxAspectRatio = 3f
