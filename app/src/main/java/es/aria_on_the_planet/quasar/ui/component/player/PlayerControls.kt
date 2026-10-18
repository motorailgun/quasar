package es.aria_on_the_planet.quasar.ui.component.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberMuteButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import kotlinx.coroutines.delay

/**
 * 一覧 (インライン再生) 用の操作。左下に残り時間、右下にミュートボタン (音声があるときのみ)。
 * 再生 / 停止やビューアを開く操作はメディア全体のタップで行うので、ここには置かない。
 */
@OptIn(UnstableApi::class)
@Composable
fun BoxScope.InlineControls(player: Player, media: MediaAttachment) {
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 1000)
    val duration = resolveDurationMs(progress.durationMs, media.meta?.durationSeconds)
    if (duration != null) {
        Text(
            text = formatPlaybackTime(duration - progress.currentPositionMs),
            style = MaterialTheme.typography.labelSmall,
            color = MediaOverlayColors.Content,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(MediaOverlayColors.Scrim, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
    MuteToggle(
        player = player,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
            .size(32.dp)
            .background(MediaOverlayColors.Scrim, CircleShape),
        iconSize = 18.dp,
    )
}

/**
 * メディアビューア用の操作。
 *
 * * 1 回タップで操作部分の表示 / 非表示 ([onVisibleChange])。再生中は [AutoHideMillis] で自動的に隠す。
 * * 左右 1/3 のダブルタップで ±10 秒 (中央のダブルタップは何もしない)。
 * * 下端に 再生 / 一時停止・「現在 / 全体」・ミュート、その下にシークバー ([BottomBar])。
 *
 * PiP では [visible] を `false` にして使う想定 (ジェスチャ以外は出ない)。
 */
@OptIn(UnstableApi::class)
@Composable
fun FullControls(
    player: Player,
    media: MediaAttachment,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlaying by rememberIsPlaying(player)
    // pointerInput の中から最新の値を読む
    val currentVisible by rememberUpdatedState(visible)
    val currentOnVisibleChange by rememberUpdatedState(onVisibleChange)
    // 操作のたびに増やし、自動で隠すまでの時間を延ばす
    var interaction by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible, isPlaying, interaction) {
        if (visible && isPlaying) {
            delay(AutoHideMillis)
            currentOnVisibleChange(false)
        }
    }
    var seekFeedback by remember { mutableStateOf<SeekFeedback?>(null) }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(SeekFeedbackMillis)
            seekFeedback = null
        }
    }

    Box(modifier.fillMaxSize()) {
        // ジェスチャ層は一番下 (シークバー等の操作を先に受け取らせる)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    detectTapGestures(
                        onTap = { currentOnVisibleChange(!currentVisible) },
                        onDoubleTap = { offset ->
                            when (seekZoneOf(offset.x, size.width.toFloat())) {
                                SeekZone.Backward -> {
                                    player.seekBack()
                                    seekFeedback = SeekFeedback(SeekZone.Backward, (seekFeedback?.takeIf { it.zone == SeekZone.Backward }?.count ?: 0) + 1)
                                }
                                SeekZone.Forward -> {
                                    player.seekForward()
                                    seekFeedback = SeekFeedback(SeekZone.Forward, (seekFeedback?.takeIf { it.zone == SeekZone.Forward }?.count ?: 0) + 1)
                                }
                                SeekZone.None -> Unit
                            }
                            interaction++
                        },
                    )
                },
        )
        seekFeedback?.let { feedback ->
            SeekFeedbackLabel(
                feedback,
                Modifier
                    .align(if (feedback.zone == SeekZone.Backward) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 48.dp),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(player, media, onInteraction = { interaction++ })
        }
    }
}

