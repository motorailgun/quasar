package es.aria_on_the_planet.quasar.ui.timeline

import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.settings.ColumnLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class ColumnWindowTest {
    @Test
    fun autoUsesTwoColumnsFromExpandedWidth() {
        assertEquals(1, visibleColumnCount(ColumnLayout.Auto, 839.dp))
        assertEquals(2, visibleColumnCount(ColumnLayout.Auto, 840.dp))
        assertEquals(2, visibleColumnCount(ColumnLayout.Auto, 1280.dp))
    }

    @Test
    fun forcedLayoutIgnoresWidth() {
        assertEquals(1, visibleColumnCount(ColumnLayout.Single, 1280.dp))
        assertEquals(2, visibleColumnCount(ColumnLayout.Double, 400.dp))
    }

    @Test
    fun tappingVisibleColumnKeepsStart() {
        assertEquals(1, startColumnFor(tapped = 1, start = 1, visible = 2, count = 4))
        assertEquals(1, startColumnFor(tapped = 2, start = 1, visible = 2, count = 4))
    }

    @Test
    fun tappingHiddenColumnMovesTheLeastAmount() {
        // [Home | Notifications] で Global → [Local | Global]
        assertEquals(2, startColumnFor(tapped = 3, start = 0, visible = 2, count = 4))
        // [Notifications | Local] で Global → [Local | Global]
        assertEquals(2, startColumnFor(tapped = 3, start = 1, visible = 2, count = 4))
        // [Local | Global] で Home → [Home | Notifications]
        assertEquals(0, startColumnFor(tapped = 0, start = 2, visible = 2, count = 4))
    }

    @Test
    fun singleColumnStartIsTheTappedColumn() {
        for (tapped in 0..3) assertEquals(tapped, startColumnFor(tapped, start = 0, visible = 1, count = 4))
    }

    @Test
    fun startIsClampedToTheLastWindow() {
        assertEquals(2, startColumnFor(tapped = 3, start = 3, visible = 2, count = 4))
    }

    @Test
    fun closenessCoversTheVisibleWindow() {
        assertEquals(1f, columnCloseness(index = 0, position = 0f, visible = 2))
        assertEquals(1f, columnCloseness(index = 1, position = 0f, visible = 2))
        assertEquals(0f, columnCloseness(index = 2, position = 0f, visible = 2))
        // 半分スワイプした途中
        assertEquals(0.5f, columnCloseness(index = 0, position = 0.5f, visible = 2))
        assertEquals(1f, columnCloseness(index = 1, position = 0.5f, visible = 2))
        assertEquals(0.5f, columnCloseness(index = 2, position = 0.5f, visible = 2))
        // 1 カラムは今までどおり (位置との距離)
        assertEquals(1f, columnCloseness(index = 1, position = 1f, visible = 1))
        assertEquals(0.25f, columnCloseness(index = 2, position = 1.25f, visible = 1))
    }
}
