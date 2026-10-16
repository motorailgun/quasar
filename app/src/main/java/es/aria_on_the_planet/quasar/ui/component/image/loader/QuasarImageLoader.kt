package es.aria_on_the_planet.quasar.ui.component.image.loader

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import coil3.video.VideoFrameDecoder
import okhttp3.Call

/**
 * アプリ全体で使う Coil の ImageLoader。
 *
 * アニメーション画像は [AnimationGate] 越しのデコーダが読む: APNG は [ApngDecoderFactory]、GIF / Motion WebP は
 * coil-gif の [AnimatedImageDecoder] (Android の ImageDecoder)。リクエストで止められていれば Coil 標準の
 * デコーダが静止画として読む。
 *
 * coil-gif / coil-video は ServiceLoader で自動登録されるが、自動登録された coil-gif は [AnimationGate] を
 * 通らずにアニメーションさせてしまうので、自動登録は切ってすべてここで並べる。
 */
internal fun newQuasarImageLoader(context: PlatformContext, callFactory: () -> Call.Factory): ImageLoader {
    // 展開済み APNG のキャッシュ。メモリが足りなくなったら空にする (Coil のメモリキャッシュは Coil 自身が空にする)
    val apngCache = ApngFrameCache().also { context.applicationContext.registerComponentCallbacks(it) }
    return ImageLoader.Builder(context)
        .serviceLoaderEnabled(false)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = callFactory))
            add(AnimationGate(ApngDecoderFactory(apngCache)))
            add(AnimationGate(AnimatedImageDecoder.Factory()))
            // 投稿画面で添付する動画のサムネイル (content:// の動画から 1 コマ取り出す)
            add(VideoFrameDecoder.Factory())
        }
        .build()
}
