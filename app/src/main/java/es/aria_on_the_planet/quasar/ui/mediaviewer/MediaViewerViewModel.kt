package es.aria_on_the_planet.quasar.ui.mediaviewer

import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.media3.exoplayer.ExoPlayer
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.MediaType
import es.aria_on_the_planet.quasar.ui.component.player.PlaybackController
import es.aria_on_the_planet.quasar.ui.component.player.isPlayable

/**
 * メディアビューアの状態。Player ([PlaybackController]) を 1 つだけ持ち、回転しても再生を続ける。
 * ページが切り替わったら ([showPage]) そのページのメディアに差し替える。各ページの持ち主は [items] の要素自身。
 */
class MediaViewerViewModel(
    val args: MediaViewerArgs,
    createPlayer: () -> ExoPlayer,
) : ViewModel() {
    val items: List<MediaAttachment> = args.items.map { it.toAttachment() }

    val playback = PlaybackController(createPlayer)

    /** video / gifv のミュート状態。ページをまたいで引き継ぐ (audio は常に音ありで始める)。 */
    private var videoMuted = args.muted

    /** 最初のページだけ、インライン再生の続きから始める。 */
    private var pendingStart: Pair<Int, Long>? = args.initialIndex to args.startPositionMs

    fun showPage(index: Int) {
        val media = items.getOrNull(index) ?: return
        // 離れるページが video / gifv なら、そのミュート状態を次へ引き継ぐ
        val active = playback.activeOwner as? MediaAttachment
        if (active != null && active.type != MediaType.Audio) videoMuted = playback.muted
        if (active === media) return
        if (!media.type.isPlayable) {
            playback.stopActive()
            return
        }
        val start = pendingStart?.takeIf { it.first == index }?.second ?: 0
        pendingStart = null
        playback.play(
            owner = media,
            media = media,
            startPositionMs = start,
            muted = if (media.type == MediaType.Audio) false else videoMuted,
        )
    }

    /**
     * 上下スワイプで閉じるときに縮んで戻る先 ([index] ページのメディアの一覧での位置)。
     * 回転等でウィンドウの大きさが開いたときと変わっていたら位置は当てにならないので `null`。
     */
    fun dismissTarget(index: Int, windowSize: IntSize): DismissTarget? {
        if (windowSize.width != args.sourceWindowWidth || windowSize.height != args.sourceWindowHeight) return null
        val media = items.getOrNull(index) ?: return null
        val bounds = args.sourceBounds[media.id]?.toRect() ?: return null
        val aspectRatio = media.meta?.aspectRatio ?: return null
        return DismissTarget(bounds, aspectRatio)
    }

    /** 背面に回ったら止める。PiP 対応時は PiP 中に呼ばれないようにする (ON_PAUSE ではなく ON_STOP で呼んでいるのはそのため)。 */
    fun pause() = playback.pause()

    override fun onCleared() {
        playback.release()
    }
}
