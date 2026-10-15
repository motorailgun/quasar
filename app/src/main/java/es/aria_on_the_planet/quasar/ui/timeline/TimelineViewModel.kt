package es.aria_on_the_planet.quasar.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.TimelineRepository
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.TimelineType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimelineUiState(
    val posts: List<Post> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

data class TimelineScreenState(
    val selected: TimelineType,
    val available: List<TimelineType>,
    val timelines: Map<TimelineType, TimelineUiState>,
) {
    val current: TimelineUiState get() = timelines.getValue(selected)
}

class TimelineViewModel(
    private val session: Session,
    private val repository: TimelineRepository,
) : ViewModel() {

    private val available = TimelineType.entries.filter {
        it != TimelineType.Home || session.accessToken != null
    }

    private val _state = MutableStateFlow(
        TimelineScreenState(
            selected = available.first(),
            available = available,
            timelines = available.associateWith { TimelineUiState() },
        )
    )
    val state: StateFlow<TimelineScreenState> = _state.asStateFlow()

    init {
        refresh(_state.value.selected)
    }

    fun select(type: TimelineType) {
        _state.update { it.copy(selected = type) }
        if (!_state.value.timelines.getValue(type).loaded) refresh(type)
    }

    fun refresh(type: TimelineType = _state.value.selected) {
        val current = _state.value.timelines.getValue(type)
        if (current.isRefreshing) return
        updateTimeline(type) { it.copy(isRefreshing = true, error = null) }
        viewModelScope.launch {
            try {
                val posts = repository.fetch(session, type)
                updateTimeline(type) {
                    it.copy(posts = posts, isRefreshing = false, loaded = true, endReached = posts.isEmpty())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateTimeline(type) { it.copy(isRefreshing = false, loaded = true, error = e.message ?: e.toString()) }
            }
        }
    }

    fun loadMore(type: TimelineType = _state.value.selected) {
        val current = _state.value.timelines.getValue(type)
        if (current.isRefreshing || current.isLoadingMore || current.endReached) return
        val maxId = current.posts.lastOrNull()?.id ?: return
        updateTimeline(type) { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            try {
                val older = repository.fetch(session, type, maxId)
                updateTimeline(type) { s ->
                    val known = s.posts.mapTo(HashSet()) { it.id }
                    s.copy(
                        posts = s.posts + older.filter { it.id !in known },
                        isLoadingMore = false,
                        endReached = older.isEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateTimeline(type) { it.copy(isLoadingMore = false, error = e.message ?: e.toString()) }
            }
        }
    }

    private fun updateTimeline(type: TimelineType, transform: (TimelineUiState) -> TimelineUiState) {
        _state.update { s -> s.copy(timelines = s.timelines + (type to transform(s.timelines.getValue(type)))) }
    }
}
