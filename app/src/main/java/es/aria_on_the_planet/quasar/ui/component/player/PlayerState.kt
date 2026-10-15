package es.aria_on_the_planet.quasar.ui.component.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import java.util.Locale

/** [player] が再生中か。`null` なら常に `false`。 */
@Composable
fun rememberIsPlaying(player: Player?): State<Boolean> {
    val state = remember(player) { mutableStateOf(player?.isPlaying == true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.value = isPlaying
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    return state
}

/** 再生中のメディアに音声トラックがあるか (gifv には無い)。トラックが分かるまでは `false`。 */
@Composable
fun rememberHasAudio(player: Player?): State<Boolean> {
    val state = remember(player) { mutableStateOf(player?.currentTracks?.hasAudio() == true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                state.value = tracks.hasAudio()
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    return state
}

private fun Tracks.hasAudio(): Boolean = containsType(C.TRACK_TYPE_AUDIO)

/** 再生時間の表示 (`m:ss`、1 時間以上は `h:mm:ss`)。負の値は 0 とみなす。 */
fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

/**
 * 全体の長さ (ms)。Player がまだ長さを知らない ([C.TIME_UNSET]) 間は API の `meta` の長さを使う。
 * どちらも無ければ `null`。
 */
fun resolveDurationMs(playerDurationMs: Long, metaDurationSeconds: Double?): Long? = when {
    playerDurationMs != C.TIME_UNSET && playerDurationMs >= 0 -> playerDurationMs
    metaDurationSeconds != null && metaDurationSeconds > 0 -> (metaDurationSeconds * 1000).toLong()
    else -> null
}
