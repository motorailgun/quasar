package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSaveTest {
    private fun media(
        type: MediaType = MediaType.Image,
        id: String = "1234",
        url: String? = "https://local.example/system/media_attachments/files/1/2/3/original/abc.PNG",
        remoteUrl: String? = null,
    ) = MediaAttachment(id, type, url, remoteUrl, null, null, null, null)

    @Test
    fun usesHostIdAndExtensionOfOriginal() {
        val target = mediaSaveTarget(media(remoteUrl = "https://remote.example/files/x.webp?v=1"))!!
        assertEquals("https://remote.example/files/x.webp?v=1", target.url)
        assertEquals("remote.example_1234.webp", target.fileName)
        assertEquals("Quasar/remote.example_1234.webp", target.subPath)
        assertEquals(SaveDirectory.Pictures, target.directory)
        assertEquals("image/webp", target.mimeType)
    }

    @Test
    fun lowercasesExtension() {
        val target = mediaSaveTarget(media())!!
        assertEquals("local.example_1234.png", target.fileName)
        assertEquals("image/png", target.mimeType)
    }

    @Test
    fun choosesDirectoryByType() {
        assertEquals(SaveDirectory.Pictures, mediaSaveTarget(media(MediaType.Video))!!.directory)
        assertEquals(SaveDirectory.Pictures, mediaSaveTarget(media(MediaType.Gifv))!!.directory)
        assertEquals(SaveDirectory.Music, mediaSaveTarget(media(MediaType.Audio))!!.directory)
        assertEquals(SaveDirectory.Downloads, mediaSaveTarget(media(MediaType.Unknown))!!.directory)
    }

    @Test
    fun fallsBackToTypeExtensionWithoutOne() {
        val noExt = "https://e.example/media/abc"
        assertEquals("e.example_1234.jpg", mediaSaveTarget(media(url = noExt))!!.fileName)
        assertEquals("e.example_1234.mp4", mediaSaveTarget(media(MediaType.Gifv, url = noExt))!!.fileName)
        assertEquals("video/mp4", mediaSaveTarget(media(MediaType.Video, url = noExt))!!.mimeType)
        val unknown = mediaSaveTarget(media(MediaType.Unknown, url = noExt))!!
        assertEquals("e.example_1234", unknown.fileName)
        assertNull(unknown.mimeType)
        // ドットがディレクトリ側にしか無い・拡張子らしくないものは使わない
        assertEquals("e.example_1234.jpg", mediaSaveTarget(media(url = "https://e.example/v1.2/abc"))!!.fileName)
        assertEquals("e.example_1234.jpg", mediaSaveTarget(media(url = "https://e.example/abc.toolong"))!!.fileName)
    }

    @Test
    fun sanitizesSyntheticIds() {
        val target = mediaSaveTarget(media(id = "avatar:1 2/3", url = "https://e.example/a.gif"))!!
        assertEquals("e.example_avatar_1_2_3.gif", target.fileName)
    }

    @Test
    fun nullWithoutUrl() {
        assertNull(mediaSaveTarget(media(url = null)))
    }
}
