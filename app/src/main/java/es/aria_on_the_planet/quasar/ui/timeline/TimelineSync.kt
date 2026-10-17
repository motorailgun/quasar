package es.aria_on_the_planet.quasar.ui.timeline

import es.aria_on_the_planet.quasar.data.streaming.StreamChannel
import es.aria_on_the_planet.quasar.data.streaming.StreamEvent
import es.aria_on_the_planet.quasar.domain.model.Notification
import es.aria_on_the_planet.quasar.domain.model.NotificationGroup
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.TimelineType
import es.aria_on_the_planet.quasar.domain.model.settings.StreamingSettings
import es.aria_on_the_planet.quasar.ui.feed.PagedFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeed
import es.aria_on_the_planet.quasar.ui.feed.TimelineItem
import es.aria_on_the_planet.quasar.ui.feed.applyNewerPage
import es.aria_on_the_planet.quasar.ui.feed.applyStreamEvent
import es.aria_on_the_planet.quasar.ui.feed.fillGap
import es.aria_on_the_planet.quasar.ui.feed.newestPostId
import es.aria_on_the_planet.quasar.ui.feed.setGapLoading
import es.aria_on_the_planet.quasar.ui.notification.NotificationEntry
import es.aria_on_the_planet.quasar.ui.notification.applyNotificationBackfill
import es.aria_on_the_planet.quasar.ui.notification.applyStreamEvent
import es.aria_on_the_planet.quasar.ui.notification.newestNotificationId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** ストリームが流すタイムライン。 */
val StreamChannel.timelineType: TimelineType
    get() = when (this) {
        StreamChannel.User -> TimelineType.Home
        StreamChannel.Local -> TimelineType.Local
        StreamChannel.Public -> TimelineType.Global
    }

/** [column] の自動更新が設定でオンか。 */
fun StreamingSettings.isEnabled(column: ColumnType): Boolean = when (column) {
    ColumnType.Home -> home
    ColumnType.Notifications -> notifications
    ColumnType.Local -> local
    ColumnType.Global -> global
}

/**
 * 自動更新するカラムに必要なストリーム。Home・通知は `user`、ローカル・グローバルはそれぞれのストリーム。
 * [settings] でオフのカラムの分は購読しない (Home と通知の両方がオフなら `user` も購読しない)。
 */
fun streamChannelsFor(columns: Collection<ColumnType>, settings: StreamingSettings): Set<StreamChannel> =
    columns.filter(settings::isEnabled).mapTo(HashSet()) { column ->
        when (column) {
            ColumnType.Home, ColumnType.Notifications -> StreamChannel.User
            ColumnType.Local -> StreamChannel.Local
            ColumnType.Global -> StreamChannel.Public
        }
    }

/** 自動更新するタイムライン (通知を除く)。 */
fun streamedTimelinesFor(columns: Collection<ColumnType>, settings: StreamingSettings): Set<TimelineType> =
    columns.filter(settings::isEnabled).mapNotNullTo(HashSet()) { it.timeline }

/**
 * ストリーミングによるカラムの自動更新 (イベントの適用・再接続時の補完・gap の読み込み) の段取り。
 * 一覧の書き換え自体は純粋関数 (`applyStreamEvent` / `applyNewerPage` / `applyNotificationBackfill` / `fillGap`) が行い、
 * ここはどのカラムに当てるか・`since_id` に何を使うか・取得の失敗をどう扱うかを決める。
 *
 * @param timelines 全てのタイムラインカラム。削除・編集はストリームしていないカラムにも当てる。
 * @param streamedTimelines ストリームで新着を受け、再接続時に補完するタイムライン。それ以外のタイムラインは、
 *   同じストリームで新着が届いても (Home がオフで通知がオンの `user` 等) 足さない。
 * @param notifications 自動更新する通知カラム。ログインしていない・自動更新がオフなら `null`。
 * @param fetchPosts `maxId` より古い / `sinceId` より新しいポストを取得する。
 * @param fetchNewerNotifications `sinceId` より新しい通知を取得する。
 * @param toGroup ストリームで届いた通知を、通知一覧と同じキー体系のグループにする。
 * @param pageLimit 1 回の取得の件数。これだけ埋まって返ってきたら、間に未取得の区間がありうると判断する。
 * @param onError 取得の失敗の報告 (ログなど)。失敗しても一覧はそのままにする。
 */
