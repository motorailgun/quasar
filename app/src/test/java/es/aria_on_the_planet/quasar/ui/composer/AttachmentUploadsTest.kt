package es.aria_on_the_planet.quasar.ui.composer

import es.aria_on_the_planet.quasar.data.media.UploadSource
import es.aria_on_the_planet.quasar.domain.model.ComposeLimits
import es.aria_on_the_planet.quasar.domain.model.DraftAttachment
import es.aria_on_the_planet.quasar.domain.model.UploadFailure
import es.aria_on_the_planet.quasar.domain.model.UploadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStream

class AttachmentUploadsTest {
    private class FakeSource(
        override val mimeType: String? = "image/png",
        override val size: Long? = 10,
    ) : UploadSource {
        override val fileName = "a.png"
        override val previewUri = "content://a"
        override fun open(): InputStream = InputStream.nullInputStream()
    }

    /** 送信 1 回分。[result] を完了させるまで送信中のまま。 */
    private class Call(val description: String?, val onProgress: (Float?) -> Unit, val onProcessing: (String) -> Unit) {
        val result = CompletableDeferred<String>()
    }

    private inner class Harness(
        scope: CoroutineScope,
        limits: ComposeLimits = ComposeLimits(),
        classify: (Throwable) -> UploadFailure = { UploadFailure.Failed },
        prepare: (suspend (UploadSource, ComposeLimits) -> UploadSource)? = null,
    ) {
        var list = emptyList<DraftAttachment>()
        val calls = mutableListOf<Call>()
        val sent = mutableListOf<UploadSource>()
        val descriptionUpdates = mutableListOf<Pair<String, String>>()
        private var nextId = 0
        val uploads = AttachmentUploads(
            scope = scope,
            attachments = { list },
            setAttachments = { list = it },
            limits = { limits },
            upload = { source, description, onProgress, onProcessing ->
                sent += source
                Call(description, onProgress, onProcessing).also { calls += it }.result.await()
            },
            updateDescription = { id, description -> descriptionUpdates += id to description },
            newId = { "l${nextId++}" },
            classifyFailure = classify,
            prepare = prepare,
        )
        fun state(index: Int = 0) = list[index].state
    }

    private suspend fun settle() = repeat(5) { yield() }

    /** 終わっていない (偽物の) 送信が残っても待ち続けないよう、最後に止める。 */
    private fun runUploads(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        block()
        coroutineContext.cancelChildren()
    }

    @Test
    fun uploadsAndReportsProgress() = runUploads {
        val h = Harness(this)
        h.uploads.add(listOf(FakeSource()))
        settle()
        assertEquals(UploadState.Uploading(null), h.state())
        h.calls[0].onProgress(0.5f)
        settle()
        assertEquals(UploadState.Uploading(0.5f), h.state())
        h.calls[0].result.complete("m1")
        settle()
        assertEquals(UploadState.Uploaded("m1"), h.state())
        // 完了後に遅れて届いた進み具合は無視する
        h.calls[0].onProgress(0.9f)
        settle()
        assertEquals(UploadState.Uploaded("m1"), h.state())
    }

    @Test
    fun processingThenUploaded() = runUploads {
        val h = Harness(this)
        h.uploads.add(listOf(FakeSource(mimeType = "video/mp4")))
        settle()
        h.calls[0].onProcessing("m1")
        assertEquals(UploadState.Processing("m1"), h.state())
        h.calls[0].result.complete("m1")
        settle()
        assertEquals(UploadState.Uploaded("m1"), h.state())
    }

    @Test
    fun rejectsBeforeSendingWhenOverLimits() = runUploads {
        val h = Harness(this, ComposeLimits(imageSizeLimit = 5, supportedMimeTypes = setOf("image/png", "video/mp4")))
        h.uploads.add(listOf(FakeSource(size = 6), FakeSource(mimeType = "image/heic")))
        settle()
        assertEquals(UploadState.Failed(UploadFailure.TooLarge), h.state(0))
        assertEquals(UploadState.Failed(UploadFailure.Unsupported), h.state(1))
        assertTrue(h.calls.isEmpty())
        // 上限に合わないものは送り直さない
        h.uploads.retry("l0")
        settle()
        assertTrue(h.calls.isEmpty())
    }

    @Test
    fun dropsSourcesBeyondMaxAttachments() = runUploads {
        val h = Harness(this, ComposeLimits(maxMediaAttachments = 2))
        h.uploads.add(listOf(FakeSource(), FakeSource(), FakeSource()))
        settle()
        assertEquals(2, h.list.size)
        h.uploads.add(listOf(FakeSource()))
        assertEquals(2, h.list.size)
    }

