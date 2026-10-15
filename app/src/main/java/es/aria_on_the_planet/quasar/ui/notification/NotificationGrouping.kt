package es.aria_on_the_planet.quasar.ui.notification

import es.aria_on_the_planet.quasar.domain.model.NotificationGroup
import es.aria_on_the_planet.quasar.domain.model.NotificationType
import es.aria_on_the_planet.quasar.ui.feed.MastodonIdComparator

/** 1 人のユーザーのリアクションをまとめる最小のポスト数。 */
const val AccountGroupingThreshold = 3

/** ユーザー単位でまとめる単位 (種類 + ユーザー)。favourite と reblog は別々に数える。 */
private data class AccountKey(val type: NotificationType, val accountId: String)

/**
 * ユーザー単位のグルーピング対象か。favourite / reblog で、サーバ側グループが 1 人・1 件・ポスト付きのもの
 * (= そのユーザーが 1 つのポストにしたリアクション)。複数人がまとまったサーバ側グループには手を付けない。
 */
private fun NotificationGroup.accountKey(): AccountKey? {
    if (type != NotificationType.Favourite && type != NotificationType.Reblog) return null
    if (count != 1 || accounts.size != 1 || post == null) return null
    return AccountKey(type, accounts.single().inServerId)
}

private fun NotificationEntry.AccountReactions.accountKey() = AccountKey(type, account.inServerId)

private fun accountReactions(members: List<NotificationGroup>): NotificationEntry.AccountReactions {
    val first = members.first()
    return NotificationEntry.AccountReactions(
        type = first.type,
        account = first.accounts.single(),
        // 新しい順 (日時が無いものは後ろ)。同時刻は元の順を保つ
        members = members.sortedWith(compareByDescending(nullsFirst()) { it.latestAt }),
    )
}

/**
 * 通知一覧 [current] に、追加で取得した (より古い) ページ [page] を足した一覧を返す純粋関数。
 * refresh では [current] を空にして呼ぶ。
 *
 * 1. ページ境界で分かれたサーバ側グループの続き (同じ `group_key`) は、既存の行の中で統合する。
 * 2. 既存の行のうち「下から 2 番目まで」を固定部分とし、最後の行と新しいページを再評価部分とする
 *    (最後の行は新しいページと続いている可能性が高いので組み直す)。
 * 3. 再評価部分の各リアクションについて、同じユーザー・種類が固定部分にいれば:
 *    - 既にユーザー単位の行があれば、そこへ吸収する (位置は変えない)。
 *    - 単体の行しか無く、固定部分の分と合わせて [threshold] 件以上になれば、そのユーザーの最も新しい行の位置に
 *      ユーザー単位の行を作り直し、他の単体の行は取り除く。
 * 4. 残りは再評価部分の中だけでグルーピングする ([threshold] 件以上のユーザーをまとめ、最も新しい位置に置く)。
 *    届かなければ単体のまま。
 */
fun appendNotificationPage(
    current: List<NotificationEntry>,
    page: List<NotificationGroup>,
    threshold: Int = AccountGroupingThreshold,
): List<NotificationEntry> {
    if (page.isEmpty()) return current

    // 1. ページ境界で分かれたサーバ側グループの続きを統合する
    val entries = current.toMutableList()
    val incoming = LinkedHashMap<String, NotificationGroup>()
    for (group in page) {
        val index = entries.indexOfFirst { entry -> entry.groups.any { it.key == group.key } }
        when {
            index >= 0 -> entries[index] = entries[index].mapGroups { if (it.key == group.key) it.mergeOlder(group) else it }
            // 同じページ内に同じキーが重複して来た場合も統合しておく
            else -> incoming[group.key] = incoming[group.key]?.mergeOlder(group) ?: group
        }
    }

    // 2. 固定部分と再評価部分に分ける。最後の行は分解して新しいページの先頭に戻す
    val fixed: MutableList<NotificationEntry?> = entries.dropLast(1).toMutableList()
    val region = entries.lastOrNull()?.groups.orEmpty() + incoming.values

    // 3. 固定部分にいるユーザーへの吸収・作り直し
    val regionByKey = region.filter { it.accountKey() != null }.groupBy { it.accountKey()!! }
    val consumed = HashSet<String>()
    for ((key, regionMembers) in regionByKey) {
        val reactionsIndex = fixed.indexOfFirst { it is NotificationEntry.AccountReactions && it.accountKey() == key }
        if (reactionsIndex >= 0) {
            val existing = fixed[reactionsIndex] as NotificationEntry.AccountReactions
            fixed[reactionsIndex] = accountReactions(existing.members + regionMembers)
            regionMembers.mapTo(consumed) { it.key }
            continue
        }
        val singleIndices = fixed.indices.filter { i ->
            (fixed[i] as? NotificationEntry.Single)?.group?.accountKey() == key
        }
        if (singleIndices.isNotEmpty() && singleIndices.size + regionMembers.size >= threshold) {
            val singles = singleIndices.map { (fixed[it] as NotificationEntry.Single).group }
            fixed[singleIndices.first()] = accountReactions(singles + regionMembers)
            singleIndices.drop(1).forEach { fixed[it] = null }
            regionMembers.mapTo(consumed) { it.key }
        }
    }

    // 4. 再評価部分の中だけでグルーピングする
    val rest = region.filter { it.key !in consumed }
    val restCounts = rest.mapNotNull { it.accountKey() }.groupingBy { it }.eachCount()
    val emitted = HashSet<AccountKey>()
    val regrouped = buildList {
        for (group in rest) {
            val key = group.accountKey()
            if (key != null && restCounts.getValue(key) >= threshold) {
                if (emitted.add(key)) add(accountReactions(rest.filter { it.accountKey() == key }))
            } else {
                add(NotificationEntry.Single(group))
            }
        }
    }

    return fixed.filterNotNull() + regrouped
}

/** 取得したページから次ページの `max_id` (ページ中で最も古い通知の ID) を求める。 */
fun nextNotificationMaxId(page: List<NotificationGroup>): String? =
    page.map { it.pageMinId }.minWithOrNull(MastodonIdComparator)