/**
 * 下端の操作 (Google フォトの動画再生を参考に控えめに): 1 行目に 再生/一時停止 · 「現在 / 全体」(中央) · ミュート、
 * その下に細いシークバー。背景は帯にせず、下から薄い黒のグラデーションだけを敷く。
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomBar(player: Player, media: MediaAttachment, onInteraction: () -> Unit) {
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 250)
    val duration = resolveDurationMs(progress.durationMs, media.meta?.durationSeconds)
    val seekBar = rememberSeekBarState()
    // 再生位置に追従させる。つまみを触っている間はシークバー側の位置 (離したらそこへシーク) を優先する
    LaunchedEffect(seekBar, progress, duration) {
        snapshotFlow { playbackFraction(progress.currentPositionMs, duration) }
            .collect { if (!seekBar.isInteracting) seekBar.slider.value = it }
    }
    val positionMs = if (seekBar.isInteracting && duration != null) {
        (duration * seekBar.slider.value).toLong()
    } else {
        progress.currentPositionMs
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, MediaOverlayColors.GradientScrim)))
            // 操作部分を隠すときはシステムバーも隠すので、表示状態に関係なくバーの分を空ける
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
            .padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PlayPauseToggle(player, onClick = onInteraction, modifier = Modifier.align(Alignment.CenterStart))
            Text(
                text = stringResource(
                    R.string.player_time,
                    formatPlaybackTime(positionMs),
                    duration?.let(::formatPlaybackTime) ?: stringResource(R.string.player_time_unknown),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MediaOverlayColors.Content,
            )
            MuteToggle(player, onClick = onInteraction, modifier = Modifier.align(Alignment.CenterEnd))
        }
        SeekBar(
            state = seekBar,
            enabled = duration != null && duration > 0,
            onValueChange = { onInteraction() },
            onValueChangeFinished = {
                if (duration != null) player.seekTo((duration * seekBar.slider.value).toLong())
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** 再生位置の割合 (0〜1)。長さが分からなければ 0。 */
internal fun playbackFraction(positionMs: Long, durationMs: Long?): Float =
    if (durationMs == null || durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

/** シークバーの状態。[slider] の値はつまみの位置 (0〜1)、[isInteracting] は押している / ドラッグしている間。 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
private class SeekBarState(
    val slider: SliderState,
    val interactionSource: MutableInteractionSource,
    private val pressed: State<Boolean>,
    private val dragged: State<Boolean>,
) {
    val isInteracting: Boolean get() = pressed.value || dragged.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberSeekBarState(): SeekBarState {
    val slider = rememberSliderState()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState()
    val dragged = interactionSource.collectIsDraggedAsState()
    return remember(slider, interactionSource) { SeekBarState(slider, interactionSource, pressed, dragged) }
}

/** 細いシークバー (M3 の Slider のトラックを細く、つまみを短いバーにしたもの)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(
    state: SeekBarState,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SliderDefaults.colors(
        thumbColor = MediaOverlayColors.Content,
        activeTrackColor = MediaOverlayColors.Content,
        inactiveTrackColor = MediaOverlayColors.ContentInactive,
        disabledThumbColor = MediaOverlayColors.ContentInactive,
        disabledActiveTrackColor = MediaOverlayColors.ContentInactive,
        disabledInactiveTrackColor = MediaOverlayColors.ContentInactive,
    )
    Slider(
        state = state.slider,
        onValueChange = {
            state.slider.value = it
            onValueChange(it)
        },
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        colors = colors,
        interactionSource = state.interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = state.interactionSource,
                colors = colors,
                enabled = enabled,
                thumbSize = DpSize(4.dp, 20.dp),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(4.dp),
                colors = colors,
                enabled = enabled,
                drawStopIndicator = null,
                thumbTrackGapSize = 4.dp,
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayPauseToggle(player: Player, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberPlayPauseButtonState(player)
    IconButton(
        onClick = {
            state.onClick()
            onClick()
        },
        enabled = state.isEnabled,
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (state.showPlay) Symbols.Filled.PlayArrow else Symbols.Filled.Pause,
            contentDescription = stringResource(if (state.showPlay) R.string.player_play else R.string.player_pause),
            tint = MediaOverlayColors.Content,
        )
    }
}

/** ミュート切り替え。音声トラックが無い (gifv 等) 間は出さない。 */
@OptIn(UnstableApi::class)
@Composable
private fun MuteToggle(
    player: Player,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit = {},
) {
    val hasAudio by rememberHasAudio(player)
    if (!hasAudio) return
    val state = rememberMuteButtonState(player)
    IconButton(
        onClick = {
            state.onClick()
            onClick()
        },
        enabled = state.isEnabled,
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (state.showMuted) Symbols.Outlined.VolumeOff else Symbols.Outlined.VolumeUp,
            contentDescription = stringResource(if (state.showMuted) R.string.player_unmute else R.string.player_mute),
            tint = MediaOverlayColors.Content,
            modifier = Modifier.size(iconSize),
        )
    }
}

private data class SeekFeedback(val zone: SeekZone, val count: Int)

@Composable
private fun SeekFeedbackLabel(feedback: SeekFeedback, modifier: Modifier = Modifier) {
    val seconds = SeekIncrementMs / 1000 * feedback.count
    Text(
        text = if (feedback.zone == SeekZone.Backward) "−${seconds}s" else "+${seconds}s",
        style = MaterialTheme.typography.titleMedium,
        color = MediaOverlayColors.Content,
        modifier = modifier
            .background(MediaOverlayColors.Scrim, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

enum class SeekZone { Backward, None, Forward }

/** ダブルタップの位置 [x] (幅 [width] 中) から、戻す / 送る / 何もしない を決める。左右 1/3 ずつ。 */
fun seekZoneOf(x: Float, width: Float): SeekZone = when {
    width <= 0f -> SeekZone.None
    x < width / 3f -> SeekZone.Backward
    x > width * 2f / 3f -> SeekZone.Forward
    else -> SeekZone.None
}

private const val AutoHideMillis = 3_000L
private const val SeekFeedbackMillis = 700L
