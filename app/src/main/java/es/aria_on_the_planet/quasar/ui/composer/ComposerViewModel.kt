package es.aria_on_the_planet.quasar.ui.composer

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.aria_on_the_planet.quasar.data.media.UploadSource
import es.aria_on_the_planet.quasar.data.remote.MastodonApiException
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.repository.EmojiRepository
import es.aria_on_the_planet.quasar.data.repository.InstanceRepository
import es.aria_on_the_planet.quasar.data.repository.MediaRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.ComposeLimits
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.PostDraft
import es.aria_on_the_planet.quasar.domain.model.UploadFailure
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.problems
import es.aria_on_the_planet.quasar.ui.component.composer.ComposerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ComposerScreenState(
    val account: User? = null,
    /** 補完・強調に使うサーバのカスタム絵文字。取得前・失敗時は空 (補完が出ないだけ)。 */
    val emojis: List<CustomEmoji> = emptyList(),
    val limits: ComposeLimits = ComposeLimits(),
    val sending: Boolean = false,
    /** 送信失敗の内容。表示したら [ComposerViewModel.errorShown] で消す。 */
    val error: String? = null,
    /** 投稿できた (画面を閉じる)。 */
    val posted: Boolean = false,
)

class ComposerViewModel(
    private val session: Session,
    private val accountRepository: AccountRepository,
    private val instanceRepository: InstanceRepository,
    private val emojiRepository: EmojiRepository,
    private val statusRepository: StatusRepository,
    private val mediaRepository: MediaRepository,
    /** 端末のファイルをアップロード元にする (`AppContainer.uploadSource`)。IO スレッドで呼ぶ。 */
    private val openUploadSource: (Uri) -> UploadSource,
) : ViewModel() {
    /** 入力内容。画面回転で消えないよう ViewModel が持つ。 */
    val composer = ComposerState()

    private val uploads = AttachmentUploads(
        scope = viewModelScope,
        attachments = { composer.attachments },
        setAttachments = { composer.attachments = it },
        limits = { _state.value.limits },
        upload = { source, description, onProgress, onProcessing ->
            mediaRepository.upload(session, source, description, onProgress, onProcessing)
        },
        updateDescription = { mediaId, description -> mediaRepository.updateDescription(session, mediaId, description) },
        onError = { Log.w(TAG, "Media upload failed", it) },
        classifyFailure = { e ->
            when ((e as? MastodonApiException)?.code) {
                401, 403 -> UploadFailure.NotAllowed
                413 -> UploadFailure.TooLarge
                415 -> UploadFailure.Unsupported
                422 -> UploadFailure.Rejected
                else -> UploadFailure.Failed
            }
        },
    )

    private val _state = MutableStateFlow(ComposerScreenState())
    val state: StateFlow<ComposerScreenState> = _state.asStateFlow()

    /** 直前に送ろうとした内容と、その Idempotency-Key。同じ内容の再送では同じキーを使い、二重投稿を防ぐ。 */
    private var lastAttempt: Pair<PostDraft, String>? = null

    init {
        viewModelScope.launch {
            // 取得できなければ既定値のまま (最終的な判定はサーバが行う)
            runCatchingLogged("instance") { instanceRepository.instance(session.serverUrl) }
                ?.let { info -> _state.update { it.copy(limits = info.composeLimits) } }
        }
        viewModelScope.launch {
            runCatchingLogged("emojis") { emojiRepository.pickerEmojis(session.serverUrl) }
                ?.let { emojis -> _state.update { it.copy(emojis = emojis) } }
        }
        viewModelScope.launch {
            val visibilityBefore = composer.visibility
            val sensitiveBefore = composer.sensitive
            runCatchingLogged("account") { accountRepository.fetchMe(session) }?.let { me ->
                _state.update { it.copy(account = me.user) }
                // 取得までの間にユーザーが選んでいたら上書きしない
                if (composer.visibility == visibilityBefore) composer.visibility = me.defaultVisibility
                if (composer.sensitive == sensitiveBefore) composer.sensitive = me.defaultSensitive
            }
        }
    }

    fun submit() {
        if (_state.value.sending) return
        val draft = composer.toDraft()
        if (draft.problems(_state.value.limits).isNotEmpty()) return
        val key = lastAttempt?.takeIf { it.first == draft }?.second ?: UUID.randomUUID().toString()
        lastAttempt = draft to key
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                statusRepository.publish(session, draft, key)
                _state.update { it.copy(sending = false, posted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to post", e)
                _state.update { it.copy(sending = false, error = e.userMessage()) }
            }
        }
    }

    /** Photo Picker で選ばれたメディアを添付し、アップロードを始める。 */
    fun addMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val sources = runCatchingLogged("media") {
                withContext(Dispatchers.IO) { uris.map(openUploadSource) }
            } ?: return@launch
            uploads.add(sources)
        }
    }

    fun removeAttachment(localId: String) = uploads.remove(localId)

    fun retryAttachment(localId: String) = uploads.retry(localId)

    fun setAttachmentDescription(localId: String, description: String) = uploads.setDescription(localId, description)

    fun errorShown() {
        _state.update { it.copy(error = null) }
    }

    /** Mastodon のエラー応答 (`{"error": "..."}`) ならその文言、それ以外は例外のメッセージ。 */
    private fun Exception.userMessage(): String =
        (this as? MastodonApiException)?.message?.let { ErrorField.find(it)?.groupValues?.get(1) }
            ?: message
            ?: javaClass.simpleName

    private suspend fun <T> runCatchingLogged(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load $what", e)
        null
    }

    private companion object {
        const val TAG = "ComposerViewModel"
        val ErrorField = Regex(""""error"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    }
}
