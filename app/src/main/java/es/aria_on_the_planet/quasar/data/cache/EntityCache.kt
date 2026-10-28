package es.aria_on_the_planet.quasar.data.cache

import android.util.Log
import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.ContextDto
import es.aria_on_the_planet.quasar.data.remote.dto.PollDto
import es.aria_on_the_planet.quasar.data.remote.dto.RelationshipDto
import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** キャッシュから読んだ値と、それを取った時刻。 */
data class Cached<T>(val value: T, val fetchedAt: Long) {
    fun isFresh(ttlMillis: Long, now: Long = System.currentTimeMillis()): Boolean = isFresh(fetchedAt, ttlMillis, now)
}

/** スレッドの並び (中身の Status は別に持つ)。 */
@Serializable
data class ContextIds(val ancestors: List<String>, val descendants: List<String>)

/** 一覧の 1 件。ブーストは外側の ID とブーストした人を持つ (中身は [CacheKind.Status]、ブーストした人は [CacheKind.Account])。 */
@Serializable
data class PostListEntry(val id: String, val statusId: String, val boostedBy: String? = null)

private val PostListSerializer = ListSerializer(PostListEntry.serializer())

/** 期限と、残す件数。 */
object CachePolicy {
    /** ポスト (件数・自分の操作の状態)。 */
    const val StatusTtlMillis = 60_000L

    /** アカウント (プロフィール)。フォローした・された等で数が変わったときは無効化する。 */
    const val AccountTtlMillis = 5 * 60_000L

    /** 相手との関係 (Follows you・フォローリクエスト等は相手の操作でも変わる)。 */
    const val RelationshipTtlMillis = 2 * 60_000L

    internal val MaxEntries = mapOf(
        CacheKind.Status to 3000,
        CacheKind.Account to 2000,
        CacheKind.Relationship to 500,
        CacheKind.Context to 300,
        CacheKind.AccountPosts to 200,
        CacheKind.PinnedPosts to 200,
    )
}

/**
 * API の結果 (DTO) のキャッシュ。Repository が取得・操作の結果を書き、ポスト画面・プロフィールが先に読む。
 *
 * * 書き込みは版 ([issue] = リクエストを出した時刻) が保存済みより新しいときだけ (CAS)。先に出した取得の結果が
 *   後から届いても、その後に行った操作の結果を巻き戻さない。
 * * 操作 (お気に入り等) は成功した応答だけを書く (見込みの状態は書かない)。
 * * キャッシュの失敗 (DB・JSON) で API の結果を使えなくしないよう、ここで握りつぶしてログに出す。
 *   読めない (形の変わった) 値は無かったことにする。
 */
