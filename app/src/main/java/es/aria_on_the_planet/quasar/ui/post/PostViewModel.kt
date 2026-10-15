package es.aria_on_the_planet.quasar.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.PostThread
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.session.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostScreenState(
    val thread: PostThread? = null,
    /** リプライを一列に並べた行。 */
    val replies: List<ReplyRow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class PostViewModel(
    private val session: Session,
    private val statusId: String,
    private val repository: StatusRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PostScreenState())
    val state: StateFlow<PostScreenState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val thread = repository.fetchThread(session, statusId)
                _state.value = PostScreenState(thread = thread, replies = linearReplies(thread.descendants))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: e.toString()) }
            }
        }
    }
}
