package es.aria_on_the_planet.quasar.data.media

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * 送る前の画像の手直し。どちらも画像だけが対象で、[ImagePrepareOptions] で選ぶ:
 *
 * * 上限 ([ImageUploadLimits]) に合わない画像を作り直す ([ImagePrepareOptions.compressOversized])。WebP / JPEG で、大きさ (縦横)
 *   は変えず、画質だけを [ReencodeQualities] の順に下げる。作り直すとメタデータ (EXIF 等) も消える。
 * * メタデータ (EXIF の位置情報等) を除く ([ImagePrepareOptions.stripMetadata])。上限に合っているものは作り直さず、入れ物
 *   (JPEG / PNG / WebP) からメタデータだけを除く ([stripImageMetadata])。それができない形式 (HEIC 等) は作り直すしかないので
 *   作り直す (アニメーションするものは除く)。GIF はメタデータを持たないのでそのまま。
 *
 * 判断と入れ物の操作は純粋関数 (`ImageReencode.kt` / `ImageMetadata.kt`) で、ここはデコード・エンコードと組み立てだけ。
 * 手直しできなかったものは元のまま返し、これまでどおり送る前の検査かサーバで断られる。結果はメモリに持つ (一時ファイルを
 * 作らないので後始末が要らない)。
 */
class ImageReencoder {
    suspend fun prepare(source: UploadSource, limits: ImageUploadLimits, options: ImagePrepareOptions): UploadSource {
        if (source.mimeType?.startsWith("image/") != true) return source
        if (!options.compressOversized && !options.stripMetadata) return source
        val knownSize = source.size
        if (!options.stripMetadata && knownSize != null && !needsReencode(source.mimeType, knownSize, limits)) return source
        return withContext(Dispatchers.Default) {
            try {
                val bytes = withContext(Dispatchers.IO) { source.open().use { it.readBytes() } }
                prepareBytes(source, bytes, limits, options)
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Not enough memory to prepare an image (${source.mimeType})")
                source
            } catch (e: Exception) {
                Log.w(TAG, "Failed to prepare an image (${source.mimeType})", e)
                source
            }
        }
    }

    private fun prepareBytes(
        source: UploadSource,
        bytes: ByteArray,
        limits: ImageUploadLimits,
        options: ImagePrepareOptions,
    ): UploadSource {
        val container = imageContainerOf(bytes)
        // 上限に合わないものは作り直す (メタデータも消える)
        if (options.compressOversized && needsReencode(source.mimeType, bytes.size.toLong(), limits) && !isAnimatedPng(bytes)) {
            reencode(source, bytes, limits)?.let { return it }
        }
        if (!options.stripMetadata) return source
        return when (container) {
            ImageContainer.Jpeg, ImageContainer.Png, ImageContainer.Webp -> {
                val orientation = if (container == ImageContainer.Jpeg) orientationOf(bytes) else ExifInterface.ORIENTATION_NORMAL
                val stripped = stripImageMetadata(bytes, orientation) ?: return source
                Log.i(TAG, "Removed image metadata: ${bytes.size} -> ${stripped.size} bytes (${source.mimeType})")
                EncodedUploadSource(source.fileName, source.mimeType ?: "image/*", stripped, source.previewUri)
            }
            ImageContainer.Gif -> source
            // HEIC 等は入れ物を直せないので作り直す (上限は満たしたまま)
            ImageContainer.Other -> reencode(source, bytes, limits) ?: source
        }
    }

    /** JPEG の EXIF の向き。読めなければ向きなし。 */
    private fun orientationOf(bytes: ByteArray): Int = try {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun reencode(source: UploadSource, bytes: ByteArray, limits: ImageUploadLimits): UploadSource? {
        var animated = false
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            animated = info.isAnimated
            // compress に使うので、ハードウェアのビットマップ (読めない) にしない
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            if (animated) return null
            val format = reencodeFormat(limits.acceptedMimeTypes, bitmap.hasAlpha()) ?: return null
            for (quality in ReencodeQualities) {
                val out = ByteArrayOutputStream()
                bitmap.compress(format.compressFormat, quality, out)
                if (fitsSize(out.size().toLong(), limits)) {
                    Log.i(TAG, "Re-encoded an image: ${bytes.size} -> ${out.size()} bytes (${format.mimeType}, quality $quality)")
                    return EncodedUploadSource(
                        fileName = reencodedFileName(source.fileName, format),
                        mimeType = format.mimeType,
                        bytes = out.toByteArray(),
                        previewUri = source.previewUri,
                    )
                }
            }
            Log.i(TAG, "Re-encoding did not fit the limit (${bytes.size} bytes)")
            return null
        } finally {
            bitmap.recycle()
        }
    }

    private val ReencodeFormat.compressFormat: Bitmap.CompressFormat
        get() = when (this) {
            ReencodeFormat.Webp -> Bitmap.CompressFormat.WEBP_LOSSY
            ReencodeFormat.Jpeg -> Bitmap.CompressFormat.JPEG
        }

    private companion object {
        const val TAG = "ImageReencoder"
    }
}

/** 端末で作り直した画像 (メモリ上)。サムネイルには元の画像 ([previewUri]) を使う。 */
private class EncodedUploadSource(
    override val fileName: String,
    override val mimeType: String,
    private val bytes: ByteArray,
    override val previewUri: String?,
) : UploadSource {
    override val size: Long = bytes.size.toLong()

    override fun open(): InputStream = ByteArrayInputStream(bytes)
}
