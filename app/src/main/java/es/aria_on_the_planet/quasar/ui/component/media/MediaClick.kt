package es.aria_on_the_planet.quasar.ui.component.media

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.player.PlaybackSnapshot

/**
 * [MediaAttachments] の中でのタップ。ポストを付けて [MediaClick] にする ([toClick])。
 *
 * @param sourceBounds 並びの各メディアのウィンドウ上の位置 (px、メディア ID ごと)。ビューアを閉じるときに縮んで戻る先。
 * @param sourceWindowSize 位置を測ったときのウィンドウの大きさ。回転等で変わっていたらビューアは位置を使わない。
 */
data class MediaTap(
    val attachment: MediaAttachment,
    val playback: PlaybackSnapshot? = null,
    val sourceBounds: Map<String, Rect> = emptyMap(),
    val sourceWindowSize: IntSize = IntSize.Zero,
) {
    fun toClick(post: Post) = MediaClick(post, attachment, playback, sourceBounds, sourceWindowSize)
}

/**
 * 添付メディアのタップ。ビューアは [post] の他のメディア (左右スワイプ) と投稿者のアイコン (audio の絵) も使う。
 *
 * @param playback インライン再生中にタップされた場合の再生位置。ビューアはそこから続けて再生する。
 * @param sourceBounds 一覧での各メディアの位置 ([MediaTap.sourceBounds])。
 */
data class MediaClick(
    val post: Post,
    val attachment: MediaAttachment,
    val playback: PlaybackSnapshot? = null,
    val sourceBounds: Map<String, Rect> = emptyMap(),
    val sourceWindowSize: IntSize = IntSize.Zero,
)
