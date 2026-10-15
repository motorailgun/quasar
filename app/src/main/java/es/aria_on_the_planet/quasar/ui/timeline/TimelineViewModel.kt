package es.aria_on_the_planet.quasar.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.TimelineRepository
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.TimelineType
import es.aria_on_the_planet.quasar.ui.feed.PostFeed
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TimelineScreenState(
    val selected: TimelineType,
    val available: List<TimelineType>,
    val timelines: Map<TimelineType, PostFeedState>,
) {
    val current: PostFeedState get() = timelines.getValue(selected)
}

class TimelineViewModel(
    private val session: Session,
    private val repository: TimelineRepository,
) : ViewModel() {

    private val available = TimelineType.entries.filter {
        it != TimelineType.Home || session.accessToken != null
    }

    private val feeds: Map<TimelineType, PostFeed> = available.associateWith { type ->
        PostFeed(viewModelScope) { maxId -> repository.fetch(session, type, maxId) }
    }

    private val selected = MutableStateFlow(available.first())

    val state: StateFlow<TimelineScreenState> = combine(
        listOf(selected) + feeds.values.map { it.state },
    ) { values ->
        TimelineScreenState(
            selected = values[0] as TimelineType,
            available = available,
            timelines = available.zip(values.drop(1).map { it as PostFeedState }).toMap(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, snapshot())

    init {
        feeds.getValue(selected.value).refresh()
    }

    fun select(type: TimelineType) {
        selected.value = type
        val feed = feeds.getValue(type)
        if (!feed.state.value.loaded) feed.refresh()
    }

    fun refresh(type: TimelineType = selected.value) = feeds.getValue(type).refresh()

    fun loadMore(type: TimelineType = selected.value) = feeds.getValue(type).loadMore()

    private fun snapshot() = TimelineScreenState(
        selected = selected.value,
        available = available,
        timelines = feeds.mapValues { it.value.state.value },
    )
}
