package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.mapper.toDomain
import es.aria_on_the_planet.quasar.data.mapper.toEditFormParams
import es.aria_on_the_planet.quasar.data.mapper.toFormParams
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.remote.MastodonApiException
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Poll
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostDraft
import es.aria_on_the_planet.quasar.domain.model.StatusSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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

    /**
     * [statusIds] のポストを 1 件ずつ取得し、同じ順で返す (同時に [MaxParallelFetches] 件まで)。
     * 消された・見られなくなったポスト (404 等、429 以外の 4xx) は飛ばす。通信エラー・5xx は投げる。
     */
    suspend fun fetchPosts(session: Session, statusIds: List<String>): List<Post> = coroutineScope {
        val permits = Semaphore(MaxParallelFetches)
        statusIds.map { id ->
            async {
                permits.withPermit {
                    try {
                        api.status(session.serverUrl, session.accessToken, id).toDomain()
                    } catch (e: MastodonApiException) {
                        if (e.code in 400..499 && e.code != 429) null else throw e
                    }
                }
            }
        }.awaitAll().filterNotNull()
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

    /**
     * [draft] を投稿し、作成されたポストを返す。未認証では投稿できない。
     * 同じ [idempotencyKey] での再送は二重に投稿されない (通信エラー後の再試行用)。
     */
    suspend fun publish(session: Session, draft: PostDraft, idempotencyKey: String): Post {
        val token = requireNotNull(session.accessToken) { "Posting requires login" }
        return api.postStatus(session.serverUrl, token, draft.toFormParams(), idempotencyKey).toDomain()
    }

    /** 自分のポストの編集前の原文 (本文・CW)。 */
    suspend fun fetchSource(session: Session, statusId: String): StatusSource {
        val token = requireNotNull(session.accessToken) { "Editing requires login" }
        val dto = api.statusSource(session.serverUrl, token, statusId)
        return StatusSource(text = dto.text, contentWarning = dto.spoilerText)
    }

    /** 自分のポスト [statusId] を [draft] の内容に書き換え、更新後のポストを返す (公開範囲・返信先は変わらない)。 */
    suspend fun edit(session: Session, statusId: String, draft: PostDraft): Post {
        val token = requireNotNull(session.accessToken) { "Editing requires login" }
        return api.editStatus(session.serverUrl, token, statusId, draft.toEditFormParams()).toDomain()
    }

    /** 自分のポスト [statusId] を削除する。 */
    suspend fun delete(session: Session, statusId: String) {
        val token = requireNotNull(session.accessToken) { "Deleting requires login" }
        api.deleteStatus(session.serverUrl, token, statusId)
    }
}

private const val MaxParallelFetches = 4

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
