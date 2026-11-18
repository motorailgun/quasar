package es.aria_on_the_planet.quasar.domain.model.settings

import es.aria_on_the_planet.quasar.domain.model.settings.PostMenuItem.Block
import es.aria_on_the_planet.quasar.domain.model.settings.PostMenuItem.CopyText
import es.aria_on_the_planet.quasar.domain.model.settings.PostMenuItem.Delete
import es.aria_on_the_planet.quasar.domain.model.settings.PostMenuItem.Edit
import es.aria_on_the_planet.quasar.domain.model.settings.PostMenuItem.HideBoosts
import es.aria_on_the_planet.quasar.domain.model.settings.PostMenuItem.Mute
import es.aria_on_the_planet.quasar.domain.model.settings.PostMenuItem.OpenInBrowser
import es.aria_on_the_planet.quasar.domain.model.settings.PostMenuItem.OpenOnServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostMenuItemsTest {
    private fun visible(
        hidden: Set<PostMenuItem> = emptySet(),
        mine: Boolean? = false,
        canEditOwn: Boolean = true,
        hasRemoteUrl: Boolean = true,
        hasText: Boolean = true,
    ) = visiblePostMenuItems(hidden, mine, canEditOwn, hasRemoteUrl, hasText)

    @Test
    fun ownAndOtherItemsDependOnAuthor() {
        assertEquals(setOf(OpenInBrowser, OpenOnServer, CopyText, Mute, Block, HideBoosts), visible(mine = false))
        assertEquals(setOf(Edit, Delete, OpenInBrowser, OpenOnServer, CopyText), visible(mine = true))
        // まだ分からなければどちらの項目も出うる
        assertEquals(PostMenuItem.entries.toSet(), visible(mine = null))
    }

    @Test
    fun hiddenItemsAreRemoved() {
        assertEquals(setOf(OpenOnServer, Mute, HideBoosts), visible(hidden = setOf(OpenInBrowser, CopyText, Block)))
    }

    @Test
    fun itemsWithoutTargetAreNotShown() {
        assertEquals(setOf(OpenInBrowser), visible(mine = true, canEditOwn = false, hasRemoteUrl = false, hasText = false))
    }

    @Test
    fun emptyWhenEverythingApplicableIsHidden() {
        // 自分のポストで共通・自分の項目を全部隠せば、他人の項目が残っていても何も出ない
        assertTrue(visible(hidden = setOf(Edit, Delete, OpenInBrowser, OpenOnServer, CopyText), mine = true).isEmpty())
        assertTrue(visible(hidden = PostMenuItem.entries.toSet(), mine = null).isEmpty())
    }

    @Test
    fun keepsMenuOrder() {
        assertEquals(PostMenuItem.entries, visible(mine = null).toList())
    }
}
