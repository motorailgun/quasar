package es.aria_on_the_planet.quasar.ui.component.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.MediaType

/** 再生できる種別 (video / gifv / audio)。 */
val MediaType.isPlayable: Boolean
    get() = this == MediaType.Video || this == MediaType.Gifv || this == MediaType.Audio

/** ビューアへ引き継ぐ再生状態。 */
data class PlaybackSnapshot(val positionMs: Long, val muted: Boolean)

/**
 * 1 つの [ExoPlayer] を複数の表示先 (一覧の各メディア、ビューアの各ページ) で使い回す。
 *
 * 同時に再生するのは「持ち主」([activeOwner]) 1 つだけで、表示側は自分が持ち主なら [player] を、
 * そうでなければ `null` を [MediaPlayer] に渡す (1 つの Player は 1 つの Surface にしか描けない)。
 * 持ち主は表示側の `remember { Any() }` などで、同じメディアが複数の場所に出ても区別できる。
 *
 * Player は最初に [play] されたときに作る。インラインでもビューアでも同じクラスを使う。
 */
@Stable
class PlaybackController(private val createPlayer: () -> ExoPlayer) {
    var player: ExoPlayer? by mutableStateOf(null)
        private set
    var activeOwner: Any? by mutableStateOf(null)
        private set

    /** すべての候補 URL で再生に失敗した。 */
    var failed: Boolean by mutableStateOf(false)
        private set

    /** 再生中のメディアがミュートか。ミュートボタン (Player の volume) の変更にも追従する。 */
    var muted: Boolean by mutableStateOf(true)
        private set

    private var media: MediaAttachment? = null
    private var urlIndex = 0

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val current = media ?: return
            val urls = current.viewUrls
            val p = player ?: return
            if (urlIndex < urls.lastIndex) {
                // url (接続中サーバ) で読めなければ remote_url (元サーバ) を試す
                urlIndex++
                val position = p.currentPosition
                p.setMediaItem(MediaItem.fromUri(urls[urlIndex]), position)
                p.prepare()
            } else {
                failed = true
            }
        }

        override fun onVolumeChanged(volume: Float) {
            muted = volume == 0f
            updateAudioFocus()
        }
    }

    fun isActive(owner: Any): Boolean = activeOwner === owner

    /** [owner] として [media] を最初から (または [startPositionMs] から) 再生する。他の持ち主の再生は止まる。 */
    fun play(owner: Any, media: MediaAttachment, startPositionMs: Long = 0, muted: Boolean = true) {
        val urls = media.viewUrls
        val p = player ?: createPlayer().also {
            it.addListener(listener)
            player = it
        }
        this.media = media
        urlIndex = 0
        failed = urls.isEmpty()
        activeOwner = owner
        if (failed) {
            p.stop()
            p.clearMediaItems()
            return
        }
        changeMuted(muted)
        // gifv は音の無い短い動画なので繰り返す
        p.repeatMode = if (media.type == MediaType.Gifv) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.setMediaItem(MediaItem.fromUri(urls.first()), startPositionMs)
        p.prepare()
        p.playWhenReady = true
    }

    /** 持ち主が [owner] のときだけ止める (他の持ち主の再生には触れない)。 */
    fun stop(owner: Any) {
        if (activeOwner === owner) stopActive()
    }

    /** 持ち主に関係なく止める。 */
    fun stopActive() {
        activeOwner = null
        media = null
        failed = false
        player?.run {
            stop()
            clearMediaItems()
        }
    }

    fun pause() {
        player?.pause()
    }

    fun changeMuted(muted: Boolean) {
        val p = player ?: return
        // mute() は直前の音量を覚えて unmute() で戻すので、同じ状態での重複呼び出しは避ける
        if (muted && p.volume != 0f) p.mute()
        if (!muted && p.volume == 0f) p.unmute()
        this.muted = muted
        updateAudioFocus()
    }

    /** [owner] が持ち主なら、ビューアへ引き継ぐ再生位置とミュート状態。 */
    fun snapshot(owner: Any): PlaybackSnapshot? {
        if (activeOwner !== owner) return null
        val p = player ?: return null
        return PlaybackSnapshot(positionMs = p.currentPosition.coerceAtLeast(0), muted = muted)
    }

    /** Player を解放する。次の [play] で作り直す。 */
    fun release() {
        activeOwner = null
        media = null
        failed = false
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
    }

    /**
     * ミュート中は音声フォーカスを取らない (他のアプリの音楽を止めない)。
     * 音を出すときだけフォーカスを取り、イヤホンが抜けたら一時停止する。
     */
    private fun updateAudioFocus() {
        val p = player ?: return
        p.setAudioAttributes(MediaAudioAttributes, /* handleAudioFocus = */ !muted)
        p.setHandleAudioBecomingNoisy(!muted)
    }
}
