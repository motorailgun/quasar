package es.aria_on_the_planet.quasar.ui.notification

import es.aria_on_the_planet.quasar.data.streaming.StreamEvent
import es.aria_on_the_planet.quasar.domain.model.Notification
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

// --- 新しい側への追加 (ストリーミング・再接続時の補完) ------------------------------------------------

/** 行の中で最も新しい通知の ID (並び順と補完の `since_id` に使う)。 */
internal val NotificationEntry.latestNotificationId: String
    get() = groups.map { it.latestNotificationId }.maxWithOrNull(MastodonIdComparator) ?: key

/** 一覧の中で最も新しい通知の ID。 */
fun newestNotificationId(entries: List<NotificationEntry>): String? =
    entries.map { it.latestNotificationId }.maxWithOrNull(MastodonIdComparator)

/**
 * 新しい通知 (またはグループ) を一覧に加える純粋関数。
 *
 * @param incoming 加えるグループ。
 * @param isSingleNotification `true` ならストリームで届いた通知 1 件 (既存グループの件数を 1 増やす)。
 *   `false` なら API で取得したグループ (件数はグループ全体の総数なので、既存の値と置き換える)。
 *
 * 1. 同じキーのグループが既にあり、その最新通知 ID が [incoming] 以上なら取り込み済みとして何もしない。
 * 2. 同じキーのグループがあれば統合し、元の位置から外して新しい位置へ動かす。ユーザー単位の行のメンバーだった場合、
 *    統合後は複数人 (または複数件) のグループになりユーザー単位の対象外になるので外し、閾値を下回った行は単体に戻す。
 * 3. 無ければ、ユーザー単位の対象なら、同じユーザー・種類の行に吸収するか、単体の行と合わせて閾値以上なら作り直す。
 * 4. 位置は最新通知 ID の順 (新しい順) で決める。
 */
fun prependNotification(
    current: List<NotificationEntry>,
    incoming: NotificationGroup,
    isSingleNotification: Boolean,
    threshold: Int = AccountGroupingThreshold,
): List<NotificationEntry> {
    val existing = current.asSequence().flatMap { it.groups }.firstOrNull { it.key == incoming.key }
    if (existing != null && MastodonIdComparator.compare(existing.latestNotificationId, incoming.latestNotificationId) >= 0) {
        return current
    }

    val entries = current.toMutableList()
    val toInsert: NotificationEntry
    if (existing != null) {
        val merged = existing.copy(
            count = if (isSingleNotification) existing.count + 1 else maxOf(existing.count, incoming.count),
            accounts = (incoming.accounts + existing.accounts).distinctBy { it.inServerId },
            post = incoming.post ?: existing.post,
            latestAt = incoming.latestAt ?: existing.latestAt,
            latestNotificationId = incoming.latestNotificationId,
        )
        removeGroup(entries, existing.key, threshold)
        toInsert = NotificationEntry.Single(merged)
    } else {
        val key = incoming.accountKey()
        val reactionsIndex = if (key == null) -1 else entries.indexOfFirst {
            it is NotificationEntry.AccountReactions && it.accountKey() == key
        }
        val singleIndices = if (key == null) emptyList() else entries.indices.filter { i ->
            (entries[i] as? NotificationEntry.Single)?.group?.accountKey() == key
        }
        toInsert = when {
            reactionsIndex >= 0 -> {
                val reactions = entries.removeAt(reactionsIndex) as NotificationEntry.AccountReactions
                accountReactions(reactions.members + incoming)
            }
            singleIndices.isNotEmpty() && singleIndices.size + 1 >= threshold -> {
                val singles = singleIndices.map { (entries[it] as NotificationEntry.Single).group }
                singleIndices.asReversed().forEach { entries.removeAt(it) }
                accountReactions(singles + incoming)
            }
            else -> NotificationEntry.Single(incoming)
        }
    }

    val newId = toInsert.latestNotificationId
    val index = entries.indexOfFirst { MastodonIdComparator.compare(it.latestNotificationId, newId) < 0 }
    if (index == -1) entries += toInsert else entries.add(index, toInsert)
    return entries
}

/** 新しい側から取得した複数のグループ (新しい順) を、古い方から順に加える。 */
fun prependNotificationGroups(current: List<NotificationEntry>, newerFirst: List<NotificationGroup>): List<NotificationEntry> =
    newerFirst.asReversed().fold(current) { acc, group -> prependNotification(acc, group, isSingleNotification = false) }

/** [key] のグループを一覧から外す。ユーザー単位の行のメンバーなら行から外し、閾値を下回れば単体の行に戻す。 */
private fun removeGroup(entries: MutableList<NotificationEntry>, key: String, threshold: Int) {
    val index = entries.indexOfFirst { entry -> entry.groups.any { it.key == key } }
    if (index < 0) return
    when (val entry = entries[index]) {
        is NotificationEntry.Single -> entries.removeAt(index)
        is NotificationEntry.AccountReactions -> {
            val remaining = entry.members.filterNot { it.key == key }
            entries.removeAt(index)
            if (remaining.size >= threshold) {
                entries.add(index, accountReactions(remaining))
            } else {
                entries.addAll(index, remaining.map { NotificationEntry.Single(it) })
            }
        }
    }
}

// --- ストリーミング・補完の適用 ------------------------------------------------------------------

/**
 * ストリームのイベントを通知一覧に当てる。新しい通知は [toGroup] で一覧と同じキー体系のグループにして合流させ、
 * ポストの編集は通知中のポストにも反映する。
 */
fun applyStreamEvent(
    current: List<NotificationEntry>,
    event: StreamEvent,
    toGroup: (Notification) -> NotificationGroup,
): List<NotificationEntry> = when (event) {
    is StreamEvent.NotificationReceived ->
        prependNotification(current, toGroup(event.notification), isSingleNotification = true)
    is StreamEvent.StatusUpdate -> {
        val statusId = event.post.statusId
        if (current.none { entry -> entry.groups.any { it.post?.statusId == statusId } }) {
            current
        } else {
            current.map { entry -> entry.mapGroups { g -> if (g.post?.statusId == statusId) g.copy(post = event.post) else g } }
        }
    }
    StreamEvent.Connected, is StreamEvent.Update, is StreamEvent.Delete -> current
}

/**
 * 再接続時の補完で取得した [page] (新しい順) を通知一覧に当てる。
 * [pageLimit] 件埋まっていれば間に未取得の区間がありうるが、通知では gap を作らず読み直すので `null` を返す。
 */
fun applyNotificationBackfill(
    current: List<NotificationEntry>,
    page: List<NotificationGroup>,
    pageLimit: Int,
): List<NotificationEntry>? = if (page.size >= pageLimit) null else prependNotificationGroups(current, page)
