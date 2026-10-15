package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** 1 つのポストと、その前後のスレッド。 */
data class PostThread(
    /** 古い順。 */
    val ancestors: List<Post>,
    val post: Post,
    /** リプライ (孫以降も含む)。親子関係は [Post.inReplyToId] で辿る。 */
    val descendants: List<Post>,
)

class StatusRepository(
    private val api: MastodonApi,
) {
    suspend fun fetchThread(session: Session, statusId: String): PostThread = coroutineScope {
        val status = async { api.status(session.serverUrl, session.accessToken, statusId) }
        val context = async { api.statusContext(session.serverUrl, session.accessToken, statusId) }
        PostThread(
            ancestors = context.await().ancestors.map { it.toDomain() },
            post = status.await().toDomain(),
            descendants = context.await().descendants.map { it.toDomain() },
        )
    }
}
