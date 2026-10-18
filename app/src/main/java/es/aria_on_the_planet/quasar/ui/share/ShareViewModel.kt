package es.aria_on_the_planet.quasar.ui.share

import android.net.Uri
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.composer.ComposerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 共有から開いた投稿画面の下書き。アプリ全体の下書き (`DraftStore`) とは別で、書きかけの投稿・返信先と混ざらない。
 * 閉じたら捨てる (破棄の確認は `ComposerScreen`)。
 *
 * 作られたときに共有の中身を入れる。プロセスが終わって作り直されたときも intent から入れ直す (書き足した分は消える)。
 *
 * @param createDraft 下書きを作る (`AppContainer.composerSession`)。2 つ目の引数は投稿できたとき。
 */
class ShareViewModel(
    createDraft: (CoroutineScope, (Post) -> Unit) -> ComposerSession,
    content: SharedContent,
) : ViewModel() {
    private val _published = MutableStateFlow(false)

    /** 投稿できた (画面を閉じる)。 */
    val published: StateFlow<Boolean> = _published.asStateFlow()

    val draft: ComposerSession = createDraft(viewModelScope) { _published.value = true }

    private val _skippedMedia = MutableStateFlow(content.rejectedMedia)

    /** 添付できなかったメディアの数 (安全でない URI・上限の枚数を超えた分)。知らせたら [skippedMediaShown]。 */
    val skippedMedia: StateFlow<Int> = _skippedMedia.asStateFlow()

    init {
        if (content.text.isNotEmpty()) draft.composer.text.setTextAndPlaceCursorAtEnd(content.text)
        if (content.media.isNotEmpty()) {
            viewModelScope.launch {
                // 上限はサーバごとに違う (既定値の 4 枚で切らない)
                val limits = draft.awaitLimits()
                val accepted = content.media.take(limits.maxMediaAttachments)
                draft.addMedia(accepted.map(Uri::parse))
                _skippedMedia.update { it + content.media.size - accepted.size }
            }
        }
    }

    fun skippedMediaShown() {
        _skippedMedia.value = 0
    }
}
