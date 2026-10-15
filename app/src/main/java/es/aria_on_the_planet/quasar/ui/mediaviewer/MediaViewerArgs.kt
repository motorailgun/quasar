package es.aria_on_the_planet.quasar.ui.mediaviewer

import androidx.compose.ui.geometry.Rect
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.MediaMeta
import es.aria_on_the_planet.quasar.domain.model.MediaSize
import es.aria_on_the_planet.quasar.domain.model.MediaType
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * メディアビューアの引数。Intent の extra に JSON 文字列で入れる (ドメインに serialization を持ち込まないための写し)。
 * 1 ポストのメディアは多くても十数件なので、Binder の上限には収まる。
 *
 * @param startPositionMs [initialIndex] のメディアの再生開始位置 (インライン再生からの引き継ぎ)。
 * @param muted video / gifv を最初にミュートするか。audio は常に音ありで始める。
 * @param sourceBounds 一覧での各メディアのウィンドウ上の位置 (px、メディア ID ごと)。上下スワイプで閉じるときに縮んで戻る先。
 * @param sourceWindowWidth 位置を測ったときのウィンドウの幅。回転等で大きさが変わっていたら [sourceBounds] は使わない。
 */
@Serializable
data class MediaViewerArgs(
    val items: List<ViewerMedia>,
    val initialIndex: Int,
    val artworkUrl: String?,
    val startPositionMs: Long = 0,
    val muted: Boolean = true,
    val sourceBounds: Map<String, ViewerRect> = emptyMap(),
    val sourceWindowWidth: Int = 0,
    val sourceWindowHeight: Int = 0,
) {
    fun encode(): String = ArgsJson.encodeToString(serializer(), this)

    companion object {
        fun decode(json: String): MediaViewerArgs? = runCatching { ArgsJson.decodeFromString(serializer(), json) }.getOrNull()

        fun from(click: MediaClick): MediaViewerArgs {
            val attachments = click.post.mediaAttachments
            return MediaViewerArgs(
                items = attachments.map { it.toViewerMedia() },
                initialIndex = attachments.indexOfFirst { it.id == click.attachment.id }.coerceAtLeast(0),
                artworkUrl = click.post.author.iconUrl.ifBlank { null },
                startPositionMs = click.playback?.positionMs ?: 0,
                muted = click.playback?.muted ?: true,
                sourceBounds = click.sourceBounds.mapValues { (_, r) -> ViewerRect(r.left, r.top, r.right, r.bottom) },
                sourceWindowWidth = click.sourceWindowSize.width,
                sourceWindowHeight = click.sourceWindowSize.height,
            )
        }
    }
}

@Serializable
data class ViewerMedia(
    val id: String,
    val type: MediaType,
    val url: String? = null,
    val remoteUrl: String? = null,
    val previewUrl: String? = null,
    val blurhash: String? = null,
    val description: String? = null,
    val original: ViewerSize? = null,
    val small: ViewerSize? = null,
    val durationSeconds: Double? = null,
)

@Serializable
data class ViewerSize(val width: Int, val height: Int)

@Serializable
data class ViewerRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun toRect() = Rect(left, top, right, bottom)
}

fun MediaAttachment.toViewerMedia() = ViewerMedia(
    id = id,
    type = type,
    url = url,
    remoteUrl = remoteUrl,
    previewUrl = previewUrl,
    blurhash = blurhash,
    description = description,
    original = meta?.original?.let { ViewerSize(it.width, it.height) },
    small = meta?.small?.let { ViewerSize(it.width, it.height) },
    durationSeconds = meta?.durationSeconds,
)

fun ViewerMedia.toAttachment() = MediaAttachment(
    id = id,
    type = type,
    url = url,
    remoteUrl = remoteUrl,
    previewUrl = previewUrl,
    meta = if (original == null && small == null && durationSeconds == null) {
        null
    } else {
        MediaMeta(
            original = original?.let { MediaSize(it.width, it.height) },
            small = small?.let { MediaSize(it.width, it.height) },
            durationSeconds = durationSeconds,
        )
    },
    blurhash = blurhash,
    description = description,
)

private val ArgsJson = Json { ignoreUnknownKeys = true }
