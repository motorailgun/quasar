package es.aria_on_the_planet.quasar.ui.component.player

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.compose.AsyncImage
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.MediaType
import es.aria_on_the_planet.quasar.ui.component.image.rememberBlurHashPainter

/**
 * video / gifv / audio の再生部分。Player は持たず、受け取った [player] を描くだけ。
 * 一覧 (インライン) とメディアビューアの両方で使う。
 *
 * * [player] が `null` (自分が再生の持ち主でない) の間は、サムネイル (preview / BlurHash) を出す。
 * * audio は映像の代わりに [artworkUrl] (投稿者のアイコン) を円形で出し、再生中だけ回す。
 * * 描画は TextureView。SurfaceView は Compose の alpha (閉じるときのフェード) や一覧のスクロールに
 *   追従しないことがあるため。
 *
 * @param controls 上に重ねる操作部分 (InlineControls / FullControls 等)。
 */
@OptIn(UnstableApi::class)
@Composable
fun MediaPlayer(
    media: MediaAttachment,
    player: Player?,
    modifier: Modifier = Modifier,
    artworkUrl: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    controls: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (media.type == MediaType.Audio) {
            val isPlaying by rememberIsPlaying(player)
            AudioArtwork(artworkUrl = artworkUrl, rotating = isPlaying, modifier = Modifier.fillMaxSize())
        } else if (player != null) {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = contentScale,
                // 最初のフレームが出るまではサムネイルで覆う
                shutter = { MediaPoster(media, contentScale, Modifier.fillMaxSize()) },
            )
        } else {
            MediaPoster(media, contentScale, Modifier.fillMaxSize())
        }
        controls()
    }
}

/** 再生前・読み込み中のサムネイル。preview_url を読み、無ければ / 読めなければ BlurHash。 */
@Composable
fun MediaPoster(media: MediaAttachment, contentScale: ContentScale, modifier: Modifier = Modifier) {
    val placeholder = rememberBlurHashPainter(media.blurhash)
    if (media.previewUrl != null) {
        AsyncImage(
            model = media.previewUrl,
            contentDescription = media.description,
            placeholder = placeholder,
            error = placeholder,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else if (placeholder != null) {
        Image(placeholder, contentDescription = media.description, contentScale = contentScale, modifier = modifier)
    } else {
        Box(modifier)
    }
}

/**
 * 音声の「絵」。投稿者のアイコンを円形で中央に置き、[rotating] の間だけ回す。
 * 止めたときは 0° に戻さず、その角度で止める。大きさは領域の短辺の半分 (最大 [MaxArtworkSize])。
 */
@Composable
fun AudioArtwork(artworkUrl: String?, rotating: Boolean, modifier: Modifier = Modifier) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(rotating) {
        while (rotating) {
            rotation.animateTo(rotation.value + 360f, tween(RotationMillis, easing = LinearEasing))
            rotation.snapTo(rotation.value % 360f)
        }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = min(min(maxWidth, maxHeight) * 0.5f, MaxArtworkSize)
        val iconModifier = Modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation.value }
            .clip(CircleShape)
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = iconModifier,
            )
        } else {
            Icon(
                Icons.Outlined.AudioFile,
                contentDescription = null,
                tint = MediaOverlayColors.Content,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

private const val RotationMillis = 8_000
private val MaxArtworkSize: Dp = 240.dp
