package es.aria_on_the_planet.quasar.debug

import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** ギャラリーの固定データ (`scripts/gallery/generate.py` の出力) が読めて、中身が揃っているか。 */
class GalleryFixturesTest {
    private val dir = File("src/debug/assets/gallery")

    private fun read(name: String) = File(dir, name).readText()

    @Test
    fun everyScenarioHasItsFixture() {
        GalleryScenario.entries.forEach { assertTrue(it.fixture, File(dir, it.fixture).isFile) }
    }

    @Test
    fun postFixturesParseAndUseKnownEmojis() {
        val lists = GalleryScenario.entries
            .filter { it != GalleryScenario.EmojiPicker && it != GalleryScenario.Profile }
            .associate { it.fixture to GalleryFixtures.posts(read(it.fixture)) }
        lists.forEach { (name, posts) ->
            assertTrue(name, posts.isNotEmpty())
            assertEquals("$name: ID は一覧の中で一意 (LazyColumn のキー)", posts.size, posts.map { it.id }.toSet().size)
            posts.forEach { assertEmojisResolved(name, it) }
        }
    }

    @Test
    fun profileFixtureParses() {
        val fixture = GalleryFixtures.profile(read(GalleryScenario.Profile.fixture))
        val user = fixture.profile.user
        assertTrue(fixture.posts.isNotEmpty())
        assertTrue("ヘッダ画像がある", fixture.profile.headerUrl != null)
        assertTrue("アイコンはアニメ版と静止版が別", user.iconUrl != user.iconStaticUrl)
        val texts = listOf(user.displayName, user.note) + fixture.profile.fields.flatMap { listOf(it.name, it.value) }
        texts.forEach { assertResolved("profile", it, user.emojis) }
        fixture.posts.forEach { assertEmojisResolved("profile", it) }
    }

    @Test
    fun pickerFixtureParses() {
        val emojis = GalleryFixtures.emojis(read(GalleryScenario.EmojiPicker.fixture))
        assertTrue(emojis.size >= 50)
        assertEquals(emojis.size, emojis.map { it.shortcode }.toSet().size)
    }

    @Test
    fun everyAssetUrlExists() {
        val urls = dir.listFiles { f -> f.extension == "json" }!!.flatMap { AssetUrl.findAll(it.readText()).map { m -> m.groupValues[1] } }
        assertTrue(urls.isNotEmpty())
        urls.toSet().forEach { assertTrue("missing asset: $it", File(dir.parentFile, it).isFile) }
    }

    private fun assertEmojisResolved(name: String, post: Post) {
        listOfNotNull(post.text, post.contentWarning).forEach { assertResolved(name, it, post.emojis) }
        post.poll?.let { poll -> poll.options.forEach { assertResolved(name, it.title, poll.emojis) } }
        assertResolved(name, post.author.displayName, post.author.emojis)
        post.quote?.post?.let { assertEmojisResolved(name, it) }
    }

    private fun assertResolved(name: String, text: String, emojis: List<CustomEmoji>) {
        val known = emojis.map { it.shortcode }.toSet()
        Shortcode.findAll(text).map { it.groupValues[1] }.forEach {
            assertTrue("$name: :$it: has no emoji in \"$text\"", it in known)
        }
    }

    private companion object {
        val Shortcode = Regex(":([A-Za-z0-9_]+):")
        val AssetUrl = Regex("file:///android_asset/([^\"]+)")
    }
}
