package es.aria_on_the_planet.quasar.ui.mediaviewer

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.MediaMeta
import es.aria_on_the_planet.quasar.domain.model.MediaSize
import es.aria_on_the_planet.quasar.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaViewerLogicTest {
    private val height = 1000f
    private val fling = 3000f

    @Test
    fun dismissesAfterEnoughDistanceInEitherDirection() {
        assertTrue(shouldDismiss(80f, 0f, height, fling))
        assertTrue(shouldDismiss(-80f, 0f, height, fling))
        assertFalse(shouldDismiss(60f, 0f, height, fling))
    }

    @Test
    fun dismissesOnFlingOnlyInDraggedDirection() {
        assertTrue(shouldDismiss(50f, 4000f, height, fling))
        assertTrue(shouldDismiss(-50f, -4000f, height, fling))
        // 下に動かしてから上へ弾いた (戻した) 場合は閉じない
        assertFalse(shouldDismiss(50f, -4000f, height, fling))
        assertFalse(shouldDismiss(50f, 1000f, height, fling))
    }

    @Test
    fun neverDismissesBeforeLayout() {
        assertFalse(shouldDismiss(500f, 9000f, 0f, fling))
    }

    @Test
    fun rubberBandResistsAndNeverExceedsMax() {
        val max = 300f
        assertEquals(0f, rubberBand(0f, max), 0.001f)
        // 動き始めでも指の距離より小さい
        assertTrue(rubberBand(10f, max) < 10f)
        assertTrue(rubberBand(10f, max) > 0f)
        // 向きは保つ
        assertEquals(-rubberBand(200f, max), rubberBand(-200f, max), 0.001f)
        // 指を遠くへ動かすほど進むが、上限は超えない
        assertTrue(rubberBand(2000f, max) > rubberBand(1000f, max))
        assertTrue(rubberBand(100_000f, max) < max)
        assertEquals(0f, rubberBand(100f, 0f), 0.001f)
    }

    @Test
    fun progressReachesOneAtMaxOffset() {
        assertEquals(0f, dismissProgress(0f, 300f), 0.001f)
        assertEquals(0.5f, dismissProgress(-150f, 300f), 0.001f)
        assertEquals(1f, dismissProgress(400f, 300f), 0.001f)
        assertEquals(0f, dismissProgress(100f, 0f), 0.001f)
    }

    @Test
    fun fitsByAspectRatio() {
        val container = Size(1000f, 2000f)
        assertEquals(Size(1000f, 500f), fitSize(2f, container))
        assertEquals(Size(500f, 2000f), fitSize(0.25f, container))
    }

    @Test
    fun exitTransformMovesFittedMediaOntoTarget() {
        val container = Rect(0f, 0f, 1000f, 2000f)
        // 横長 2:1 のメディアは 1000x500 で中央に出ている。一覧では (100, 300) に 200x100
        val target = DismissTarget(Rect(100f, 300f, 300f, 400f), 2f)
        val t = exitTransformTo(target, container)!!
        assertEquals(0.2f, t.scale, 0.001f)
        assertEquals(200f - 500f, t.translationX, 0.001f)
        assertEquals(350f - 1000f, t.translationY, 0.001f)
        // 戻り先の縦横比が少し違っても (Crop で丸めた等) 幅と高さの間の拡大率にする
        val squareish = exitTransformTo(DismissTarget(Rect(0f, 0f, 200f, 200f), 2f), container)!!
        assertTrue(squareish.scale > 0.2f && squareish.scale < 0.4f)
        assertNull(exitTransformTo(target, Rect.Zero))
    }

    @Test
    fun argsRoundTripThroughJson() {
        val media = MediaAttachment(
            id = "m1", type = MediaType.Video, url = "https://e/v.mp4", remoteUrl = "https://r/v.mp4",
            previewUrl = "https://e/v.png",
            meta = MediaMeta(MediaSize(864, 1080), MediaSize(512, 640), 21.7),
            blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj", description = "alt",
        )
        val unknown = MediaAttachment("m2", MediaType.Unknown, null, "https://r/x", null, null, null, null)
        val args = MediaViewerArgs(
            items = listOf(media, unknown).map { it.toViewerMedia() },
            initialIndex = 1, artworkUrl = "https://e/a.png", startPositionMs = 1234, muted = false,
            sourceBounds = mapOf("m1" to ViewerRect(1f, 2f, 3f, 4f)), sourceWindowWidth = 1080, sourceWindowHeight = 2376,
        )
        val decoded = MediaViewerArgs.decode(args.encode())!!
        assertEquals(args, decoded)
        assertEquals(listOf(media, unknown), decoded.items.map { it.toAttachment() })
    }

    @Test
    fun rejectsBrokenArgs() {
        assertEquals(null, MediaViewerArgs.decode("{"))
    }
}
