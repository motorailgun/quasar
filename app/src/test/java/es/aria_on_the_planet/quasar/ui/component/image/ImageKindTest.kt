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

    @Test
    fun frameRateLimitAppliesToEmojisAndAvatarsOnly() {
        val settings = DisplaySettings()
        assertEquals(DisplaySettings.DefaultEmojiAvatarAnimationMaxFps, settings.animationMaxFps(ImageKind.PostEmoji))
        assertEquals(30, settings.animationMaxFps(ImageKind.OtherEmoji))
        assertEquals(30, settings.animationMaxFps(ImageKind.Avatar))
        assertEquals(null, settings.animationMaxFps(ImageKind.Other))
        assertEquals(12, DisplaySettings(emojiAvatarAnimationMaxFps = 12).animationMaxFps(ImageKind.Avatar))
        // null・0 以下は上限なし
        assertEquals(null, DisplaySettings(emojiAvatarAnimationMaxFps = null).animationMaxFps(ImageKind.Avatar))
        assertEquals(null, DisplaySettings(emojiAvatarAnimationMaxFps = 0).animationMaxFps(ImageKind.Avatar))
    }

    private fun DisplaySettings.animatingKinds() = ImageKind.entries.filter { animates(it) }.toSet()
}
