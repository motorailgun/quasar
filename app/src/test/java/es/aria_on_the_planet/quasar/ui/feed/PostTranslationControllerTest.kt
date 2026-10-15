package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.data.translation.ServerTranslationCapabilities
import es.aria_on_the_planet.quasar.data.translation.TranslationEnvironment
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.TranslatedContent
import es.aria_on_the_planet.quasar.domain.model.TranslationProviderType
import es.aria_on_the_planet.quasar.domain.model.TranslationSettings
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.translationSourceHash
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Unconfined なので、翻訳が完了済みの値を返す場合は toggle の中で反映まで進む。 */
class PostTranslationControllerTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val user = User("1", "alice", "", "https://e", "", "", emptyList())
    private fun post(id: String, text: String = "<p>안녕</p>", language: String? = "ko") =
        Post(id, user, text, Instant.EPOCH, 0, 0, 0, emptyList(), language = language)

    private val settings = MutableStateFlow(TranslationSettings(onDeviceEnabled = true))
    private val environment = MutableStateFlow(
        TranslationEnvironment(
            loggedIn = true,
            server = ServerTranslationCapabilities(enabled = true),
            onDeviceLanguages = setOf("ko", "en"),
        ),
    )
    private val errors = mutableListOf<String>()
    private val calls = mutableListOf<Pair<String, TranslationProviderType>>()

    private fun content(post: Post, provider: TranslationProviderType) = TranslatedContent(
        html = "<p>hello</p>", contentWarning = null, sourceLanguage = "ko", provider = provider,
        sourceHash = post.translationSourceHash(),
    )

    private fun controller(
        maxEntries: Int = 400,
        translate: suspend (Post, TranslationProviderType, () -> Unit) -> TranslatedContent =
            { post, provider, _ -> content(post, provider) },
    ) = PostTranslationController(
        scope = scope,
        settings = settings,
        environment = environment,
        translate = { request, provider, onDownloading ->
            calls += request.post.statusId to provider
            translate(request.post, provider, onDownloading)
        },
        onError = { message, _ -> errors += message },
        maxEntries = maxEntries,
    )

    private fun PostTranslationController.ui(post: Post) = translations.value.of(post)

    @Test
    fun toggleCycle() {
        val p = post("a")
        val c = controller()
        assertEquals(PostTranslationUi.Available, c.ui(p))
        c.toggle(p)
        val shown = c.ui(p) as PostTranslationUi.Shown
        assertEquals(TranslationProviderType.Server, shown.content.provider)
        c.toggle(p)
        assertEquals(PostTranslationUi.Original(shown.content), c.ui(p))
        // 原文 → 訳はキャッシュから (再翻訳しない)
        c.toggle(p)
        assertEquals(shown, c.ui(p))
        assertEquals(1, calls.size)
    }

    @Test
    fun untranslatablePostHasNoUi() {
        val c = controller()
        assertNull(c.ui(post("a", language = "en")))
        c.toggle(post("a", language = "en"))
        assertEquals(0, calls.size)
    }

    @Test
    fun fallsBackToOnDeviceWhenServerFails() {
        val p = post("a")
        val c = controller { post, provider, _ ->
            if (provider == TranslationProviderType.Server) throw IOException("503") else content(post, provider)
        }
        c.toggle(p)
        assertEquals(TranslationProviderType.OnDevice, (c.ui(p) as PostTranslationUi.Shown).content.provider)
        assertEquals(listOf("a" to TranslationProviderType.Server, "a" to TranslationProviderType.OnDevice), calls)
        assertEquals(0, errors.size)
    }

    @Test
    fun failsWhenAllProvidersFailAndRetries() {
        val p = post("a")
        var fail = true
        val c = controller { post, provider, _ -> if (fail) throw IOException("boom") else content(post, provider) }
        c.toggle(p)
        assertEquals(PostTranslationUi.Failed, c.ui(p))
        assertEquals(1, errors.size)
        fail = false
        c.toggle(p)
        assertEquals(PostTranslationUi.Shown(content(p, TranslationProviderType.Server)), c.ui(p))
    }

    @Test
    fun ignoresToggleWhileTranslatingAndShowsModelDownload() {
        val p = post("a")
        val result = CompletableDeferred<TranslatedContent>()
        environment.value = environment.value.copy(server = null)
        val c = controller { _, _, onDownloading -> onDownloading(); result.await() }
        c.toggle(p)
        assertEquals(PostTranslationUi.Loading("ko", downloadingModel = true), c.ui(p))
        c.toggle(p)
        assertEquals(1, calls.size)
        result.complete(content(p, TranslationProviderType.OnDevice))
        assertEquals(PostTranslationUi.Shown(content(p, TranslationProviderType.OnDevice)), c.ui(p))
    }

    @Test
    fun editedPostDropsOldTranslation() {
        val p = post("a")
        val c = controller()
        c.toggle(p)
        val edited = p.copy(text = "<p>수정</p>")
        assertEquals(PostTranslationUi.Available, c.ui(edited))
        c.toggle(edited)
        assertEquals(2, calls.size)
        assertEquals(edited.translationSourceHash(), (c.ui(edited) as PostTranslationUi.Shown).content.sourceHash)
    }

    @Test
    fun autoTranslateOnlyWhenEnabled() {
        val p = post("a")
        val c = controller()
        c.onShown(p)
        assertEquals(0, calls.size)
        settings.value = settings.value.copy(autoTranslate = true)
        c.onShown(p)
        c.onShown(p)
        assertEquals(1, calls.size)
        assertEquals(PostTranslationUi.Shown(content(p, TranslationProviderType.Server)), c.ui(p))
        // 原文に戻したものは表示し直しても訳さない
        c.toggle(p)
        c.onShown(p)
        assertEquals(1, calls.size)
    }

    @Test
    fun settingsChangeDropsStatesAndCancelsInFlight() {
        val pending = CompletableDeferred<TranslatedContent>()
        val c = controller { post, provider, _ -> if (post.statusId == "b") pending.await() else content(post, provider) }
        c.toggle(post("a"))
        c.toggle(post("b"))
        settings.value = settings.value.copy(targetLanguage = "ja", onDeviceEnabled = false)
        pending.complete(content(post("b"), TranslationProviderType.Server))
        assertEquals(PostTranslationUi.Available, c.ui(post("a")))
        assertEquals(PostTranslationUi.Available, c.ui(post("b")))
    }

    @Test
    fun dropsOldestEntriesBeyondLimit() {
        val c = controller(maxEntries = 2)
        val posts = listOf(post("a"), post("b"), post("c"))
        posts.forEach(c::toggle)
        assertEquals(PostTranslationUi.Available, c.ui(posts[0]))
        assertEquals(PostTranslationUi.Shown(content(posts[2], TranslationProviderType.Server)), c.ui(posts[2]))
    }
}
