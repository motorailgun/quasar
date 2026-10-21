package es.aria_on_the_planet.quasar.data.cache

import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.QuoteDto
import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheMergeTest {
    private fun entry(id: String, version: Long, kind: CacheKind = CacheKind.Status) =
        CacheEntry(kind.name, id, json = "v$version", version = version, fetchedAt = version)

    @Test
    fun onlyNewerVersionsAreWritten() {
        val incoming = listOf(entry("a", 10), entry("b", 10), entry("c", 10), entry("d", 10))
        // a: 古い版が保存済み → 書く / b: 新しい版 (後に出したリクエストの結果) が保存済み → 捨てる
        // c: 同じ版 → 書かない / d: 保存されていない → 書く
        val existing = mapOf("a" to 5L, "b" to 20L, "c" to 10L)
        assertEquals(listOf("a", "d"), selectNewer(incoming, existing).map { it.id })
    }

    @Test
    fun duplicatesInOneBatchKeepTheNewestVersion() {
        val result = newestPerKey(
            listOf(entry("a", 1), entry("b", 1), entry("a", 3), entry("a", 2), entry("a", 1, CacheKind.Account)),
        )
        // 種類が違えば別物。順は最初に出た位置
        assertEquals(listOf("a" to 3L, "b" to 1L, "a" to 1L), result.map { it.id to it.version })
        assertEquals(listOf("Status", "Status", "Account"), result.map { it.kind })
    }

    private fun account(id: String) = AccountDto(id = id, username = "u$id", acct = "u$id", url = "https://example.invalid/@u$id", avatar = "")

    private fun status(id: String, author: String, reblog: StatusDto? = null, quoted: StatusDto? = null) = StatusDto(
        id = id,
        createdAt = "2026-01-01T00:00:00.000Z",
        account = account(author),
        reblog = reblog,
        quote = quoted?.let { QuoteDto(state = "accepted", quotedStatus = it) },
    )

    @Test
    fun boostsAreStoredAsTheirContentAndQuotesAreIncluded() {
        val quoted = status("q", author = "3")
        val inner = status("inner", author = "2", quoted = quoted)
        val boost = status("outer", author = "1", reblog = inner)
        val collected = collectEntities(listOf(boost, status("plain", author = "2")), accounts = listOf(account("9")))
        // ブーストの外側は持たない (ポスト画面は中身の ID で開く)
        assertEquals(listOf("inner", "q", "plain"), collected.statuses.map { it.id })
        // ブーストした人・投稿者・引用先の投稿者・別に渡したアカウント。同じ ID は 1 つ
        assertEquals(listOf("1", "2", "3", "9"), collected.accounts.map { it.id })
    }

    @Test
    fun shallowQuotesAreNotFollowed() {
        val shallow = status("s", author = "1").copy(quote = QuoteDto(state = "accepted", quotedStatusId = "elsewhere"))
        assertEquals(listOf("s"), collectEntities(listOf(shallow)).statuses.map { it.id })
    }

    @Test
    fun freshnessUsesFetchTimeAndTreatsInvalidatedAsStale() {
        assertTrue(isFresh(fetchedAt = 1_000, ttlMillis = 500, now = 1_499))
        assertFalse(isFresh(fetchedAt = 1_000, ttlMillis = 500, now = 1_500))
        // 無効化 (fetchedAt = 0) は常に期限切れ
        assertFalse(isFresh(fetchedAt = 0, ttlMillis = Long.MAX_VALUE, now = 1))
    }
}
