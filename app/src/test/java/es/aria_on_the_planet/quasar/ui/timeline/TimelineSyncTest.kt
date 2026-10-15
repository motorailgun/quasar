package es.aria_on_the_planet.quasar.ui.timeline

import es.aria_on_the_planet.quasar.data.streaming.StreamChannel
import es.aria_on_the_planet.quasar.data.streaming.StreamEvent
import es.aria_on_the_planet.quasar.domain.model.Notification
import es.aria_on_the_planet.quasar.domain.model.NotificationGroup
import es.aria_on_the_planet.quasar.domain.model.NotificationType
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.TimelineType
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.asGroup
import es.aria_on_the_planet.quasar.ui.feed.PagedFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeed
import es.aria_on_the_planet.quasar.ui.feed.TimelineItem
import es.aria_on_the_planet.quasar.ui.notification.NotificationEntry
import es.aria_on_the_planet.quasar.ui.notification.appendNotificationPage
import es.aria_on_the_planet.quasar.ui.notification.nextNotificationMaxId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * TimelineSync の段取りのテスト。取得は偽物の関数で置き換え、一覧は実物の PostFeed / PagedFeed を使う。
 * Unconfined なので PagedFeed の launch は取得完了まで同期的に進む。
 */
class TimelineSyncTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val user = User("1", "alice", "", "https://e", "", "", emptyList())
    private fun post(id: Int) = Post(id.toString(), user, "", Instant.EPOCH, 0, 0, 0, emptyList(), statusId = id.toString())
    private fun posts(vararg ids: Int) = ids.map(::post)
    private fun notification(id: Int) = Notification(id.toString(), NotificationType.Mention, Instant.EPOCH, user, post(id))
    private fun group(id: Int) = notification(id).asGroup()

    /** 取得の記録と、要求に応じて返す内容を持つ偽物のサーバ。 */
    private inner class FakeServer {
        val postRequests = mutableListOf<Triple<TimelineType, String?, String?>>()
        val notificationRequests = mutableListOf<String?>()
        var timelines: MutableMap<TimelineType, (maxId: String?, sinceId: String?) -> List<Post>> = mutableMapOf()
        var notifications: (maxId: String?, sinceId: String?) -> List<NotificationGroup> = { _, _ -> emptyList() }
        var failPosts = false

        fun fetchPosts(type: TimelineType, maxId: String?, sinceId: String?): List<Post> {
            postRequests += Triple(type, maxId, sinceId)
            if (failPosts) throw IOException("boom")
            return timelines[type]?.invoke(maxId, sinceId).orEmpty()
        }

        fun fetchNotifications(maxId: String?, sinceId: String?): List<NotificationGroup> {
            notificationRequests += sinceId ?: maxId
            return notifications(maxId, sinceId)
        }
    }

    private val errors = mutableListOf<String>()

    private fun feedFor(server: FakeServer, type: TimelineType) =
        PostFeed(scope) { maxId -> server.fetchPosts(type, maxId, null) }

    private fun notificationFeed(server: FakeServer) = PagedFeed<NotificationGroup, NotificationEntry>(
        scope = scope,
        nextMaxIdOf = ::nextNotificationMaxId,
        append = { current, page -> appendNotificationPage(current, page) },
    ) { maxId -> server.fetchNotifications(maxId, null) }

    private fun sync(
        server: FakeServer,
        timelines: Map<TimelineType, PostFeed>,
        streamed: Set<TimelineType> = timelines.keys,
        notifications: PagedFeed<NotificationGroup, NotificationEntry>? = null,
        pageLimit: Int = 3,
    ) = TimelineSync(
        scope = scope,
        timelines = timelines,
        streamedTimelines = streamed,
        notifications = notifications,
        fetchPosts = server::fetchPosts,
        fetchNewerNotifications = { sinceId -> server.fetchNotifications(null, sinceId) },
        toGroup = { it.asGroup() },
        pageLimit = pageLimit,
        onError = { message, _ -> errors += message },
    )

    private fun describe(items: List<TimelineItem>) = items.map {
        when (it) {
            is TimelineItem.PostItem -> it.post.id
            is TimelineItem.Gap -> "gap(${it.maxId}..${it.sinceId})"
        }
    }

    // --- ストリームのイベント -------------------------------------------------------------------

    @Test
    fun updateGoesOnlyToTheColumnOfItsStream() = runBlocking {
        val server = FakeServer().apply {
            timelines[TimelineType.Home] = { _, _ -> posts(20, 10) }
            timelines[TimelineType.Local] = { _, _ -> posts(21, 11) }
        }
        val home = feedFor(server, TimelineType.Home).apply { refresh() }
        val local = feedFor(server, TimelineType.Local).apply { refresh() }
        val sync = sync(server, mapOf(TimelineType.Home to home, TimelineType.Local to local))

        sync.handle(StreamEvent.Update(StreamChannel.Local, post(30)))
        assertEquals(listOf("20", "10"), describe(home.state.value.items))
        assertEquals(listOf("30", "21", "11"), describe(local.state.value.items))
    }

    @Test
    fun deleteAndEditApplyToAllColumnsEvenIfNotStreamed() = runBlocking {
        val server = FakeServer().apply {
            timelines[TimelineType.Home] = { _, _ -> posts(20, 10) }
            timelines[TimelineType.Global] = { _, _ -> posts(20, 5) }
        }
        val home = feedFor(server, TimelineType.Home).apply { refresh() }
        val global = feedFor(server, TimelineType.Global).apply { refresh() }
        val sync = sync(server, mapOf(TimelineType.Home to home, TimelineType.Global to global), streamed = setOf(TimelineType.Home))

        sync.handle(StreamEvent.Delete("20"))
        assertEquals(listOf("10"), describe(home.state.value.items))
        assertEquals(listOf("5"), describe(global.state.value.items))
    }

    @Test
    fun eventsAreIgnoredByColumnsNotYetLoaded() = runBlocking {
        val server = FakeServer()
        val home = feedFor(server, TimelineType.Home) // refresh していない
        val notifications = notificationFeed(server)
        val sync = sync(server, mapOf(TimelineType.Home to home), notifications = notifications)

        sync.handle(StreamEvent.Update(StreamChannel.User, post(30)))
        sync.handle(StreamEvent.NotificationReceived(notification(40)))
        assertTrue(home.state.value.items.isEmpty())
        assertTrue(notifications.state.value.items.isEmpty())
    }

    @Test
    fun notificationIsPrependedWhenLoaded() = runBlocking {
        val server = FakeServer().apply { notifications = { _, _ -> listOf(group(10)) } }
        val notifications = notificationFeed(server).apply { refresh() }
        val sync = sync(server, emptyMap(), notifications = notifications)

        sync.handle(StreamEvent.NotificationReceived(notification(40)))
        assertEquals(listOf("ungrouped-40", "ungrouped-10"), notifications.state.value.items.map { it.key })
    }

    // --- 再接続時の補完 -------------------------------------------------------------------------

    @Test
    fun backfillUsesNewestIdAndMergesPartialPage() = runBlocking {
        val server = FakeServer().apply {
            timelines[TimelineType.Home] = { _, sinceId -> if (sinceId == null) posts(20, 10) else posts(40, 30) }
        }
        val home = feedFor(server, TimelineType.Home).apply { refresh() }
        val sync = sync(server, mapOf(TimelineType.Home to home), pageLimit = 3)

        sync.handle(StreamEvent.Connected)
        assertEquals(Triple(TimelineType.Home, null, "20"), server.postRequests.last())
        assertEquals(listOf("40", "30", "20", "10"), describe(home.state.value.items))
    }

    @Test
    fun backfillWithFullPageInsertsGap() = runBlocking {
        val server = FakeServer().apply {
            timelines[TimelineType.Home] = { _, sinceId -> if (sinceId == null) posts(20, 10) else posts(90, 80, 70) }
        }
        val home = feedFor(server, TimelineType.Home).apply { refresh() }
        val sync = sync(server, mapOf(TimelineType.Home to home), pageLimit = 3)

        sync.backfill()
        assertEquals(listOf("90", "80", "70", "gap(70..20)", "20", "10"), describe(home.state.value.items))
    }

    @Test
    fun backfillSkipsColumnsNotStreamedOrNotLoaded() = runBlocking {
        val server = FakeServer().apply {
            timelines[TimelineType.Home] = { _, _ -> posts(20) }
            timelines[TimelineType.Global] = { _, _ -> posts(20) }
        }
        val home = feedFor(server, TimelineType.Home) // 未読み込み
        val global = feedFor(server, TimelineType.Global).apply { refresh() } // ストリーム対象外
        val sync = sync(server, mapOf(TimelineType.Home to home, TimelineType.Global to global), streamed = setOf(TimelineType.Home))
        server.postRequests.clear()

        sync.backfill()
        assertEquals(emptyList<Any>(), server.postRequests)
    }

    @Test
    fun backfillFailureIsReportedAndLeavesListAsIs() = runBlocking {
        val server = FakeServer().apply { timelines[TimelineType.Home] = { _, _ -> posts(20, 10) } }
        val home = feedFor(server, TimelineType.Home).apply { refresh() }
        val sync = sync(server, mapOf(TimelineType.Home to home))
        server.failPosts = true

        sync.backfill()
        assertEquals(listOf("20", "10"), describe(home.state.value.items))
        assertEquals(listOf("Failed to backfill Home"), errors)
    }

    @Test
    fun notificationBackfillMergesPartialPageAndRefreshesOnFullPage() = runBlocking {
        val server = FakeServer().apply {
            notifications = { _, sinceId -> if (sinceId == null) listOf(group(10)) else listOf(group(30), group(20)) }
        }
        val notifications = notificationFeed(server).apply { refresh() }
        val sync = sync(server, emptyMap(), notifications = notifications, pageLimit = 3)

        sync.backfill()
        assertEquals(listOf("ungrouped-30", "ungrouped-20", "ungrouped-10"), notifications.state.value.items.map { it.key })
        assertEquals(listOf(null, "10"), server.notificationRequests)

        // 1 ページ分埋まって返ってきたら読み直す (since なしの取得が走る)
        server.notifications = { _, sinceId -> if (sinceId == null) listOf(group(60)) else listOf(group(50), group(45), group(40)) }
        sync.backfill()
        assertEquals(listOf(null, "10", "30", null), server.notificationRequests)
        assertEquals(listOf("ungrouped-60"), notifications.state.value.items.map { it.key })
    }

    // --- gap の読み込み -------------------------------------------------------------------------

    @Test
    fun fillGapRequestsRangeAndMovesGapDown() = runBlocking {
        val server = FakeServer().apply {
            timelines[TimelineType.Home] = { maxId, sinceId ->
                when {
                    sinceId == null && maxId == null -> posts(20, 10)
                    maxId == null -> posts(90, 80, 70) // 補完 (1 ページ分)
                    else -> posts(60, 50, 40) // gap の中 (まだ続く)
                }
            }
        }
        val home = feedFor(server, TimelineType.Home).apply { refresh() }
        val sync = sync(server, mapOf(TimelineType.Home to home), pageLimit = 3)
        sync.backfill()
        val gap = home.state.value.items.filterIsInstance<TimelineItem.Gap>().single()

        sync.fillGap(TimelineType.Home, gap)
        assertEquals(Triple(TimelineType.Home, "70", "20"), server.postRequests.last())
        assertEquals(
            listOf("90", "80", "70", "60", "50", "40", "gap(40..20)", "20", "10"),
            describe(home.state.value.items),
        )
    }

    @Test
    fun fillGapFailureClearsLoadingState() = runBlocking {
        val server = FakeServer().apply {
            timelines[TimelineType.Home] = { _, sinceId -> if (sinceId == null) posts(20, 10) else posts(90, 80, 70) }
        }
        val home = feedFor(server, TimelineType.Home).apply { refresh() }
        val sync = sync(server, mapOf(TimelineType.Home to home), pageLimit = 3)
        sync.backfill()
        val gap = home.state.value.items.filterIsInstance<TimelineItem.Gap>().single()
        server.failPosts = true

        sync.fillGap(TimelineType.Home, gap)
        val after = home.state.value.items.filterIsInstance<TimelineItem.Gap>().single()
        assertEquals(false, after.isLoading)
        assertEquals("Failed to fill gap", errors.last())
    }

    // --- ストリームとカラムの対応 ---------------------------------------------------------------

    @Test
    fun streamChannelsFollowAvailableColumns() {
        val all = ColumnType.entries
        assertEquals(setOf(StreamChannel.User, StreamChannel.Local), streamChannelsFor(all, includeGlobal = false))
        assertEquals(setOf(StreamChannel.User, StreamChannel.Local, StreamChannel.Public), streamChannelsFor(all, includeGlobal = true))
        // 未ログイン (Home・通知なし)
        assertEquals(setOf(StreamChannel.Local), streamChannelsFor(listOf(ColumnType.Local, ColumnType.Global), includeGlobal = false))
        assertEquals(TimelineType.Home, StreamChannel.User.timelineType)
    }
}
