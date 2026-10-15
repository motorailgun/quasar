package es.aria_on_the_planet.quasar.ui.timeline

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.remote.MastodonApi
import es.aria_on_the_planet.quasar.data.repository.NotificationRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.TimelineRepository
import es.aria_on_the_planet.quasar.data.repository.withPoll
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.data.streaming.StreamingClient
import es.aria_on_the_planet.quasar.domain.model.NotificationGroup
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.TimelineType
import es.aria_on_the_planet.quasar.ui.feed.FeedState
import es.aria_on_the_planet.quasar.ui.feed.PagedFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import es.aria_on_the_planet.quasar.ui.feed.TimelineItem
import es.aria_on_the_planet.quasar.ui.feed.submitVote
import es.aria_on_the_planet.quasar.ui.notification.NotificationEntry
import es.aria_on_the_planet.quasar.ui.notification.appendNotificationPage
import es.aria_on_the_planet.quasar.ui.notification.mapGroups
import es.aria_on_the_planet.quasar.ui.notification.nextNotificationMaxId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** メイン画面のカラム。ポストのタイムラインに加えて通知を含む。 */
enum class ColumnType(val timeline: TimelineType?) {
    Home(TimelineType.Home),
    Notifications(null),
    Local(TimelineType.Local),
    Global(TimelineType.Global),
}

data class TimelineScreenState(
    val selected: ColumnType,
    val available: List<ColumnType>,
    val timelines: Map<TimelineType, PostFeedState>,
    val notifications: FeedState<NotificationEntry>,
) {
    /** 選択中カラムの状態 (読み込み中・エラー表示の判定用)。 */
    val current: FeedState<*>
        get() = selected.timeline?.let { timelines.getValue(it) } ?: notifications
}

class TimelineViewModel(
    private val session: Session,
    private val repository: TimelineRepository,
    private val statusRepository: StatusRepository,
    private val notificationRepository: NotificationRepository,
    private val streamingClient: StreamingClient,
) : ViewModel() {

    /** 投票できるか (要ログイン)。 */
    val canVote: Boolean = session.accessToken != null

    /** Home と通知はログイン時のみ。 */
    private val available = ColumnType.entries.filter {
        session.accessToken != null || (it != ColumnType.Home && it != ColumnType.Notifications)
    }

    private val feeds: Map<TimelineType, PostFeed> = available.mapNotNull { it.timeline }.associateWith { type ->
        PostFeed(viewModelScope) { maxId -> repository.fetch(session, type, maxId) }
    }

    // ページ境界で分かれたサーバ側グループの統合と、ユーザー単位のグルーピングは appendNotificationPage が行う
    private val notifications = PagedFeed<NotificationGroup, NotificationEntry>(
        scope = viewModelScope,
        nextMaxIdOf = ::nextNotificationMaxId,
        append = { current, page -> appendNotificationPage(current, page) },
        // ストリーム・補完で伸びたら古い側を捨てる (gap は作らない)
        trim = { it.take(MaxNotificationEntries) },
        nextMaxIdOfItems = { entries -> nextNotificationMaxId(entries.flatMap { it.groups }) },
    ) { maxId -> notificationRepository.fetch(session, maxId) }

    private val selected = MutableStateFlow(available.first())

    val state: StateFlow<TimelineScreenState> = combine(
        listOf(selected, notifications.state) + feeds.values.map { it.state },
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        TimelineScreenState(
            selected = values[0] as ColumnType,
            available = available,
            timelines = feeds.keys.zip(values.drop(2).map { it as PostFeedState }).toMap(),
            notifications = values[1] as FeedState<NotificationEntry>,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, snapshot())

    init {
        refresh(selected.value)
    }

    fun select(column: ColumnType) {
        selected.value = column
        if (!stateOf(column).loaded) refresh(column)
    }

    fun refresh(column: ColumnType = selected.value) {
        val type = column.timeline
        if (type != null) feeds.getValue(type).refresh() else notifications.refresh()
    }

    fun loadMore(column: ColumnType = selected.value) {
        val type = column.timeline
        if (type != null) feeds.getValue(type).loadMore() else notifications.loadMore()
    }

    private val sync = TimelineSync(
        scope = viewModelScope,
        timelines = feeds,
        streamedTimelines = streamChannelsFor(available, includeGlobal = StreamGlobalTimeline)
            .map { it.timelineType }
            .filterTo(HashSet()) { it in feeds },
        notifications = notifications.takeIf { ColumnType.Notifications in available },
        fetchPosts = { type, maxId, sinceId -> repository.fetch(session, type, maxId = maxId, sinceId = sinceId) },
        fetchNewerNotifications = { sinceId -> notificationRepository.fetch(session, sinceId = sinceId) },
        toGroup = notificationRepository::toGroup,
        pageLimit = MastodonApi.DEFAULT_LIMIT,
        onError = { message, error -> Log.w(TAG, message, error) },
    )

    /**
     * ストリーミングでカラムを自動更新する。collect している間 (= 画面が前面にある間) だけ接続し、
     * 呼び出し側のコルーチンがキャンセルされると切断する。グローバルは流量が多いので通常は購読しない。
     */
    suspend fun stream() {
        val channels = streamChannelsFor(available, includeGlobal = StreamGlobalTimeline)
        streamingClient.events(session, channels).collect(sync::handle)
    }

    /** gap (未取得の区間) を上側から 1 ページ分埋める。 */
    fun fillGap(column: ColumnType, gap: TimelineItem.Gap) {
        val type = column.timeline ?: return
        sync.fillGap(type, gap)
    }

    suspend fun vote(post: Post, choices: List<Int>): Boolean =
        viewModelScope.submitVote(statusRepository, session, post, choices) { poll ->
            // 同じポストが複数のカラムに載っていることがあるので全てに反映する
            feeds.values.forEach { feed -> feed.updatePosts { it.withPoll(poll) } }
            notifications.updateItems { entry -> entry.mapGroups { it.copy(post = it.post?.withPoll(poll)) } }
        }

    private fun stateOf(column: ColumnType): FeedState<*> =
        column.timeline?.let { feeds.getValue(it).state.value } ?: notifications.state.value

    private companion object {
        const val TAG = "TimelineViewModel"
        /** 通知一覧の最大行数。 */
        const val MaxNotificationEntries = 400

        /** グローバルもストリーミング・補完するか。流量が多いので通常は無効 (動作確認用のスイッチ)。 */
        const val StreamGlobalTimeline = false
    }

    private fun snapshot() = TimelineScreenState(
        selected = selected.value,
        available = available,
        timelines = feeds.mapValues { it.value.state.value },
        notifications = notifications.state.value,
    )
}