    @Test
    fun failureCanBeRetried() = runUploads {
        val h = Harness(this)
        h.uploads.add(listOf(FakeSource()))
        settle()
        h.calls[0].result.completeExceptionally(IOException("boom"))
        settle()
        assertEquals(UploadState.Failed(UploadFailure.Failed), h.state())
        h.uploads.retry("l0")
        settle()
        assertEquals(2, h.calls.size)
        h.calls[1].result.complete("m2")
        settle()
        assertEquals(UploadState.Uploaded("m2"), h.state())
    }

    @Test
    fun removeCancelsUpload() = runUploads {
        val h = Harness(this)
        h.uploads.add(listOf(FakeSource()))
        settle()
        h.uploads.remove("l0")
        settle()
        assertTrue(h.list.isEmpty())
        assertTrue(h.calls[0].result.isCancelled || !h.calls[0].result.isCompleted)
    }

    @Test
    fun descriptionSentWithUploadOrSyncedAfter() = runUploads {
        val h = Harness(this)
        h.uploads.add(listOf(FakeSource(), FakeSource()))
        settle()
        // 送信中に変えた分は、完了後に反映する
        h.uploads.setDescription("l0", "a cat")
        settle()
        h.calls[0].result.complete("m0")
        settle()
        assertEquals(listOf("m0" to "a cat"), h.descriptionUpdates)

        // 完了後に変えたらすぐ反映、同じ内容なら送らない
        h.calls[1].result.complete("m1")
        settle()
        h.uploads.setDescription("l1", "a dog")
        settle()
        h.uploads.setDescription("l1", "a dog")
        settle()
        assertEquals(listOf("m0" to "a cat", "m1" to "a dog"), h.descriptionUpdates)
    }

    @Test
    fun descriptionSetBeforeStartGoesWithUpload() = runUploads {
        val h = Harness(this)
        h.uploads.add(listOf(FakeSource()))
        // 送信が始まる前に変えた分はアップロードに含め、後から更新しない
        h.uploads.setDescription("l0", "a cat")
        settle()
        assertEquals("a cat", h.calls[0].description)
        h.calls[0].result.complete("m0")
        settle()
        assertTrue(h.descriptionUpdates.isEmpty())
    }

    @Test
    fun retryIgnoresAttachmentsThatHaveNotFailed() = runUploads {
        val h = Harness(this)
        h.uploads.add(listOf(FakeSource()))
        settle()
        h.uploads.retry("l0")
        settle()
        assertEquals(1, h.calls.size)
    }

    @Test
    fun nonRetryableFailureIsNotRetried() = runUploads {
        val h = Harness(this, classify = { UploadFailure.NotAllowed })
        h.uploads.add(listOf(FakeSource()))
        settle()
        h.calls[0].result.completeExceptionally(IOException("403"))
        settle()
        assertEquals(UploadState.Failed(UploadFailure.NotAllowed), h.state())
        h.uploads.retry("l0")
        settle()
        assertEquals(1, h.calls.size)
    }

    @Test
    fun preparedSourceIsCheckedAndSent() = runUploads {
        val limits = ComposeLimits(imageSizeLimit = 50)
        val small = FakeSource(size = 10)
        val h = Harness(this, limits, prepare = { _, l ->
            assertEquals(limits, l)
            small
        })
        // そのままでは上限を超えるが、作り直したものは収まる
        h.uploads.add(listOf(FakeSource(size = 100)))
        settle()
        assertEquals(UploadState.Uploading(null), h.state())
        assertEquals(listOf<UploadSource>(small), h.sent)
    }

    @Test
    fun retryDoesNotPrepareAgain() = runUploads {
        var prepared = 0
        val h = Harness(this, prepare = { source, _ -> prepared++; source })
        h.uploads.add(listOf(FakeSource()))
        settle()
        h.calls[0].result.completeExceptionally(IOException("network"))
        settle()
        assertEquals(UploadState.Failed(UploadFailure.Failed), h.state())
        h.uploads.retry(h.list[0].localId)
        settle()
        assertEquals(2, h.calls.size)
        assertEquals(1, prepared)
    }

    @Test
    fun failedPrepareFallsBackToOriginal() = runUploads {
        val h = Harness(this, ComposeLimits(imageSizeLimit = 50), prepare = { _, _ -> throw IOException("decode") })
        h.uploads.add(listOf(FakeSource(size = 100)))
        settle()
        // 元のまま検査するので上限を超えて失敗
        assertEquals(UploadState.Failed(UploadFailure.TooLarge), h.state())
        assertTrue(h.calls.isEmpty())
    }
}
