package es.aria_on_the_planet.quasar.ui.component.media

import es.aria_on_the_planet.quasar.ui.component.image.ImageKind
import es.aria_on_the_planet.quasar.ui.component.image.RemoteImage
import es.aria_on_the_planet.quasar.ui.component.player.MediaOverlayColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.MediaType
import es.aria_on_the_planet.quasar.ui.component.image.rememberBlurHashPainter
import es.aria_on_the_planet.quasar.ui.component.modifier.horizontalFadingEdges
import es.aria_on_the_planet.quasar.ui.component.player.InlineControls
import es.aria_on_the_planet.quasar.ui.component.player.LocalInlinePlayback
import es.aria_on_the_planet.quasar.ui.component.player.MediaPlayer
import es.aria_on_the_planet.quasar.ui.component.player.PlaybackSnapshot
import es.aria_on_the_planet.quasar.ui.component.player.isPlayable

/**
 * 添付メディアの並び。全アイテムを [height] で揃え、幅はアスペクト比から決める。
 * 合計幅が収まらない場合は横スクロールし、まだスクロールできる側の端をフェードさせる。
 *
 * @param sensitive 閲覧注意 (NSFW)。`true` なら各メディアを初期状態でぼかし、右上の目アイコンで切り替えられる。
 * @param artworkUrl audio をインライン再生するときに回すアイコン (投稿者)。
 * @param onClick ビューアを開く等。インライン再生中にタップされた場合は再生位置が付く。
 *   各メディアのウィンドウ上の位置も付ける (ビューアを閉じるときに縮んで戻る先)。
 */
@Composable
fun MediaAttachments(
    attachments: List<MediaAttachment>,
    height: Dp,
    modifier: Modifier = Modifier,
    sensitive: Boolean = false,
    artworkUrl: String? = null,
    onClick: (MediaTap) -> Unit = {},
) {
    if (attachments.isEmpty()) return
    // 位置はタップしたときにだけ要るので、置かれた座標系だけを持っておき、タップ時にウィンドウ上の位置を求める
    // (onGloballyPositioned だと一覧のスクロールの毎フレーム呼ばれる)。再描画は不要なので State にしない
    val placements = remember { mutableMapOf<String, LayoutCoordinates>() }
    val windowSize = LocalWindowInfo.current.containerSize
    val scrollState = rememberScrollState()
    // 収まっているときはスクロールを切る。有効なままだと横のドラッグをこの Row が取り、離したときの
    // 速さが親の HorizontalPager に届かない (Pager の pageNestedScrollConnection が子のフリングを
    // 消費する) ので、速く払ってもカラムが切り替わらなくなる。maxValue はレイアウトで決まり、決まる前は
    // Int.MAX_VALUE。その間も無効扱いにして、収まる (大半の) 並びでは組み立て直しを起こさない
    val scrollable by remember { derivedStateOf { scrollState.maxValue in 1 until Int.MAX_VALUE } }
    // 端のフェードは残りのスクロール量から描画時に決める (コンポジションでは読まない)
    val fadePx = with(LocalDensity.current) { FadingEdgeWidth.toPx() }
    Row(
        modifier = modifier
            .horizontalFadingEdges(
                width = FadingEdgeWidth,
                start = { (scrollState.value / fadePx).coerceIn(0f, 1f) },
                end = { ((scrollState.maxValue - scrollState.value) / fadePx).coerceIn(0f, 1f) },
            )
            .horizontalScroll(scrollState, enabled = scrollable),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        attachments.forEach { attachment ->
            key(attachment.id) {
                MediaAttachmentItem(
                    attachment = attachment,
                    height = height,
                    sensitive = sensitive,
                    artworkUrl = artworkUrl,
                    onClick = { playback ->
                        val bounds = placements
                            .filterValues { it.isAttached }
                            .mapValues { (_, coordinates) -> Rect(coordinates.positionInWindow(), coordinates.size.toSize()) }
                        onClick(MediaTap(attachment, playback, bounds, windowSize))
                    },
                    modifier = Modifier.onPlaced { placements[attachment.id] = it },
                )
            }
        }
    }
}

/**
 * 添付メディア 1 件。高さ固定で、幅はアスペクト比に従う。
 *
 * タップは [onClick] (ビューア)。video / gifv / audio は中央の再生ボタンだけ別扱いで、[LocalInlinePlayback] が
 * あればその場で再生する (ミュート、自動再生はしない)。インライン再生中にタップすると再生位置を付けて [onClick] を呼ぶ。
 * 一覧から外れたら止める。
 *
 * @param sensitive `true` なら初期状態でぼかし、右上の目アイコン (またはメディア自体のタップ) で表示する。
 *   `false` なら目アイコンは出さない。
 */
