package es.aria_on_the_planet.quasar.ui.notification

import es.aria_on_the_planet.quasar.domain.model.NotificationGroup
import es.aria_on_the_planet.quasar.domain.model.NotificationType
import es.aria_on_the_planet.quasar.domain.model.NotificationType.Favourite
import es.aria_on_the_planet.quasar.domain.model.NotificationType.Follow
import es.aria_on_the_planet.quasar.domain.model.NotificationType.Mention
import es.aria_on_the_planet.quasar.domain.model.NotificationType.Reblog
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

/**
 * appendNotificationPage のテスト。
 * 通知は新しい順で、`id` が大きいほど新しい (= latestAt も新しい) とする。
 * 一覧の比較は [describe] で「キー(メンバー)」の文字列にして行う。
 */
class NotificationGroupingTest {
    private fun user(id: String) = User(id, "u$id", "", "https://e", "", "", emptyList())
    private fun post(id: String) = Post(id, user("me"), "", Instant.EPOCH, 0, 0, 0, emptyList())

    /** [who] が投稿 [postId] に [type] したサーバ側グループ (1 人・1 件)。id は通知 ID。 */
    private fun reaction(id: Int, who: String, type: NotificationType = Favourite, postId: String = "p$id") =
        NotificationGroup(
            key = "ungrouped-$id", type = type, count = 1, accounts = listOf(user(who)),
            post = post(postId), latestAt = Instant.ofEpochSecond(id.toLong()), pageMinId = id.toString(),
        )

    private fun mention(id: Int, who: String = "m") =
        reaction(id, who, Mention).copy(key = "ungrouped-$id")

    private fun follow(id: Int, who: String) =
        reaction(id, who, Follow).copy(post = null)

    /** 複数人がまとまったサーバ側グループ。 */
    private fun serverGroup(id: Int, whos: List<String>, type: NotificationType = Favourite) =
        reaction(id, whos.first(), type).copy(key = "$type-p$id", count = whos.size, accounts = whos.map(::user))

    private fun describe(entries: List<NotificationEntry>): List<String> = entries.map { entry ->
        when (entry) {
            is NotificationEntry.Single -> entry.group.key.removePrefix("ungrouped-")
            is NotificationEntry.AccountReactions ->
                "${entry.type}:${entry.account.inServerId}(" + entry.members.joinToString(",") { it.key.removePrefix("ungrouped-") } + ")"
        }
    }

    private fun refresh(page: List<NotificationGroup>) = appendNotificationPage(emptyList(), page)

    // --- 1 ページ内のグルーピング -------------------------------------------------------------

    @Test
    fun groupsThreeReactionsByOneUserAtNewestPosition() {
        val result = refresh(listOf(reaction(10, "a"), mention(9), reaction(8, "a"), reaction(7, "b"), reaction(6, "a")))
        assertEquals(listOf("Favourite:a(10,8,6)", "9", "7"), describe(result))
    }

    @Test
    fun doesNotGroupBelowThreshold() {
        val result = refresh(listOf(reaction(10, "a"), reaction(9, "a"), reaction(8, "b")))
        assertEquals(listOf("10", "9", "8"), describe(result))
    }

    @Test
    fun countsFavouritesAndBoostsSeparately() {
        val result = refresh(
            listOf(reaction(10, "a"), reaction(9, "a", Reblog), reaction(8, "a"), reaction(7, "a", Reblog)),
        )
        assertEquals(listOf("10", "9", "8", "7"), describe(result))

        val withThreeBoosts = refresh(
            listOf(reaction(10, "a", Reblog), reaction(9, "a"), reaction(8, "a", Reblog), reaction(7, "a", Reblog)),
        )
        assertEquals(listOf("Reblog:a(10,8,7)", "9"), describe(withThreeBoosts))
    }

    @Test
    fun ignoresNonReactionAndMultiAccountGroups() {
        val result = refresh(
            listOf(
                follow(10, "a"), follow(9, "a"), follow(8, "a"),
                // a を含む複数人のサーバ側グループはそのまま
                serverGroup(7, listOf("a", "b")),
                reaction(6, "a"), reaction(5, "a"),
            ),
        )
        assertEquals(listOf("10", "9", "8", "Favourite-p7", "6", "5"), describe(result))
    }

