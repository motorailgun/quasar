package es.aria_on_the_planet.quasar.data.media

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * [source] を流しながら送る [RequestBody]。送った量を [onProgress] (0〜1、サイズ不明なら `null`) で知らせる。
 * 知らせるのは 1% 刻みで変わったときだけ。
 */
class ProgressRequestBody(
    private val source: UploadSource,
    private val onProgress: (Float?) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = source.mimeType?.toMediaTypeOrNull()

    override fun contentLength(): Long = source.size ?: -1

    override fun writeTo(sink: BufferedSink) {
        val total = source.size
        var sent = 0L
        var lastPercent = -1
        onProgress(if (total != null) 0f else null)
        source.open().source().use { input ->
            while (true) {
                val read = input.read(sink.buffer, SegmentSize)
                if (read == -1L) break
                sink.emit()
                sent += read
                if (total != null && total > 0) {
                    val percent = (sent * 100 / total).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress((sent.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        }
    }

    private companion object {
        const val SegmentSize = 64L * 1024
    }
}
