package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class NotificationGroupTest {
    private fun user(id: String) = User(id, "u$id", "", "https://e", "", "", emptyList())
    private fun group(key: String, count: Int, accounts: List<String>, pageMinId: String) = NotificationGroup(
        key = key, type = NotificationType.Favourite, count = count,
        accounts = accounts.map(::user), post = null, latestAt = Instant.EPOCH, pageMinId = pageMinId,
    )

    @Test
    fun mergeKeepsTotalCountAndUnionsAccounts() {
        val newer = group("g", count = 5, accounts = listOf("1", "2"), pageMinId = "100")
        val older = group("g", count = 5, accounts = listOf("2", "3"), pageMinId = "80")
        val merged = newer.mergeOlder(older)
        // notifications_count はグループ全体の総数なので足さない
        assertEquals(5, merged.count)
        assertEquals(listOf("1", "2", "3"), merged.accounts.map { it.inServerId })
        // 次ページは古い側の page_min_id から
        assertEquals("80", merged.pageMinId)
    }

    @Test
    fun v1NotificationBecomesSingleGroup() {
        val g = Notification("42", NotificationType.Mention, Instant.EPOCH, user("1"), null).asGroup()
        assertEquals("ungrouped-42", g.key)
        assertEquals(1, g.count)
        assertEquals("42", g.pageMinId)
    }
}
