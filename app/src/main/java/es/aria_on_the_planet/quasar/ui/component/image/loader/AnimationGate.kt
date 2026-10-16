package es.aria_on_the_planet.quasar.ui.component.image.loader

import coil3.Extras
import coil3.ImageLoader
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.getExtra
import coil3.request.ImageRequest
import coil3.request.Options

private val AnimateKey = Extras.Key(default = true)
private val MaxFpsKey = Extras.Key<Int?>(default = null)

/**
 * このリクエストの画像をアニメーションさせるか。`false` ならアニメーション用のデコーダ ([AnimationGate]) を通さず、
 * Coil 標準のデコーダが先頭 (既定) のフレームだけを静止画として読む。
 *
 * 同じ URL でも静止画とアニメーションで結果が変わるので、メモリキャッシュのキーも分ける
 * (分けないと設定を切り替えても前の結果が出たままになる)。
 */
internal fun ImageRequest.Builder.animate(enabled: Boolean, maxFps: Int? = null): ImageRequest.Builder = apply {
    extras[AnimateKey] = enabled
    extras[MaxFpsKey] = maxFps
    memoryCacheKeyExtra("quasar.animate", if (enabled) null else "false")
}

/**
 * [animate] で指定した描き直しの上限 (fps)。対応しているのは自前で再生する APNG ([ApngAnimationDrawable]) だけ。
 * アニメーションの結果はメモリキャッシュに入らないので、キャッシュのキーには含めない。
 */
internal val Options.animationMaxFps: Int? get() = getExtra(MaxFpsKey)

/** [animate] で止められたリクエストでは [delegate] を使わず、次のデコーダに回す。 */
internal class AnimationGate(private val delegate: Decoder.Factory) : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? =
        if (options.getExtra(AnimateKey)) delegate.create(result, options, imageLoader) else null
}
