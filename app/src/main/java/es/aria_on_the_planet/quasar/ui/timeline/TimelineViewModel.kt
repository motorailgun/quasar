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
import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.ui.feed.PostActionController
import es.aria_on_the_planet.quasar.domain.model.TimelineType
import es.aria_on_the_planet.quasar.ui.feed.FeedState
import es.aria_on_the_planet.quasar.ui.feed.PagedFeed
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.ui.feed.PostFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import es.aria_on_the_planet.quasar.ui.feed.TimelineItem
import es.aria_on_the_planet.quasar.ui.feed.submitVote
import es.aria_on_the_planet.quasar.ui.notification.NotificationEntry
import es.aria_on_the_planet.quasar.ui.notification.appendNotificationPage
import es.aria_on_the_planet.quasar.ui.notification.mapGroups
import es.aria_on_the_planet.quasar.ui.notification.nextNotificationMaxId
import es.aria_on_the_planet.quasar.domain.model.settings.AppSettings
import es.aria_on_the_planet.quasar.domain.model.settings.NotificationApi
import es.aria_on_the_planet.quasar.domain.model.settings.StreamingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    val timelines: Map<TimelineType, PostFeedState>,
    val notifications: FeedState<NotificationEntry>,
) {
    /** 選択中カラムの状態 (読み込み中・エラー表示の判定用)。 */
    val current: FeedState<*>
        get() = feedOf(selected)

    /** カラムの状態。 */
    fun feedOf(column: ColumnType): FeedState<*> =
        column.timeline?.let { timelines.getValue(it) } ?: notifications
}

