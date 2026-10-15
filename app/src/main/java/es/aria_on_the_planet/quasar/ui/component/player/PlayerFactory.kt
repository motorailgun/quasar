package es.aria_on_the_planet.quasar.ui.component.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient

/** メディア再生用の [ExoPlayer] を作る。通信はアプリ共通の [OkHttpClient] を使い、シーク幅は ±10 秒。 */
@OptIn(UnstableApi::class)
fun createMediaPlayer(context: Context, okHttpClient: OkHttpClient): ExoPlayer =
    ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(okHttpClient)))
        .setSeekBackIncrementMs(SeekIncrementMs)
        .setSeekForwardIncrementMs(SeekIncrementMs)
        .setAudioAttributes(MediaAudioAttributes, /* handleAudioFocus = */ false)
        .build()

const val SeekIncrementMs = 10_000L

internal val MediaAudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()
