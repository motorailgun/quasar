package es.aria_on_the_planet.quasar.data.cache

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** [CacheDao] の SQL (CAS・部分更新・無効化・上限)。端末上で動かす (`connectedDebugAndroidTest`。終わるとアプリが消える)。 */
@RunWith(AndroidJUnit4::class)
class CacheDaoTest {
    private lateinit var db: CacheDatabase
    private lateinit var dao: CacheDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CacheDatabase::class.java).build()
        dao = db.cacheDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entry(id: String, version: Long, json: String = "v$version", kind: CacheKind = CacheKind.Status) =
        CacheEntry(kind.name, id, json, version, fetchedAt = version)

    @Test
    fun olderWritesDoNotOverwriteNewerOnes() = runBlocking {
        dao.putNewer(listOf(entry("a", 10), entry("b", 10)))
        // a は後から届いた古いリクエストの結果、b は新しい結果
        dao.putNewer(listOf(entry("a", 5), entry("b", 20)))
        assertEquals("v10", dao.get("Status", "a")?.json)
        assertEquals("v20", dao.get("Status", "b")?.json)
    }

    @Test
    fun kindsAreSeparateNamespaces() = runBlocking {
        dao.putNewer(listOf(entry("1", 10, kind = CacheKind.Status), entry("1", 5, kind = CacheKind.Account)))
        assertEquals(10L, dao.get("Status", "1")?.version)
        assertEquals(5L, dao.get("Account", "1")?.version)
    }

    @Test
    fun updateRewritesOnlyWithNewerVersionAndKeepsFetchTime() = runBlocking {
        dao.putNewer(listOf(entry("a", 10)))
        dao.update("Status", "a", version = 5) { "stale" }
        assertEquals("v10", dao.get("Status", "a")?.json)
        dao.update("Status", "a", version = 20) { "$it+poll" }
        val updated = dao.get("Status", "a")!!
        assertEquals("v10+poll", updated.json)
        assertEquals(20L, updated.version)
        // 期限の判定は元の取得の時刻のまま
        assertEquals(10L, updated.fetchedAt)
        // 無いものは作らない
        dao.update("Status", "missing", version = 30) { "x" }
        assertNull(dao.get("Status", "missing"))
    }

    @Test
    fun invalidateKeepsContentButExpiresIt() = runBlocking {
        dao.putNewer(listOf(entry("a", 10, kind = CacheKind.Account)))
        dao.invalidate("Account", "a")
        val invalidated = dao.get("Account", "a")!!
        assertEquals("v10", invalidated.json)
        assertEquals(0L, invalidated.fetchedAt)
        // 無効化しても版は残るので、古い結果では上書きされない
        dao.putNewer(listOf(entry("a", 5, kind = CacheKind.Account)))
        assertEquals("v10", dao.get("Account", "a")?.json)
    }

    @Test
    fun trimKeepsTheMostRecentlyFetchedPerKind() = runBlocking {
        dao.putNewer((1L..5L).map { entry("s$it", it) } + entry("acc", 1, kind = CacheKind.Account))
        dao.trim("Status", keep = 2)
        assertEquals(listOf("s4", "s5"), dao.getAll("Status", (1..5).map { "s$it" }).map { it.id }.sorted())
        // 他の種類は消さない
        assertEquals("v1", dao.get("Account", "acc")?.json)
    }

    @Test
    fun largeBatchesAreSplitBelowTheVariableLimit() = runBlocking {
        dao.putNewer((1L..1200L).map { entry("s$it", it) })
        assertEquals(900, dao.getAll("Status", (1..900).map { "s$it" }).size)
        dao.putNewer((1L..1200L).map { entry("s$it", 0) })
        assertEquals("v1200", dao.get("Status", "s1200")?.json)
    }
}
