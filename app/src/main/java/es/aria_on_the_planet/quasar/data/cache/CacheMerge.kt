package es.aria_on_the_planet.quasar.data.cache

import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto

/** 同じ (種類, ID) が複数あれば版の最も新しいものだけを残す (1 ページに同じポストが 2 回出る等)。順は最初に出た位置。 */
internal fun newestPerKey(entries: List<CacheEntry>): List<CacheEntry> {
    val newest = LinkedHashMap<Pair<String, String>, CacheEntry>()
    for (entry in entries) {
        val key = entry.kind to entry.id
        val current = newest[key]
        if (current == null || entry.version > current.version) newest[key] = entry
    }
    return newest.values.toList()
}

/**
 * [entries] (同じ種類) のうち、保存済みの版 ([existing]: ID → 版) より新しいものだけを返す。保存されていないものは書く。
 * 同じ版は書かない (同じリクエストの結果を二度書いても変わらない)。
 */
internal fun selectNewer(entries: List<CacheEntry>, existing: Map<String, Long>): List<CacheEntry> =
    entries.filter { entry -> existing[entry.id]?.let { entry.version > it } ?: true }

/** API の結果に含まれる Status と Account。 */
internal data class CollectedEntities(val statuses: List<StatusDto>, val accounts: List<AccountDto>)

/**
 * [statuses] から、保存する Status と Account を取り出す。
 * * ブースト: 外側は持たず (ポスト画面は中身の ID で開く)、中身を持つ。ブーストした人のアカウントは持つ。
 * * 引用: 引用先の Status も持つ (`quoted_status` がある = 中身が返っているときだけ)。
 * * 各 Status の投稿者・[accounts] を Account として持つ。
 * 同じ ID は後に出た方で上書きする (同じ応答の中なので中身は同じ)。
 */
internal fun collectEntities(statuses: List<StatusDto>, accounts: List<AccountDto> = emptyList()): CollectedEntities {
    val collectedStatuses = LinkedHashMap<String, StatusDto>()
    val collectedAccounts = LinkedHashMap<String, AccountDto>()
    fun visit(status: StatusDto) {
        collectedAccounts[status.account.id] = status.account
        val inner = status.reblog
        if (inner != null) {
            visit(inner)
            return
        }
        collectedStatuses[status.id] = status
        status.quote?.quotedStatus?.let(::visit)
    }
    statuses.forEach(::visit)
    accounts.forEach { collectedAccounts[it.id] = it }
    return CollectedEntities(collectedStatuses.values.toList(), collectedAccounts.values.toList())
}

/** 期限 [ttlMillis] 以内に取ったものか。無効化されたもの ([CacheEntry.fetchedAt] = 0) は期限切れ。 */
internal fun isFresh(fetchedAt: Long, ttlMillis: Long, now: Long): Boolean =
    fetchedAt > 0 && now - fetchedAt < ttlMillis
