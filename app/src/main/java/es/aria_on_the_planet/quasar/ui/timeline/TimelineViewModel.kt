package es.aria_on_the_planet.quasar.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.NotificationRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.repository.TimelineRepository
import es.aria_on_the_planet.quasar.data.repository.withPoll
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Notification
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.TimelineType
import es.aria_on_the_planet.quasar.ui.feed.FeedState
import es.aria_on_the_planet.quasar.ui.feed.PagedFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import es.aria_on_the_planet.quasar.ui.feed.submitVote
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
    val notifications: FeedState<Notification>,
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

    private val notifications = PagedFeed<Notification>(viewModelScope, { it.id }) { maxId ->
        notificationRepository.fetch(session, maxId)
    }

    private val selected = MutableStateFlow(available.first())

    val state: StateFlow<TimelineScreenState> = combine(
        listOf(selected, notifications.state) + feeds.values.map { it.state },
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        TimelineScreenState(
            selected = values[0] as ColumnType,
            available = available,
            timelines = feeds.keys.zip(values.drop(2).map { it as PostFeedState }).toMap(),
            notifications = values[1] as FeedState<Notification>,
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

    suspend fun vote(post: Post, choices: List<Int>): Boolean =
        viewModelScope.submitVote(statusRepository, session, post, choices) { poll ->
            // 同じポストが複数のカラムに載っていることがあるので全てに反映する
            feeds.values.forEach { feed -> feed.updatePosts { it.withPoll(poll) } }
            notifications.updateItems { n -> n.copy(post = n.post?.withPoll(poll)) }
        }

    private fun stateOf(column: ColumnType): FeedState<*> =
        column.timeline?.let { feeds.getValue(it).state.value } ?: notifications.state.value

    private fun snapshot() = TimelineScreenState(
        selected = selected.value,
        available = available,
        timelines = feeds.mapValues { it.value.state.value },
        notifications = notifications.state.value,
    )
}