    @Test
    fun separateUsersFormSeparateGroups() {
        val result = refresh(
            listOf(reaction(10, "a"), reaction(9, "b"), reaction(8, "a"), reaction(7, "b"), reaction(6, "a"), reaction(5, "b")),
        )
        assertEquals(listOf("Favourite:a(10,8,6)", "Favourite:b(9,7,5)"), describe(result))
    }

    @Test
    fun emptyPageReturnsCurrentAsIs() {
        val current = refresh(listOf(reaction(10, "a")))
        assertSame(current, appendNotificationPage(current, emptyList()))
    }

    // --- 追加読み込み: 固定部分 (下から 2 番目まで) への吸収 -------------------------------------------

    @Test
    fun absorbsIntoExistingUserGroupInFixedPartWithoutMovingIt() {
        val first = refresh(
            listOf(reaction(20, "a"), reaction(19, "a"), reaction(18, "a"), mention(17), mention(16)),
        )
        assertEquals(listOf("Favourite:a(20,19,18)", "17", "16"), describe(first))

        val second = appendNotificationPage(first, listOf(reaction(15, "a"), mention(14)))
        assertEquals(listOf("Favourite:a(20,19,18,15)", "17", "16", "14"), describe(second))
    }

    @Test
    fun rebuildsFromSinglesInFixedPartWhenThresholdReached() {
        // 1 ページ目では a は 2 件で未グループ
        val first = refresh(listOf(reaction(20, "a"), mention(19), reaction(18, "a"), mention(17)))
        assertEquals(listOf("20", "19", "18", "17"), describe(first))

        // 2 ページ目に a がもう 1 件 → a の最も新しい位置 (20) に作り直し、18 は取り除く
        val second = appendNotificationPage(first, listOf(reaction(16, "a"), mention(15)))
        assertEquals(listOf("Favourite:a(20,18,16)", "19", "17", "15"), describe(second))
    }

    @Test
    fun doesNotTouchFixedPartWhenThresholdNotReached() {
        val first = refresh(listOf(reaction(20, "a"), mention(19), mention(18)))
        val second = appendNotificationPage(first, listOf(reaction(17, "a"), mention(16)))
        assertEquals(listOf("20", "19", "18", "17", "16"), describe(second))
    }

    @Test
    fun lastItemIsReevaluatedWithNewPage() {
        // a は最後の行 (18) にだけいる → 固定部分ではないので、新しいページと合わせて再評価部分でまとめる
        val first = refresh(listOf(mention(20), mention(19), reaction(18, "a")))
        val second = appendNotificationPage(first, listOf(reaction(17, "a"), mention(16), reaction(15, "a")))
        assertEquals(listOf("20", "19", "Favourite:a(18,17,15)", "16"), describe(second))
    }

    @Test
    fun lastItemUserGroupKeepsGrowingWithStableKey() {
        val first = refresh(listOf(mention(20), reaction(19, "a"), reaction(18, "a"), reaction(17, "a")))
        assertEquals(listOf("20", "Favourite:a(19,18,17)"), describe(first))
        val key = first.last().key

        val second = appendNotificationPage(first, listOf(reaction(16, "a"), mention(15)))
        assertEquals(listOf("20", "Favourite:a(19,18,17,16)", "15"), describe(second))
        assertEquals(key, second[1].key)
    }

    @Test
    fun newUserInNewPageIsGroupedWithinRegionOnly() {
        val first = refresh(listOf(mention(20), mention(19)))
        val second = appendNotificationPage(
            first,
            listOf(reaction(18, "b"), mention(17), reaction(16, "b"), reaction(15, "b")),
        )
        assertEquals(listOf("20", "19", "Favourite:b(18,16,15)", "17"), describe(second))
    }