class TimelineSync(
    private val scope: CoroutineScope,
    private val timelines: Map<TimelineType, PostFeed>,
    private val streamedTimelines: Set<TimelineType>,
    private val notifications: PagedFeed<NotificationGroup, NotificationEntry>?,
    private val fetchPosts: suspend (type: TimelineType, maxId: String?, sinceId: String?) -> List<Post>,
    private val fetchNewerNotifications: suspend (sinceId: String) -> List<NotificationGroup>,
    private val toGroup: (Notification) -> NotificationGroup,
    private val pageLimit: Int,
    private val onError: (message: String, error: Throwable) -> Unit,
) {
    /** ストリームのイベントを処理する。[StreamEvent.Connected] (再接続を含む) では切断中の抜けを補完する。 */
    suspend fun handle(event: StreamEvent) {
        if (event == StreamEvent.Connected) {
            backfill()
            return
        }
        // 新着は自動更新がオンのカラムにだけ足す (削除・編集はどのカラムにも当てる)
        val target = (event as? StreamEvent.Update)?.channel?.timelineType?.takeIf { it in streamedTimelines }
        for ((type, feed) in timelines) {
            // 未読み込みのカラムには当てない (初回の読み込みで最新が取れる)
            if (!feed.state.value.loaded) continue
            feed.updateList { applyStreamEvent(it, event, isTarget = type == target) }
        }
        notifications?.takeIf { it.state.value.loaded }?.updateList { applyStreamEvent(it, event, toGroup) }
    }

    /**
     * 切断中に取りこぼした分を、各カラムの最新 ID 以降 (`since_id`) で取得して加える。
     * タイムラインは 1 ページ分埋まったら gap を挟み、通知は読み直す。読み込み前・読み込み中のカラムは飛ばす。
     */
    suspend fun backfill() {
        for (type in streamedTimelines) {
            val feed = timelines[type] ?: continue
            val state = feed.state.value
            if (!state.loaded || state.isRefreshing) continue
            val sinceId = newestPostId(state.items) ?: continue
            val page = fetchOrReport("backfill $type") { fetchPosts(type, null, sinceId) } ?: continue
            feed.updateList { applyNewerPage(it, sinceId, page, pageLimit) }
        }
        val feed = notifications ?: return
        val state = feed.state.value
        if (!state.loaded || state.isRefreshing) return
        val sinceId = newestNotificationId(state.items) ?: return
        val page = fetchOrReport("backfill notifications") { fetchNewerNotifications(sinceId) } ?: return
        var needsRefresh = false
        feed.updateList { items -> applyNotificationBackfill(items, page, pageLimit) ?: items.also { needsRefresh = true } }
        if (needsRefresh) feed.refresh()
    }

    /** gap (未取得の区間) を上側から 1 ページ分埋める。まだ続きがあれば gap は取得分の下へ移る。 */
    fun fillGap(type: TimelineType, gap: TimelineItem.Gap) {
        val feed = timelines[type] ?: return
        if (gap.isLoading) return
        feed.updateList { setGapLoading(it, gap, loading = true) }
        scope.launch {
            val page = fetchOrReport("fill gap") { fetchPosts(type, gap.maxId, gap.sinceId) }
            feed.updateList { items ->
                if (page == null) setGapLoading(items, gap, loading = false) else fillGap(items, gap, page, pageLimit)
            }
        }
    }

    private suspend fun <T> fetchOrReport(label: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError("Failed to $label", e)
        null
    }
}
