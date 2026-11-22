package es.aria_on_the_planet.quasar.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.account.AccountContext
import es.aria_on_the_planet.quasar.domain.model.PostVersion
import es.aria_on_the_planet.quasar.ui.feed.LoadError
import es.aria_on_the_planet.quasar.ui.feed.toLoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostHistoryState(
    /** 古い順 (最後が今の版)。読み込む前は `null`。 */
    val versions: List<PostVersion>? = null,
    val isLoading: Boolean = false,
    val error: LoadError? = null,
)

/**
 * 編集履歴の版の画面。版の一覧はシート ([es.aria_on_the_planet.quasar.ui.component.post.PostHistoryMenu]) で取った
 * ものがあればそれを使い (`StatusRepository.cachedHistory`)、無ければ (プロセスの再生成等) 取り直す。
 */
class PostHistoryViewModel(
    account: AccountContext,
    private val statusId: String,
) : ViewModel() {
    private val repository = account.statusRepository

    private val _state = MutableStateFlow(PostHistoryState(versions = repository.cachedHistory(statusId)))
    val state: StateFlow<PostHistoryState> = _state.asStateFlow()

    init {
        if (_state.value.versions == null) load()
    }

    fun retry() = load()

    private fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val versions = repository.fetchHistory(statusId)
                _state.update { it.copy(versions = versions, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.toLoadError()) }
            }
        }
    }
}
