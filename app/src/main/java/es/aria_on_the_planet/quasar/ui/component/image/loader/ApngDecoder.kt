package es.aria_on_the_planet.quasar.ui.component.image.loader

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log
import android.util.LruCache
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.linecorp.apng.ApngDrawable
import com.linecorp.apng.decoder.ApngException
import kotlinx.coroutines.runInterruptible
import java.io.IOException

/**
 * APNG を LINE の apng-drawable で読むデコーダ。GIF / Motion WebP は Android の ImageDecoder (coil-gif) が読むが、
 * APNG は ImageDecoder がアニメーションしないため。普通の PNG・2 フレーム未満の APNG・大きすぎる APNG は扱わず、
 * Coil 標準のデコーダに回す (先頭 (既定) のフレームの静止画になる)。
 *
 * 再生はライブラリに任せず [ApngAnimationDrawable] が行う ([ApngDrawable] を再生させるとコマが変わらなくても
 * 毎フレーム描き直すため)。apng-drawable は全フレームを先に展開するので、同じ画像を何度も
 * 展開しないよう [ApngFrameCache] で使い回す。`com.linecorp.apng` を触るのはこのファイルと [ApngAnimationDrawable] だけにする。
 */
internal class ApngDecoderFactory(private val cache: ApngFrameCache) : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
        val info = readApngInfo(result.source.source()) ?: return null
        if (info.decodedByteCount > MaxDecodedBytes) return null
        return ApngDecoder(result.source, cacheKey = options.diskCacheKey, maxFps = options.animationMaxFps, cache)
    }
}

private class ApngDecoder(
    private val source: ImageSource,
    private val cacheKey: String?,
    private val maxFps: Int?,
    private val cache: ApngFrameCache,
) : Decoder {
    override suspend fun decode(): DecodeResult? = runInterruptible {
        val original = cacheKey?.let(cache::get)
            ?: decodeOrNull()?.also { decoded -> cacheKey?.let { cache.put(it, decoded) } }
            ?: return@runInterruptible null
        // newDrawable() (内部で Apng.copy()) はフレームの中身を共有し、描画用のビットマップ (1 フレーム分) だけを
        // 新しく持つ。再生位置は表示ごとに別
        val frames = original.constantState?.newDrawable() as? ApngDrawable ?: return@runInterruptible null
        DecodeResult(image = ApngAnimationDrawable(frames, maxFps).asImage(), isSampled = false)
    }

    /** 失敗したら `null` で次のデコーダに回すので、[source] は消費せず peek で読む。 */
    private fun decodeOrNull(): ApngDrawable? = try {
        source.source().peek().inputStream().use { ApngDrawable.decode(it) }
    } catch (_: ApngException) {
        null
    } catch (_: IOException) {
        null
    }
}

/**
 * 展開済みの APNG (URL ごと) のキャッシュ。表示には `newDrawable()` で作った別インスタンスを使い、ここにある元のインスタンスは
 * 描かない。追い出したものは参照が無くなれば apng-drawable 側で解放される (表示中のコピーがフレームを共有している
 * 可能性があるので、ここでは `recycle()` しない)。
 *
 * Coil のメモリキャッシュと同じく、バックグラウンドに回ってメモリが足りなくなったら ([ComponentCallbacks2]) 空にする。
 */
internal class ApngFrameCache(maxBytes: Int = DefaultCacheBytes) : ComponentCallbacks2 {
    private val cache = object : LruCache<String, ApngDrawable>(maxBytes) {
        override fun sizeOf(key: String, value: ApngDrawable): Int =
            value.allocationByteCount.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    fun get(key: String): ApngDrawable? = cache.get(key)

    fun put(key: String, drawable: ApngDrawable) {
        cache.put(key, drawable)
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) clear("trimMemory($level)")
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() = clear("lowMemory")

    private fun clear(reason: String) {
        val before = cache.size()
        cache.evictAll()
        Log.d(TAG, "Cleared on $reason: $before bytes")
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit
}

private const val TAG = "ApngFrameCache"

/** 1 枚あたりの展開後の上限。超える APNG はアニメーションさせない (大きな添付画像でメモリを使い切らないため)。 */
private const val MaxDecodedBytes = 16L * 1024 * 1024

private const val DefaultCacheBytes = 32 * 1024 * 1024
