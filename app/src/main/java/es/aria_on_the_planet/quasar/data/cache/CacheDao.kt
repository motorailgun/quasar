package es.aria_on_the_planet.quasar.data.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class CacheDao {
    @Query("SELECT * FROM cache_entries WHERE kind = :kind AND id = :id")
    abstract suspend fun get(kind: String, id: String): CacheEntry?

    /** [ids] は SQLite の変数の上限 (999) を超えないよう呼び出し側で分ける。 */
    @Query("SELECT * FROM cache_entries WHERE kind = :kind AND id IN (:ids)")
    abstract suspend fun getAll(kind: String, ids: List<String>): List<CacheEntry>

    @Query("SELECT id, version FROM cache_entries WHERE kind = :kind AND id IN (:ids)")
    protected abstract suspend fun versions(kind: String, ids: List<String>): List<EntryVersion>

    @Upsert
    protected abstract suspend fun upsert(entries: List<CacheEntry>)

    /** 保存済みより版が新しいものだけを書く (CAS)。 */
    @Transaction
    open suspend fun putNewer(entries: List<CacheEntry>) {
        newestPerKey(entries).groupBy { it.kind }.forEach { (kind, sameKind) ->
            sameKind.chunked(MaxIdsPerQuery).forEach { chunk ->
                val existing = versions(kind, chunk.map { it.id }).associate { it.id to it.version }
                val newer = selectNewer(chunk, existing)
                if (newer.isNotEmpty()) upsert(newer)
            }
        }
    }

    /**
     * 保存済みの 1 件の中身を [transform] で書き換える (版が新しいときだけ)。期限の判定に使う時刻は変えない
     * (投票結果だけを差し替える等、全体を取り直したわけではないので)。保存されていなければ何もしない。
     */
    @Transaction
    open suspend fun update(kind: String, id: String, version: Long, transform: (String) -> String?) {
        val current = get(kind, id) ?: return
        if (version <= current.version) return
        val json = transform(current.json) ?: return
        upsert(listOf(current.copy(json = json, version = version)))
    }

    /** 次に読んだときに期限切れとして扱わせる (中身はすぐ出すために残す)。 */
    @Query("UPDATE cache_entries SET fetchedAt = 0 WHERE kind = :kind AND id = :id")
    abstract suspend fun invalidate(kind: String, id: String)

    @Query("DELETE FROM cache_entries WHERE kind = :kind AND id = :id")
    abstract suspend fun delete(kind: String, id: String)

    /** [kind] のうち、新しく取った順で [keep] 件を残して消す。 */
    @Query(
        "DELETE FROM cache_entries WHERE kind = :kind AND id NOT IN " +
            "(SELECT id FROM cache_entries WHERE kind = :kind ORDER BY fetchedAt DESC LIMIT :keep)",
    )
    abstract suspend fun trim(kind: String, keep: Int)

    @Query("DELETE FROM cache_entries")
    abstract suspend fun clear()

    companion object {
        const val MaxIdsPerQuery = 500
    }
}
