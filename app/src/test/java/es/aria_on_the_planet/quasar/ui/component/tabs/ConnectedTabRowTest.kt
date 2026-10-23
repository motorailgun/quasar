package es.aria_on_the_planet.quasar.ui.component.tabs

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectedTabRowTest {
    private val icons = listOf(40, 40, 40, 40)

    @Test
    fun `equal widths with labels when every label fits`() {
        val layout = layoutTabs(listOf(50, 60, 90, 100), icons, selected = 0, available = 400)
        assertEquals(listOf(100, 100, 100, 100), layout.widths)
        assertEquals(listOf(true, true, true, true), layout.showLabel)
    }

    @Test
    fun `remainder of the equal share goes to the last tab`() {
        assertEquals(listOf(33, 33, 34), layoutTabs(listOf(10, 10, 10), listOf(5, 5, 5), 1, 100).widths)
    }

    @Test
    fun `only the selected tab keeps its label when labels do not fit`() {
        // 等分 100 に 120 が収まらない → 選択中 (2) はラベル、他はアイコンで残りを等分 (割り切れない分は選択中へ)
        val layout = layoutTabs(listOf(50, 60, 120, 110), icons, selected = 2, available = 400)
        assertEquals(listOf(false, false, true, false), layout.showLabel)
        assertEquals(listOf(93, 93, 121, 93), layout.widths)
    }

    @Test
    fun `selected tab with a short label is not narrower than the equal share`() {
        val layout = layoutTabs(listOf(50, 60, 120, 110), icons, selected = 0, available = 400)
        assertEquals(listOf(true, false, false, false), layout.showLabel)
        assertEquals(listOf(100, 100, 100, 100), layout.widths)
    }

    @Test
    fun `overflows with natural widths when even the icons do not fit`() {
        val layout = layoutTabs(listOf(50, 60, 300, 110), icons, selected = 2, available = 380)
        assertEquals(listOf(false, false, true, false), layout.showLabel)
        // 他のアイコンは同じ幅に揃える
        assertEquals(listOf(40, 40, 300, 40), layoutTabs(listOf(50, 60, 300, 110), listOf(30, 40, 35, 20), 2, 380).widths)
        assertEquals(420, layout.widths.sum())
    }

    @Test
    fun `fits exactly the available width unless overflowing`() {
        val labels = listOf(70, 80, 95, 130)
        for (available in listOf(300, 333, 400, 520, 1000)) {
            for (selected in labels.indices) {
                val layout = layoutTabs(labels, icons, selected, available)
                assertEquals("$available / $selected", available, layout.widths.sum())
            }
        }
    }

    @Test
    fun `single tab`() {
        assertEquals(listOf(100), layoutTabs(listOf(50), listOf(20), 0, 100).widths)
        assertEquals(TabLayout(listOf(150), listOf(true)), layoutTabs(listOf(150), listOf(20), 0, 100))
    }

    @Test
    fun `no tabs`() {
        assertEquals(TabLayout(emptyList(), emptyList()), layoutTabs(emptyList(), emptyList(), 0, 100))
    }
}
