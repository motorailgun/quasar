package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.data.cache.CachePolicy
import es.aria_on_the_planet.quasar.data.cache.EntityCache
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 1 つのポストと、その前後のスレッド。 */
data class PostThread(
    /** 古い順。 */
    val ancestors: List<Post>,
    val post: Post,
    /** リプライ (孫以降も含む)。親子関係は [Post.inReplyToId] で辿る。 */
    val descendants: List<Post>,
)

/** キャッシュから組み立てたスレッド。[isPostFresh] なら本体は取り直さなくてよい。 */
data class CachedThread(val thread: PostThread, val isPostFresh: Boolean)

/**
 * ポストの取得・操作。取得・操作の結果はキャッシュ ([cache]) にも書く。操作 (お気に入り等) は成功した応答だけを書く。
 */
class StatusRepository(
    private val api: MastodonApi,
    private val cache: EntityCache,
) {
    /**
     * [statusId] のスレッドを取得する。[post] (キャッシュの本体) を渡すと本体は取らず、スレッド (`/context`) だけを取る。
     * 消された・見られなくなったポスト (404 / 410) はキャッシュからも消して投げ直す。
     */
    suspend fun fetchThread(session: Session, statusId: String, post: Post? = null): PostThread = coroutineScope {
        val version = cache.issue()
        try {
            val status = if (post == null) async { api.status(session.serverUrl, session.accessToken, statusId) } else null
            val context = async { api.statusContext(session.serverUrl, session.accessToken, statusId) }.await()
            val fetched = status?.await()
            fetched?.let { cache.putStatuses(listOf(it), version) }
            cache.putContext(statusId, context, version)
            PostThread(
                ancestors = context.ancestors.map { it.toDomain() },
                post = fetched?.toDomain() ?: requireNotNull(post),
                descendants = context.descendants.map { it.toDomain() },
            )
        } catch (e: MastodonApiException) {
            if (e.code == 404 || e.code == 410) cache.deleteStatus(statusId)
            throw e
        }
    }

    /**
     * キャッシュにある [statusId] のスレッド。本体が無ければ `null`。スレッドの並びが無い (タイムラインで見ただけ) なら
     * 本体だけ、並びの中でキャッシュから消えたポストは飛ばす。
     */
    suspend fun cachedThread(statusId: String): CachedThread? = withContext(Dispatchers.Default) {
        val post = cache.status(statusId) ?: return@withContext null
        val ids = cache.context(statusId)?.value
        val others = ids?.let { cache.statuses(it.ancestors + it.descendants) }.orEmpty()
        fun posts(ids: List<String>?) = ids.orEmpty().mapNotNull { others[it]?.value?.toDomain() }
        CachedThread(
            thread = PostThread(posts(ids?.ancestors), post.value.toDomain(), posts(ids?.descendants)),
            isPostFresh = post.isFresh(CachePolicy.StatusTtlMillis),
        )
    }

    /**
     * [statusIds] のポストを 1 件ずつ取得し、同じ順で返す (同時に [MaxParallelFetches] 件まで)。
     * 消された・見られなくなったポスト (404 等、429 以外の 4xx) は飛ばす。通信エラー・5xx は投げる。
     */
    suspend fun fetchPosts(session: Session, statusIds: List<String>): List<Post> = coroutineScope {
        val permits = Semaphore(MaxParallelFetches)
        val version = cache.issue()
        val statuses = statusIds.map { id ->
            async {
                permits.withPermit {
                    try {
                        api.status(session.serverUrl, session.accessToken, id)
                    } catch (e: MastodonApiException) {
                        if (e.code in 400..499 && e.code != 429) null else throw e
                    }
                }
            }
        }.awaitAll().filterNotNull()
        cache.putStatuses(statuses, version)
        statuses.map { it.toDomain() }
    }

    /** キャッシュにある [statusIds] のポスト (ID → 値)。無いものは含まない。 */
    suspend fun cachedPosts(statusIds: List<String>): Map<String, CacheHit<Post>> = withContext(Dispatchers.Default) {
        cache.statuses(statusIds).mapValues { (_, cached) ->
            CacheHit(cached.value.toDomain(), cached.isFresh(CachePolicy.StatusTtlMillis))
        }
    }

    /** [statusId] の投票 [pollId] に投票して更新後の [Poll] を返す。未認証では投票できない。 */
    suspend fun vote(session: Session, statusId: String, pollId: String, choices: List<Int>): Poll {
        val token = requireNotNull(session.accessToken) { "Voting requires login" }
        val version = cache.issue()
        val poll = api.votePoll(session.serverUrl, token, pollId, choices)
        cache.updatePoll(statusId, poll, version)
        return poll.toDomain()
    }

    /**
     * [statusId] のポストに [action] を行い、更新後のポストを返す。未認証では操作できない。
     * ブーストの応答はブースト自体の Status だが、[toDomain] で中身の件数・状態が取り出される。
     */
    suspend fun perform(session: Session, statusId: String, action: PostAction): Post {
        val token = requireNotNull(session.accessToken) { "Post actions require login" }
        val version = cache.issue()
        return api.statusAction(session.serverUrl, token, statusId, action.endpoint)
            .also { cache.putStatuses(listOf(it), version) }
            .toDomain()
    }

    /**
     * [draft] を投稿し、作成されたポストを返す。未認証では投稿できない。
     * 同じ [idempotencyKey] での再送は二重に投稿されない (通信エラー後の再試行用)。
     */
    suspend fun publish(session: Session, draft: PostDraft, idempotencyKey: String): Post {
        val token = requireNotNull(session.accessToken) { "Posting requires login" }
        val version = cache.issue()
        return api.postStatus(session.serverUrl, token, draft.toFormParams(), idempotencyKey)
            .also { cache.putStatuses(listOf(it), version) }
            .toDomain()
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
        val version = cache.issue()
        return api.editStatus(session.serverUrl, token, statusId, draft.toEditFormParams())
            .also { cache.putStatuses(listOf(it), version) }
            .toDomain()
    }

    /** 自分のポスト [statusId] を削除する。 */
    suspend fun delete(session: Session, statusId: String) {
        val token = requireNotNull(session.accessToken) { "Deleting requires login" }
        api.deleteStatus(session.serverUrl, token, statusId)
        cache.deleteStatus(statusId)
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
