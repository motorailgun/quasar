package es.aria_on_the_planet.quasar.ui.notification

import es.aria_on_the_planet.quasar.domain.model.NotificationGroup
import es.aria_on_the_planet.quasar.domain.model.NotificationType
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import java.time.Instant

/** 通知一覧の 1 行。 */
sealed interface NotificationEntry {
    /** LazyColumn のキー。一覧内で一意。 */
    val key: String

    /** 含まれるサーバ側グループ。 */
    val groups: List<NotificationGroup>

    /** サーバ側グループ ([NotificationGroup]) 1 つをそのまま表示する行。 */
    data class Single(val group: NotificationGroup) : NotificationEntry {
        override val key: String get() = group.key
        override val groups: List<NotificationGroup> get() = listOf(group)
    }

    /**
     * 1 人のユーザーが複数のポストにした同じ種類のリアクションをまとめた行 (クライアント側のグルーピング)。
     * 例:「〇〇 favourited 3 of your posts」。
     *
     * @param members 1 ポスト分ずつのグループ。新しい順。
     */
    data class AccountReactions(
        val type: NotificationType,
        val account: User,
        val members: List<NotificationGroup>,
    ) : NotificationEntry {
        override val key: String get() = accountReactionsKey(type, account.inServerId)
        override val groups: List<NotificationGroup> get() = members
        val latestAt: Instant? get() = members.firstOrNull()?.latestAt
        val latestPost: Post? get() = members.firstOrNull()?.post
    }
}

internal fun accountReactionsKey(type: NotificationType, accountId: String) = "account-$type-$accountId"

/** 含まれる各グループを書き換える (投票結果の反映など)。 */
fun NotificationEntry.mapGroups(transform: (NotificationGroup) -> NotificationGroup): NotificationEntry = when (this) {
    is NotificationEntry.Single -> copy(group = transform(group))
    is NotificationEntry.AccountReactions -> copy(members = members.map(transform))
}
