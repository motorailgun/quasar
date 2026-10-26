package es.aria_on_the_planet.quasar.ui.openlink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.repository.ResolvedLink
import es.aria_on_the_planet.quasar.data.repository.SearchRepository
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.ui.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface OpenLinkState {
    data object Loading : OpenLinkState

    /** 開く画面が決まった ([route] で置き換える)。 */
    data class Resolved(val route: Route) : OpenLinkState

    /** サーバで見つからなかった (見られない・取れない)。 */
    data object NotFound : OpenLinkState
    data class Failed(val message: String) : OpenLinkState
}

/** 他のアプリから共有された [query] (URL・`@user@domain`) を接続中サーバで引き、開く画面を決める。 */
class OpenLinkViewModel(
    private val session: Session,
    val query: String,
    private val repository: SearchRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<OpenLinkState>(OpenLinkState.Loading)
    val state: StateFlow<OpenLinkState> = _state.asStateFlow()

    init {
        resolve()
    }

    fun resolve() {
        _state.value = OpenLinkState.Loading
        viewModelScope.launch {
            _state.value = try {
                when (val link = repository.resolve(session, query)) {
                    is ResolvedLink.ToPost -> OpenLinkState.Resolved(Route.Post(link.post.statusId))
                    is ResolvedLink.ToAccount -> OpenLinkState.Resolved(Route.Profile(link.user.inServerId))
                    is ResolvedLink.ToHashtag -> OpenLinkState.Resolved(Route.Hashtag(link.name))
                    ResolvedLink.NotFound -> OpenLinkState.NotFound
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OpenLinkState.Failed(e.message ?: e.toString())
            }
        }
    }
}
