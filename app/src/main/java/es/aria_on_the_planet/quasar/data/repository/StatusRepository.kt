package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Poll
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.domain.model.PostActionType
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

    /** 投票して更新後の [Poll] を返す。未認証では投票できない。 */
    suspend fun vote(session: Session, pollId: String, choices: List<Int>): Poll {
        val token = requireNotNull(session.accessToken) { "Voting requires login" }
        return api.votePoll(session.serverUrl, token, pollId, choices).toDomain()
    }

    /**
     * [statusId] のポストに [action] を行い、更新後のポストを返す。未認証では操作できない。
     * ブーストの応答はブースト自体の Status だが、[toDomain] で中身の件数・状態が取り出される。
     */
    suspend fun perform(session: Session, statusId: String, action: PostAction): Post {
        val token = requireNotNull(session.accessToken) { "Post actions require login" }
        return api.statusAction(session.serverUrl, token, statusId, action.endpoint).toDomain()
    }
}

private val PostAction.endpoint: String
    get() = when (type) {
        PostActionType.Boost -> if (undo) "unreblog" else "reblog"
        PostActionType.Favourite -> if (undo) "unfavourite" else "favourite"
        PostActionType.Bookmark -> if (undo) "unbookmark" else "bookmark"
    }

/** [poll] と同じ ID の投票を、引用先も含めて差し替える。 */
fun Post.withPoll(poll: Poll): Post {
    val quoted = quote?.post
    return copy(
        poll = if (this.poll?.id == poll.id) poll else this.poll,
        quote = if (quoted != null) quote.copy(post = quoted.withPoll(poll)) else quote,
    )
}
