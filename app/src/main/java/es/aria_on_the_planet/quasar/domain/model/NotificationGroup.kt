package es.aria_on_the_planet.quasar.domain.model

import java.time.Instant

/**
 * 通知のグループ。v2 (grouped notifications) ではサーバが favourite / reblog / follow 等を
 * 「同じ種類・同じ対象・近い時間帯」でまとめる。v1 の通知や、まとめられない種別は 1 件のグループになる。
 */
data class NotificationGroup(
    /** グループを識別するキー (不透明な値として扱う)。 */
    val key: String,
    val type: NotificationType,
    /** グループに含まれる通知の総数 (ページをまたいだ合計)。 */
    val count: Int,
    /** 操作したアカウントのうち代表 (最近のもの)。全員とは限らない。 */
    val accounts: List<User>,
    /** 対象のポスト。種別によっては `null`。 */
    val post: Post?,
    /** グループ内で最も新しい通知の日時。不明なら `null`。 */
    val latestAt: Instant?,
    /** このページに含まれる最も古い通知の ID。次ページ取得の `max_id` に使う。 */
    val pageMinId: String,
) {
    /**
     * ページ境界で分かれた同じグループの続き ([older]) を統合する。
     * 件数はグループ全体の合計なので足さず、アカウントは重複を除いて追加する。
     */
    fun mergeOlder(older: NotificationGroup): NotificationGroup = copy(
        count = maxOf(count, older.count),
        accounts = (accounts + older.accounts).distinctBy { it.inServerId },
        post = post ?: older.post,
        latestAt = latestAt ?: older.latestAt,
        pageMinId = older.pageMinId,
    )
}

/** v1 の通知を 1 件だけのグループとして扱う。 */
fun Notification.asGroup() = NotificationGroup(
    key = "ungrouped-$id",
    type = type,
    count = 1,
    accounts = listOf(account),
    post = post,
    latestAt = createdAt,
    pageMinId = id,
)
