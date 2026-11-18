package es.aria_on_the_planet.quasar.data.account

import es.aria_on_the_planet.quasar.data.auth.OAuthClient
import es.aria_on_the_planet.quasar.data.session.KeyValueStore
import es.aria_on_the_planet.quasar.data.session.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountStoreTest {
    private class MemoryStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
        val values = initial.toMutableMap()

        override fun get(name: String): String? = values[name]

        override fun put(name: String, value: String) {
            values[name] = value
        }

        override fun remove(name: String) {
            values.remove(name)
        }
    }

    private class Plaintext(var session: LegacyPlaintextSession?) : LegacyPlaintextSessionSource {
        override fun read(): LegacyPlaintextSession? = session

        override fun delete() {
            session = null
        }
    }

    /** a, b, c... の順にキーを振る。 */
    private fun keys(): () -> AccountKey {
        var next = 'a'
        return { AccountKey((next++).toString()) }
    }

    private val client = OAuthClient("id", "secret")
    private val profile = StoredProfile("alice", "Alice", "https://a.example/avatar.png", "https://a.example/avatar_static.png")

    private fun store(storage: MemoryStore = MemoryStore(), plaintext: Plaintext? = null) =
        AccountStore(storage, plaintext, keys())

    @Test
    fun migratesTheLegacySession() {
        val storage = MemoryStore(
            mapOf("session" to """{"serverUrl":"https://a.example","accessToken":"token","client":{"clientId":"id","clientSecret":"secret"}}"""),
        )
        val store = store(storage)
        val active = store.active.value!!
        assertEquals(AccountKey("a"), active.key)
        assertEquals(Session("https://a.example", "token"), active.session)
        assertEquals(client, active.client)
        // ID は最初の fetchMe まで無い
        assertNull(active.accountId)
        // 新しい形で書いて、古いものは消す
        assertFalse("session" in storage.values)
        assertTrue("accounts" in storage.values)
        // 次の起動では移した結果を読む (キーは同じ)
        assertEquals(store.state.value, store(storage).state.value)
    }

    @Test
    fun migratesThePlaintextSessionWhenThereIsNoEncryptedOne() {
        val plaintext = Plaintext(LegacyPlaintextSession("https://a.example", "token"))
        val storage = MemoryStore()
        val active = store(storage, plaintext).active.value!!
        assertEquals(Session("https://a.example", "token"), active.session)
        assertNull(active.client)
        assertNull(plaintext.session)
    }

    @Test
    fun legacySessionWithoutTokenIsLoggedOut() {
        val storage = MemoryStore(mapOf("session" to """{"serverUrl":"https://a.example","accessToken":null}"""))
        val store = store(storage)
        assertNull(store.active.value)
        assertTrue(store.state.value.accounts.isEmpty())
        assertFalse("session" in storage.values)
    }

    @Test
    fun nothingToMigrateIsLoggedOut() {
        val storage = MemoryStore()
        assertNull(store(storage).active.value)
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun savedAccountsWinOverALeftoverLegacySession() {
        // 新しい形を書いた後、古いものを消す前に終わった
        val storage = MemoryStore()
        store(storage).addLogin("https://a.example", "new", client, "1", profile)
        storage.values["session"] = """{"serverUrl":"https://b.example","accessToken":"old"}"""
        val active = store(storage).active.value!!
        assertEquals("new", active.accessToken)
    }

    @Test
    fun loginAddsAndActivates() {
        val store = store()
        val first = store.addLogin("https://a.example", "t1", client, "1", profile)
        assertNull(first.replaced)
        assertEquals(first.account, store.active.value)
        val second = store.addLogin("https://b.example", "t2", client, "1", null)
        assertNull(second.replaced)
        assertEquals(listOf(AccountKey("a"), AccountKey("b")), store.state.value.accounts.map { it.key })
        assertEquals(AccountKey("b"), store.active.value?.key)
    }

    @Test
    fun loginToTheSameAccountReplacesTheToken() {
        val store = store()
        store.addLogin("https://a.example", "t1", client, "1", profile)
        store.addLogin("https://b.example", "t2", client, "1", null)
        val newClient = OAuthClient("id2", "secret2")
        val result = store.addLogin("https://a.example/", "t3", newClient, "1", null)
        // 足さずに差し替え、キー・表示用の情報は残す
        assertEquals(2, store.state.value.accounts.size)
        assertEquals("t1", result.replaced?.accessToken)
        val active = store.active.value!!
        assertEquals(AccountKey("a"), active.key)
        assertEquals("t3", active.accessToken)
        assertEquals(newClient, active.client)
        assertEquals(profile, active.profile)
    }

    @Test
    fun accountsWithoutIdAreNotMerged() {
        // 移したアカウントは fetchMe まで ID が無いので、同じアカウントでも別に足される
        val storage = MemoryStore(mapOf("session" to """{"serverUrl":"https://a.example","accessToken":"t1"}"""))
        val store = store(storage)
        store.addLogin("https://a.example", "t2", client, "1", null)
        assertEquals(2, store.state.value.accounts.size)
        // ID が埋まれば次のログインからは同じアカウントとして扱う
        store.updateProfile(AccountKey("a"), "1", profile)
        val again = store.addLogin("https://a.example", "t3", client, "1", null)
        assertEquals(AccountKey("a"), again.account.key)
    }

    @Test
    fun removingTheActiveAccountFallsBackToTheFirstRemaining() {
        val store = store()
        store.addLogin("https://a.example", "t1", client, "1", null)
        store.addLogin("https://b.example", "t2", client, "2", null)
        store.addLogin("https://c.example", "t3", client, "3", null)
        assertEquals("t3", store.remove(AccountKey("c"))?.accessToken)
        assertEquals(AccountKey("a"), store.active.value?.key)
        // 使っていないものを除いても使っているものは変わらない
        store.remove(AccountKey("b"))
        assertEquals(AccountKey("a"), store.active.value?.key)
        store.remove(AccountKey("a"))
        assertNull(store.active.value)
        assertTrue(store.state.value.accounts.isEmpty())
        assertNull(store.remove(AccountKey("a")))
    }

    @Test
    fun updateProfileIsSavedAndEmitted() {
        val storage = MemoryStore()
        val store = store(storage)
        store.addLogin("https://a.example", "t1", client, null, null)
        store.updateProfile(AccountKey("a"), "1", profile)
        assertEquals("1", store.active.value?.accountId)
        assertEquals(profile, store.active.value?.profile)
        assertEquals("alice@a.example", store.active.value?.fullAcct)
        assertEquals(store.state.value, store(storage).state.value)
    }

    @Test
    fun unreadableOrInconsistentStorageIsLoggedOut() {
        assertNull(store(MemoryStore(mapOf("accounts" to "{broken"))).active.value)
        val dangling = """{"accounts":[],"activeKey":"x"}"""
        assertNull(store(MemoryStore(mapOf("accounts" to dangling))).active.value)
    }

    @Test
    fun tokensAreNotInToString() {
        val account = StoredAccount(AccountKey("a"), "https://a.example", "secret-token", client)
        assertFalse("secret-token" in account.toString())
        assertFalse("secret" in account.toString())
        assertFalse("secret-token" in Session("https://a.example", "secret-token").toString())
    }
}