class TimelineViewModel(
    private val session: Session,
    private val repository: TimelineRepository,
    private val statusRepository: StatusRepository,
    private val notificationRepository: NotificationRepository,
    private val accountRepository: AccountRepository,
    private val streamingClient: StreamingClient,
    /** 設定 (読み込み後の値の流れ)。自動更新するカラムと通知の取得 API を決める。 */
    private val settings: Flow<AppSettings>,
) : ViewModel() {

    private val feeds: Map<TimelineType, PostFeed> = ColumnType.entries.mapNotNull { it.timeline }.associateWith { type ->
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
    ) { maxId -> notificationRepository.fetch(session, notificationApi(), maxId) }

    private suspend fun notificationApi(): NotificationApi = settings.first().notificationApi

    private val selected = MutableStateFlow(ColumnType.Home)

    val state: StateFlow<TimelineScreenState> = combine(
        listOf(selected, notifications.state) + feeds.values.map { it.state },
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        TimelineScreenState(
            selected = values[0] as ColumnType,
            timelines = feeds.keys.zip(values.drop(2).map { it as PostFeedState }).toMap(),
            notifications = values[1] as FeedState<NotificationEntry>,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, snapshot())

    init {
        refresh(selected.value)
        // ブロック・ミュート等をしたら、その相手のポスト・ブーストを一覧から除く
        viewModelScope.launch {
            accountRepository.relationshipChanges.collect { change -> feeds.values.forEach { it.hidePostsBy(change) } }
        }
        // 通知の取得 API を切り替えたら、混ざらないよう読み直す (未読み込みなら開いたときに新しい方で読む)
        viewModelScope.launch {
            settings.map { it.notificationApi }.distinctUntilChanged().drop(1).collect {
                if (notifications.state.value.loaded) notifications.refresh()
            }
        }
    }

    fun select(column: ColumnType) {
        selected.value = column
        ensureLoaded(column)
    }

    /** まだ一度も読み込んでいなければ読み込む (スワイプで隣のカラムが見え始めたとき等)。 */
    fun ensureLoaded(column: ColumnType) {
        val state = stateOf(column)
        if (!state.loaded && !state.isRefreshing) refresh(column)
    }

    fun refresh(column: ColumnType = selected.value) {
        val type = column.timeline
        if (type != null) feeds.getValue(type).refresh() else notifications.refresh()
    }

    fun loadMore(column: ColumnType = selected.value) {
        val type = column.timeline
        if (type != null) feeds.getValue(type).loadMore() else notifications.loadMore()
    }

    /** [streaming] の設定で自動更新するカラムだけを扱う [TimelineSync]。 */
    private fun syncFor(streaming: StreamingSettings, notificationApi: NotificationApi) = TimelineSync(
        scope = viewModelScope,
        timelines = feeds,
        streamedTimelines = streamedTimelinesFor(streaming),
        notifications = notifications.takeIf { streaming.isEnabled(ColumnType.Notifications) },
        fetchPosts = { type, maxId, sinceId -> repository.fetch(session, type, maxId = maxId, sinceId = sinceId) },
        fetchNewerNotifications = { sinceId -> notificationRepository.fetch(session, notificationApi, sinceId = sinceId) },
        toGroup = { notificationRepository.toGroup(it, notificationApi) },
        pageLimit = MastodonApi.DEFAULT_LIMIT,
        onError = { message, error -> Log.w(TAG, message, error) },
    )

    /** gap の読み込み用 (gap はタイムラインにしか無く、自動更新の設定には関わらない)。 */
    private val gapSync = syncFor(StreamingSettings(home = false, notifications = false, local = false), NotificationApi.Grouped)

    /**
     * ストリーミングでカラムを自動更新する。collect している間 (= 画面が前面にある間) だけ接続し、
     * 呼び出し側のコルーチンがキャンセルされると切断する。自動更新がオフのカラムの分は購読しない。
     * 設定が変わったら接続し直す (再接続時の補完で、オンにしたカラムの抜けも埋まる)。
     */
    suspend fun stream() {
        settings.map { it.streaming to it.notificationApi }.distinctUntilChanged().collectLatest { (streaming, api) ->
            val channels = streamChannelsFor(streaming)
            if (channels.isEmpty()) return@collectLatest
            val sync = syncFor(streaming, api)
            streamingClient.events(session, channels).collect(sync::handle)
        }
    }

    /** gap (未取得の区間) を上側から 1 ページ分埋める。 */
    fun fillGap(column: ColumnType, gap: TimelineItem.Gap) {
        val type = column.timeline ?: return
        gapSync.fillGap(type, gap)
    }

    suspend fun vote(post: Post, choices: List<Int>): Boolean =
        viewModelScope.submitVote(statusRepository, session, post, choices) { poll ->
            // 同じポストが複数のカラムに載っていることがあるので全てに反映する
            feeds.values.forEach { feed -> feed.updatePosts { it.withPoll(poll) } }
            notifications.updateItems { entry -> entry.mapGroups { it.copy(post = it.post?.withPoll(poll)) } }
        }

    private val postActions = PostActionController(
        scope = viewModelScope,
        send = { statusId, action -> statusRepository.perform(session, statusId, action) },
        // 同じポストが複数のカラム・通知に載っていることがあるので全てに反映する
        update = { transform ->
            feeds.values.forEach { feed -> feed.updatePosts(transform) }
            notifications.updateItems { entry -> entry.mapGroups { it.copy(post = it.post?.let(transform)) } }
        },
        onError = { message, error -> Log.w(TAG, message, error) },
    )

    /** ブースト・お気に入り・ブックマーク (またはその取り消し) を行う。 */
    fun performAction(post: Post, action: PostAction) = postActions.perform(post, action)

    private fun stateOf(column: ColumnType): FeedState<*> =
        column.timeline?.let { feeds.getValue(it).state.value } ?: notifications.state.value

    private companion object {
        const val TAG = "TimelineViewModel"
        /** 通知一覧の最大行数。 */
        const val MaxNotificationEntries = 400
    }

    private fun snapshot() = TimelineScreenState(
        selected = selected.value,
        timelines = feeds.mapValues { it.value.state.value },
        notifications = notifications.state.value,
    )
}
