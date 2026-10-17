package es.aria_on_the_planet.quasar.domain.model.settings

import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test
    fun everyPostActionHasItsOwnConfirmTarget() {
        val targets = PostActionType.entries.flatMap { type ->
            listOf(PostAction(type, undo = false), PostAction(type, undo = true))
        }.map { it.confirmTarget }
        assertEquals(targets.size, targets.toSet().size)
        assertEquals(ConfirmTarget.entries.toSet() - ConfirmTarget.Post, targets.toSet())
        assertEquals(ConfirmTarget.Unboost, PostAction(PostActionType.Boost, undo = true).confirmTarget)
        assertEquals(ConfirmTarget.Favourite, PostAction(PostActionType.Favourite, undo = false).confirmTarget)
    }
}