class EntityCache(
    private val dao: CacheDao,
    private val json: Json,
    private val clock: VersionClock = VersionClock(),
) {
    /** これから出すリクエストの版。結果を書くときに渡す。 */
    fun issue(): Long = clock.next()

    /** [statuses] (ブーストの中身・引用先を含む) と、その投稿者・[accounts] を書く。 */
    suspend fun putStatuses(statuses: List<StatusDto>, version: Long, accounts: List<AccountDto> = emptyList()) = write {
        val collected = collectEntities(statuses, accounts)
        dao.putNewer(
                collected.statuses.map { entry(CacheKind.Status, it.id, StatusDto.serializer(), it, version) } +
                collected.accounts.map { accountEntry(it, version) },
        )
    }

    suspend fun putAccounts(accounts: List<AccountDto>, version: Long) = write {
        dao.putNewer(accounts.map { accountEntry(it, version) })
    }

    suspend fun putRelationships(relationships: List<RelationshipDto>, version: Long) = write {
        dao.putNewer(relationships.map { entry(CacheKind.Relationship, it.id, RelationshipDto.serializer(), it, version) })
    }

    /** [statusId] のスレッド ([context]) を書く。中身の Status も書く。 */
    suspend fun putContext(statusId: String, context: ContextDto, version: Long) {
        putStatuses(context.ancestors + context.descendants, version)
        val ids = ContextIds(context.ancestors.map { it.id }, context.descendants.map { it.id })
        write { dao.putNewer(listOf(entry(CacheKind.Context, statusId, ContextIds.serializer(), ids, version))) }
    }

    /** [ownerId] の一覧 ([kind]、[CacheKind.AccountPosts] 等) を [statuses] (新しい順) の並びで書く。中身の Status も書く。 */
    suspend fun putPostList(kind: CacheKind, ownerId: String, statuses: List<StatusDto>, version: Long) {
        putStatuses(statuses, version)
        val entries = statuses.map { PostListEntry(it.id, it.reblog?.id ?: it.id, it.account.id.takeIf { _ -> it.reblog != null }) }
        write { dao.putNewer(listOf(entry(kind, ownerId, PostListSerializer, entries, version))) }
    }

    /**
     * [putPostList] で書いた一覧を Status に戻す (ブーストは外側を組み立て直す)。キャッシュから消えたポスト・ブーストした人は飛ばす。
     * 取得時刻は並びを書いた時刻。
     */
    suspend fun postList(kind: CacheKind, ownerId: String): Cached<List<StatusDto>>? {
        val list = read(kind, ownerId, PostListSerializer) ?: return null
        val bodies = statuses(list.value.map { it.statusId })
        val boosters = readAll(CacheKind.Account, list.value.mapNotNull { it.boostedBy }, AccountDto.serializer())
        val restored = list.value.mapNotNull { entry ->
            val body = bodies[entry.statusId]?.value ?: return@mapNotNull null
            val boostedBy = entry.boostedBy ?: return@mapNotNull body
            boosters[boostedBy]?.value?.let { StatusDto(id = entry.id, createdAt = body.createdAt, account = it, reblog = body) }
        }
        return Cached(restored, list.fetchedAt)
    }

    /** 保存済みの [statusId] の投票を [poll] に差し替える (投票した応答)。 */
    suspend fun updatePoll(statusId: String, poll: PollDto, version: Long) = write {
        dao.update(CacheKind.Status.name, statusId, version) { stored ->
            val status = json.decodeFromString(StatusDto.serializer(), stored)
            if (status.poll?.id != poll.id) null else json.encodeToString(StatusDto.serializer(), status.copy(poll = poll))
        }
    }

    suspend fun status(id: String): Cached<StatusDto>? = read(CacheKind.Status, id, StatusDto.serializer())

    /** [ids] のうち保存されているもの (ID → 値)。 */
    suspend fun statuses(ids: List<String>): Map<String, Cached<StatusDto>> = readAll(CacheKind.Status, ids, StatusDto.serializer())

    suspend fun account(id: String): Cached<AccountDto>? = read(CacheKind.Account, id, AccountDto.serializer())

    suspend fun relationship(accountId: String): Cached<RelationshipDto>? =
        read(CacheKind.Relationship, accountId, RelationshipDto.serializer())

    suspend fun context(statusId: String): Cached<ContextIds>? = read(CacheKind.Context, statusId, ContextIds.serializer())

    /** 次に読んだとき期限切れとして扱う (フォローで数が変わった等)。 */
    suspend fun invalidateAccount(id: String) = write { dao.invalidate(CacheKind.Account.name, id) }

    suspend fun deleteStatus(id: String) = write {
        dao.delete(CacheKind.Status.name, id)
        dao.delete(CacheKind.Context.name, id)
    }

    /** 種類ごとに上限 ([CachePolicy.MaxEntries]) を超えた古い分を消す。 */
    suspend fun trim() = write { CachePolicy.MaxEntries.forEach { (kind, keep) -> dao.trim(kind.name, keep) } }

    /** 全部消す (ログアウト・別のアカウントでのログイン)。 */
    suspend fun clear() = write { dao.clear() }

    private fun <T> entry(kind: CacheKind, id: String, serializer: KSerializer<T>, value: T, version: Long) =
        CacheEntry(kind.name, id, json.encodeToString(serializer, value), version, fetchedAt = version)

    // verify_credentials の source (投稿の既定値等) は持たない
    private fun accountEntry(account: AccountDto, version: Long) =
        entry(CacheKind.Account, account.id, AccountDto.serializer(), account.copy(source = null), version)

    private suspend fun <T> read(kind: CacheKind, id: String, serializer: KSerializer<T>): Cached<T>? =
        guard { dao.get(kind.name, id) }?.let { withContext(Dispatchers.Default) { decode(it, serializer) } }

    private suspend fun <T> readAll(kind: CacheKind, ids: List<String>, serializer: KSerializer<T>): Map<String, Cached<T>> {
        val entries = guard { ids.distinct().chunked(CacheDao.MaxIdsPerQuery).flatMap { dao.getAll(kind.name, it) } }.orEmpty()
        return withContext(Dispatchers.Default) {
            entries.mapNotNull { entry -> decode(entry, serializer)?.let { entry.id to it } }.toMap()
        }
    }

    private fun <T> decode(entry: CacheEntry, serializer: KSerializer<T>): Cached<T>? = try {
        Cached(json.decodeFromString(serializer, entry.json), entry.fetchedAt)
    } catch (e: Exception) {
        Log.w(TAG, "Unreadable ${entry.kind} in cache", e)
        null
    }

    /** JSON の変換 (1 ページ分) を呼び出し元 (UI のスレッド) で行わないよう、[Dispatchers.Default] で書く。 */
    private suspend fun write(block: suspend () -> Unit) {
        withContext(Dispatchers.Default) { guard(block) }
    }

    private suspend fun <T> guard(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Cache operation failed", e)
        null
    }

    private companion object {
        const val TAG = "EntityCache"
    }
}
