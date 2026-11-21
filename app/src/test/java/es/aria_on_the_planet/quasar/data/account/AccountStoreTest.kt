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
        // 残っていた古いものはこの起動で消す
        assertFalse("session" in storage.values)
    }

    @Test
    fun leftoverPlaintextSessionIsDeletedOnTheNextLaunch() {
        // 平文から移して新しい形を書いた後、平文を消す前に終わった
        val storage = MemoryStore()
        store(storage).addLogin("https://a.example", "new", client, "1", profile)
        val plaintext = Plaintext(LegacyPlaintextSession("https://a.example", "old"))
        assertEquals("new", store(storage, plaintext).active.value!!.accessToken)
        assertNull(plaintext.session)
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
    fun migratedAccountWithoutIdIsReusedOnceTheIdIsFilled() {
        // 移したアカウントは fetchMe まで ID が無い。ログインの前に ID を埋めれば (OAuthLogin)、同じアカウントは足さずに差し替える
        val storage = MemoryStore(mapOf("session" to """{"serverUrl":"https://a.example","accessToken":"t1"}"""))
        val store = store(storage)
        assertEquals(listOf(AccountKey("a")), store.state.value.withoutIdOn("https://a.example/").map { it.key })
        store.updateProfile(AccountKey("a"), "1", profile)
        assertTrue(store.state.value.withoutIdOn("https://a.example").isEmpty())
        val again = store.addLogin("https://a.example", "t2", client, "1", null)
        assertEquals(AccountKey("a"), again.account.key)
        assertEquals("t1", again.replaced?.accessToken)
        assertEquals(1, store.state.value.accounts.size)
    }

    @Test
    fun accountsToResolveAreOnlyThoseWithoutIdOnTheSameServer() {
        val accounts = StoredAccounts(
            listOf(
                StoredAccount(AccountKey("a"), "https://a.example", "t1"),
                StoredAccount(AccountKey("b"), "https://A.example/", "t2"),
                StoredAccount(AccountKey("c"), "https://a.example", "t3", accountId = "3"),
                StoredAccount(AccountKey("d"), "https://b.example", "t4"),
            ),
        )
        assertEquals(listOf(AccountKey("a"), AccountKey("b")), accounts.withoutIdOn("https://a.example").map { it.key })
        assertEquals(listOf(AccountKey("d")), accounts.withoutIdOn("https://b.example").map { it.key })
        assertTrue(accounts.withoutIdOn("https://c.example").isEmpty())
    }

    @Test
    fun reloginReplacesTheTokenOnTheSameKey() {
        val storage = MemoryStore()
        val store = store(storage)
        store.addLogin("https://a.example", "t1", client, "1", profile)
        store.addLogin("https://b.example", "t2", client, "2", null)
        val newClient = OAuthClient("id2", "secret2")
        val result = store.relogin(AccountKey("a"), "https://a.example/", "t3", newClient, "1", null)
        result as ReloginResult.Replaced
        assertEquals("t1", result.replaced.accessToken)
        // キーはそのまま (設定・キャッシュが残る)、使うアカウントになる。表示用の情報は残す
        assertEquals(listOf(AccountKey("a"), AccountKey("b")), store.state.value.accounts.map { it.key })
        val active = store.active.value!!
        assertEquals(AccountKey("a"), active.key)
        assertEquals("t3", active.accessToken)
        assertEquals(newClient, active.client)
        assertEquals(profile, active.profile)
        assertEquals(store.state.value, store(storage).state.value)
    }

    @Test
    fun reloginAsAnotherAccountChangesNothing() {
        val store = store()
        store.addLogin("https://a.example", "t1", client, "1", profile)
        val before = store.state.value
        assertEquals(ReloginResult.DifferentAccount, store.relogin(AccountKey("a"), "https://a.example", "t2", client, "9", null))
        assertEquals(ReloginResult.DifferentAccount, store.relogin(AccountKey("a"), "https://b.example", "t2", client, "1", null))
        assertEquals(ReloginResult.Missing, store.relogin(AccountKey("x"), "https://a.example", "t2", client, "1", null))
        assertEquals(before, store.state.value)
    }

    @Test
    fun reloginFillsTheIdOfAMigratedAccount() {
        val storage = MemoryStore(mapOf("session" to """{"serverUrl":"https://a.example","accessToken":"t1"}"""))
        val store = store(storage)
        val result = store.relogin(AccountKey("a"), "https://a.example", "t2", client, "1", profile)
        assertTrue(result is ReloginResult.Replaced)
        assertEquals("1", store.active.value?.accountId)
        assertEquals("t2", store.active.value?.accessToken)
    }

    @Test
    fun reloginOfAMigratedAccountIntoAnAlreadyAddedOneIsRejected() {
        // 移したアカウント (ID 無し) にログインし直したら、既に足してある別のキーと同じアカウントだった
        val storage = MemoryStore(mapOf("session" to """{"serverUrl":"https://a.example","accessToken":"t1"}"""))
        val store = store(storage)
        store.addLogin("https://a.example", "t2", client, "1", null)
        val before = store.state.value
        assertEquals(ReloginResult.AlreadyAdded, store.relogin(AccountKey("a"), "https://a.example", "t3", client, "1", null))
        assertEquals(before, store.state.value)
    }

    @Test
    fun switchingChangesOnlyTheActiveAccount() {
        val storage = MemoryStore()
        val store = store(storage)
        store.addLogin("https://a.example", "t1", client, "1", null)
        store.addLogin("https://b.example", "t2", client, "2", null)
        val accounts = store.state.value.accounts
        store.setActive(AccountKey("a"))
        assertEquals(AccountKey("a"), store.active.value?.key)
        assertEquals(accounts, store.state.value.accounts)
        // 保存される (次の起動はこのアカウントから)
        assertEquals(AccountKey("a"), store(storage).active.value?.key)
        // 一覧に無いキーには切り替えない
        store.setActive(AccountKey("x"))
        assertEquals(AccountKey("a"), store.active.value?.key)
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
        store.addLogin("https://a.example", "t1", client, "1", null)
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
    fun danglingActiveKeyFallsBackToTheFirstAccount() {
        val stored = """{"accounts":[""" +
            """{"key":"a","serverUrl":"https://a.example","accessToken":"t1"},""" +
            """{"key":"b","serverUrl":"https://b.example","accessToken":"t2"}""" +
            """],"activeKey":"x"}"""
        val store = store(MemoryStore(mapOf("accounts" to stored)))
        assertEquals(AccountKey("a"), store.active.value?.key)
        assertEquals(2, store.state.value.accounts.size)
    }

    @Test
    fun tokensAreNotInToString() {
        val account = StoredAccount(AccountKey("a"), "https://a.example", "secret-token", client)
        assertFalse("secret-token" in account.toString())
        assertFalse("secret" in account.toString())
        assertFalse("secret-token" in Session("https://a.example", "secret-token").toString())
    }
}
