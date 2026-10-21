package es.aria_on_the_planet.quasar.data.cache

import androidx.room.Entity
import androidx.room.Index

/** キャッシュの種類 ([CacheEntry.kind])。ID は種類ごとの名前空間。 */
enum class CacheKind {
    /** Status (`StatusDto`)。ブーストは中身だけを、中身の ID で持つ。 */
    Status,

    /** Account (`AccountDto`)。 */
    Account,

    /** ログイン中ユーザーとの関係 (`RelationshipDto`)。ID は相手のアカウント ID。 */
    Relationship,

    /** スレッド (`ContextIds`)。ID は本体の Status ID。中身の Status は [Status] に入っている。 */
    Context,
}

/**
 * キャッシュの 1 件。[json] は API の JSON (DTO) そのまま。
 *
 * @param version 書き込みの版 = その値を取りに行ったリクエストを出した時刻 ([VersionClock])。
 *   後から届いた古いリクエストの結果で上書きしないよう、書き込みはこれが大きいときだけ行う ([selectNewer])。
 * @param fetchedAt 期限の判定に使う時刻。普段は [version] と同じで、無効化すると 0 になる。
 */
@Entity(
    tableName = "cache_entries",
    primaryKeys = ["kind", "id"],
    indices = [Index("kind", "fetchedAt")],
)
data class CacheEntry(
    val kind: String,
    val id: String,
    val json: String,
    val version: Long,
    val fetchedAt: Long,
)

/** [CacheDao.versions] の結果。 */
data class EntryVersion(val id: String, val version: Long)
