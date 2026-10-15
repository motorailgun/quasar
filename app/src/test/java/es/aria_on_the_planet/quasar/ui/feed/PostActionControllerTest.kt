package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.domain.model.PostViewerState
import es.aria_on_the_planet.quasar.domain.model.User
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.Instant

/** Unconfined なので、送信が完了済みの値を返す場合は perform の中で反映まで進む。 */
class PostActionControllerTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val user = User("1", "alice", "", "https://e", "", "", emptyList())
    private fun post(id: String, favourites: Int = 0, viewer: PostViewerState = PostViewerState()) =
        Post(id, user, "", Instant.EPOCH, 0, 0, favourites, emptyList(), viewer = viewer)

    private var posts = listOf(post("a", favourites = 1), post("b"))
    private val errors = mutableListOf<String>()
    private val favourite = PostAction(PostActionType.Favourite, undo = false)

    private fun controller(send: suspend (String, PostAction) -> Post) = PostActionController(
        scope = scope,
        send = send,
        update = { transform -> posts = posts.map(transform) },
        onError = { message, _ -> errors += message },
    )

    @Test
    fun appliesOptimisticallyThenServerState() {
        val response = CompletableDeferred<Post>()
        val c = controller { _, _ -> response.await() }
        c.perform(posts[0], favourite)
        // 送信中は見込みの状態
        assertEquals(true, posts[0].viewer.favourited)
        assertEquals(2, posts[0].reactionsCount)
        // サーバの件数で上書き
        response.complete(post("a", favourites = 7, viewer = PostViewerState(favourited = true)))
        assertEquals(7, posts[0].reactionsCount)
        assertEquals(post("b"), posts[1])
    }

    @Test
    fun revertsOnFailure() {
        val before = posts
        val c = controller { _, _ -> throw IOException("boom") }
        c.perform(posts[0], favourite)
        assertEquals(before, posts)
        assertEquals(1, errors.size)
    }

    @Test
    fun ignoresSameActionWhileInFlight() {
        val response = CompletableDeferred<Post>()
        val sent = mutableListOf<PostAction>()
        val c = controller { _, action -> sent += action; response.await() }
        c.perform(posts[0], favourite)
        c.perform(posts[0], favourite.inverse())
        assertEquals(listOf(favourite), sent)
        // 別の種類は並行して送れる
        c.perform(posts[0], PostAction(PostActionType.Bookmark, undo = false))
        assertEquals(2, sent.size)
        // 完了後は再び受け付ける
        response.complete(post("a", favourites = 2, viewer = PostViewerState(favourited = true, bookmarked = true)))
        c.perform(posts[0], favourite.inverse())
        assertEquals(3, sent.size)
        assertEquals(favourite.inverse(), sent.last())
    }
}