    @Test
    fun fixedSinglesAndRegionCombineAcrossTailAndNewPage() {
        // 固定部分に a が 1 件、最後の行も a、新しいページにも a → 合計 3 件で固定部分の位置に作り直す
        val first = refresh(listOf(reaction(20, "a"), mention(19), reaction(18, "a")))
        val second = appendNotificationPage(first, listOf(reaction(17, "a"), mention(16)))
        assertEquals(listOf("Favourite:a(20,18,17)", "19", "16"), describe(second))
    }

    // --- ページ境界で分かれたサーバ側グループ ----------------------------------------------------------

    @Test
    fun mergesServerGroupContinuationAcrossPages() {
        val first = refresh(listOf(mention(20), serverGroup(19, listOf("a", "b"))))
        val continuation = serverGroup(19, listOf("c")).copy(count = 3, pageMinId = "12")
        val second = appendNotificationPage(first, listOf(continuation, mention(11)))
        assertEquals(listOf("20", "Favourite-p19", "11"), describe(second))
        val merged = (second[1] as NotificationEntry.Single).group
        assertEquals(listOf("a", "b", "c"), merged.accounts.map { it.inServerId })
        assertEquals("12", merged.pageMinId)
    }

    @Test
    fun mergesContinuationInsideUserGroup() {
        val first = refresh(listOf(reaction(20, "a"), reaction(19, "a"), reaction(18, "a"), mention(17), mention(16)))
        // 18 のサーバ側グループの続き (同じキー) が来た
        val continuation = reaction(18, "a").copy(pageMinId = "5")
        val second = appendNotificationPage(first, listOf(continuation, mention(4)))
        assertEquals(listOf("Favourite:a(20,19,18)", "17", "16", "4"), describe(second))
        val member = (second[0] as NotificationEntry.AccountReactions).members.last()
        assertEquals("5", member.pageMinId)
    }

    @Test
    fun deduplicatesSameKeyWithinOnePage() {
        val result = refresh(listOf(serverGroup(10, listOf("a")), serverGroup(10, listOf("b")).copy(pageMinId = "3")))
        assertEquals(listOf("Favourite-p10"), describe(result))
        assertEquals(listOf("a", "b"), (result[0] as NotificationEntry.Single).group.accounts.map { it.inServerId })
    }

    // --- 不変条件 ------------------------------------------------------------------------------

    @Test
    fun keysStayUniqueAndNothingIsLostAcrossManyPages() {
        // a, b, c がランダム気味に混ざる 60 件を 5 件ずつ読み込む
        val whos = listOf("a", "b", "a", "c", "a", "b", "b", "c", "a", "c")
        val all = (60 downTo 1).map { id ->
            if (id % 7 == 0) mention(id) else reaction(id, whos[id % whos.size], if (id % 5 == 0) Reblog else Favourite)
        }
        var entries = emptyList<NotificationEntry>()
        all.chunked(5).forEach { page ->
            entries = appendNotificationPage(entries, page)
            val keys = entries.map { it.key }
            assertEquals("keys must be unique: $keys", keys.size, keys.toSet().size)
        }
        // 全通知がちょうど 1 回ずつ含まれる
        val contained = entries.flatMap { it.groups }.map { it.key }.sorted()
        assertEquals(all.map { it.key }.sorted(), contained)
        // ユーザー単位の行は閾値以上で、同じ (種類, ユーザー) は 1 つだけ
        val reactions = entries.filterIsInstance<NotificationEntry.AccountReactions>()
        reactions.forEach { assertEquals(true, it.members.size >= AccountGroupingThreshold) }
        assertEquals(reactions.size, reactions.map { it.type to it.account.inServerId }.toSet().size)
        // ユーザー単位の行のメンバーは新しい順
        reactions.forEach { r ->
            assertEquals(r.members.sortedByDescending { it.latestAt }, r.members)
        }
    }

    @Test
    fun nextMaxIdIsOldestInRawPage() {
        val page = listOf(serverGroup(30, listOf("a")).copy(pageMinId = "12"), reaction(20, "b"), reaction(15, "c"))
        assertEquals("12", nextNotificationMaxId(page))
        assertEquals(null, nextNotificationMaxId(emptyList()))
    }
}