@Composable
fun MediaAttachmentItem(
    attachment: MediaAttachment,
    height: Dp,
    modifier: Modifier = Modifier,
    sensitive: Boolean = false,
    artworkUrl: String? = null,
    onClick: (PlaybackSnapshot?) -> Unit = {},
) {
    var hidden by rememberSaveable(attachment.id, sensitive) { mutableStateOf(sensitive) }
    val playback = LocalInlinePlayback.current?.takeIf { attachment.type.isPlayable }
    // 同じメディアが一覧の複数の場所に出ても区別できるよう、持ち主はこの表示ごとに作る
    val owner = remember { Any() }
    val playing = playback != null && playback.isActive(owner)
    DisposableEffect(playback, owner) {
        onDispose { playback?.stop(owner) }
    }
    Box(
        modifier = modifier
            .height(height)
            .width(height * displayAspectRatio(attachment))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = {
                when {
                    // ぼかし中のタップは表示に切り替えるだけにする
                    hidden -> hidden = false
                    playing -> {
                        val snapshot = playback.snapshot(owner)
                        // ビューアはこの画面に重ねて出す (この画面はコンポジションに残る) ので明示的に止める
                        playback.stop(owner)
                        onClick(snapshot)
                    }
                    else -> onClick(null)
                }
            }),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) {
            MediaPlayer(
                media = attachment,
                player = playback.player,
                artworkUrl = artworkUrl,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                playback.player?.let { InlineControls(it, attachment) }
                if (playback.failed) PlaybackFailedLabel()
            }
        } else {
            MediaThumbnail(
                attachment = attachment,
                hidden = hidden,
                onPlay = {
                    if (playback != null) playback.play(owner, attachment, muted = true) else onClick(null)
                },
            )
        }
        if (sensitive) {
            SensitiveToggle(
                hidden = hidden,
                onToggle = {
                    hidden = !hidden
                    if (hidden) playback?.stop(owner)
                },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/** 再生していないときの表示 (サムネイル + 種別のバッジ)。再生できる種別は中央の再生ボタンで [onPlay]。 */
@Composable
private fun BoxScope.MediaThumbnail(attachment: MediaAttachment, hidden: Boolean, onPlay: () -> Unit) {
    val placeholder = rememberBlurHashPainter(attachment.blurhash)
    if (hidden && placeholder != null) {
        // BlurHash があればそれをぼかし表示にする (本体の画像は読み込まない)
        Image(
            painter = placeholder,
            contentDescription = stringResource(R.string.media_sensitive),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else if (attachment.previewUrl != null) {
        RemoteImage(
            url = attachment.previewUrl,
            kind = ImageKind.Other,
            contentDescription = if (hidden) stringResource(R.string.media_sensitive) else attachment.description,
            placeholder = placeholder,
            error = placeholder,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (hidden) Modifier.blur(HiddenBlurRadius) else Modifier),
        )
    } else {
        Icon(
            imageVector = if (attachment.type == MediaType.Audio) Symbols.Outlined.AudioFile else Symbols.Outlined.BrokenImage,
            contentDescription = attachment.description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
    }
    if (attachment.type == MediaType.Gifv) TypeLabel("GIF", Modifier.align(Alignment.BottomStart))
    // ぼかし中はメディア全体のタップで表示に切り替えるので、再生ボタンは出さない
    if (attachment.type.isPlayable && !hidden) PlayButton(onPlay)
}

@Composable
private fun BoxScope.PlaybackFailedLabel() {
    Text(
        text = stringResource(R.string.player_error),
        style = MaterialTheme.typography.labelSmall,
        color = MediaOverlayColors.Content,
        modifier = Modifier
            .align(Alignment.Center)
            .background(MediaOverlayColors.Scrim, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 閲覧注意メディアのぼかし切り替え (右上の目アイコン)。 */
@Composable
private fun SensitiveToggle(hidden: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(MediaOverlayColors.Scrim)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            // ぼかし中は「表示する」、表示中は「隠す」
            imageVector = if (hidden) Symbols.Outlined.Visibility else Symbols.Outlined.VisibilityOff,
            contentDescription = stringResource(if (hidden) R.string.media_show else R.string.media_hide),
            tint = MediaOverlayColors.Content,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** その場で再生するボタン。見た目は 40dp だが、押しやすいよう周囲 4dp もタッチ範囲にする。 */
@Composable
private fun PlayButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = stringResource(R.string.player_play_inline), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Symbols.Filled.PlayArrow,
            contentDescription = stringResource(R.string.player_play),
            tint = MediaOverlayColors.Content,
            modifier = Modifier
                .size(40.dp)
                .background(MediaOverlayColors.Scrim, CircleShape)
                .padding(6.dp),
        )
    }
}

@Composable
private fun TypeLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MediaOverlayColors.Content,
        modifier = modifier
            .padding(6.dp)
            .background(MediaOverlayColors.Scrim, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** 極端に細長いメディアで行が崩れないよう、表示上のアスペクト比は一定範囲に収める。 */
internal fun displayAspectRatio(attachment: MediaAttachment): Float =
    (attachment.meta?.aspectRatio ?: 1f).coerceIn(MinAspectRatio, MaxAspectRatio)

private val FadingEdgeWidth = 12.dp
private val HiddenBlurRadius = 32.dp
private const val MinAspectRatio = 1f / 3f
private const val MaxAspectRatio = 3f
