package es.aria_on_the_planet.quasar.ui.mediaviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomTransformTest {
    private val container = Size(1000f, 2000f)

    /** 横長 2:1 の画像は 1000x500 で中央に収まっている。 */
    private val content = Size(1000f, 500f)

    @Test
    fun zoomKeepsPointUnderCentroid() {
        // 中心から右上 (100, -50) の点を 2 倍に
        val centroid = Offset(600f, 950f)
        val t = ZoomTransform.Identity.applyGesture(centroid, Offset.Zero, 2f, container, content)
        assertEquals(2f, t.scale, 0.001f)
        // 中身上の点 (100, -50) は 2 倍で (200, -100) に行くので、(-100, 50) 戻すと指の下に残る
        assertEquals(-100f, t.offset.x, 0.001f)
        // 縦は 500 * 2 = 1000 < 2000 なので動かせず中央のまま
        assertEquals(0f, t.offset.y, 0.001f)
    }

    @Test
    fun scaleIsClampedBetweenOneAndMax() {
        val center = Offset(500f, 1000f)
        val shrunk = ZoomTransform.Identity.applyGesture(center, Offset.Zero, 0.5f, container, content)
        assertEquals(1f, shrunk.scale, 0.001f)
        val huge = ZoomTransform.Identity.applyGesture(center, Offset.Zero, 100f, container, content)
        assertEquals(MaxZoomScale, huge.scale, 0.001f)
    }

    @Test
    fun zoomingOutToOneRecentres() {
        val zoomed = ZoomTransform(3f, Offset(-800f, 0f))
        val back = zoomed.applyGesture(Offset(500f, 1000f), Offset.Zero, 1f / 3f, container, content)
        assertEquals(1f, back.scale, 0.001f)
        assertOffset(Offset.Zero, back.offset)
    }

    @Test
    fun panIsLimitedToContentEdges() {
        // 2 倍: 横 2000 (はみ出し 500 ずつ)、縦 1000 (領域より小さいので動かない)
        assertOffset(Offset(500f, 0f), clampZoomOffset(Offset(900f, 300f), 2f, container, content))
        assertOffset(Offset(-500f, 0f), clampZoomOffset(Offset(-900f, -300f), 2f, container, content))
        // 4 倍: 縦 2000 = 領域と同じなのでまだ動かない、横は 1500 まで
        assertOffset(Offset(1500f, 0f), clampZoomOffset(Offset(9999f, 9999f), 4f, container, content))
    }

    /** Offset の等価比較は -0.0 と 0.0 を区別するので、成分ごとに比べる。 */
    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }
}
