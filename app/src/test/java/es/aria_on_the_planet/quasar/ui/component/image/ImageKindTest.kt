package es.aria_on_the_planet.quasar.ui.component.image

import es.aria_on_the_planet.quasar.ui.settings.DisplaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageKindTest {
    @Test
    fun allAnimateByDefault() {
        val settings = DisplaySettings()
        ImageKind.entries.forEach { assertTrue(it.name, settings.animates(it)) }
    }

    @Test
    fun eachSettingStopsOnlyItsKind() {
        assertEquals(
            setOf(ImageKind.OtherEmoji, ImageKind.Avatar, ImageKind.Other),
            DisplaySettings(animatePostEmojis = false).animatingKinds(),
        )
        assertEquals(
            setOf(ImageKind.PostEmoji, ImageKind.Avatar, ImageKind.Other),
            DisplaySettings(animateOtherEmojis = false).animatingKinds(),
        )
        assertEquals(
            setOf(ImageKind.PostEmoji, ImageKind.OtherEmoji, ImageKind.Other),
            DisplaySettings(animateAvatars = false).animatingKinds(),
        )
    }

    @Test
    fun animateImagesOffStopsEverything() {
        val settings = DisplaySettings(animateImages = false)
        ImageKind.entries.forEach { assertFalse(it.name, settings.animates(it)) }
    }

    private fun DisplaySettings.animatingKinds() = ImageKind.entries.filter { animates(it) }.toSet()
}
