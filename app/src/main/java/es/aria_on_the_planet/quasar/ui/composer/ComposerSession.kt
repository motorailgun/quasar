package es.aria_on_the_planet.quasar.ui.composer

import android.net.Uri
import android.util.Log
import es.aria_on_the_planet.quasar.data.media.UploadSource
import es.aria_on_the_planet.quasar.data.remote.MastodonApiException
import es.aria_on_the_planet.quasar.data.remote.userMessage
import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.repository.EmojiRepository
import es.aria_on_the_planet.quasar.data.repository.InstanceRepository
import es.aria_on_the_planet.quasar.data.repository.MediaRepository
import es.aria_on_the_planet.quasar.data.repository.StatusRepository
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.ComposeLimits
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostDraft
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.StatusSource
import es.aria_on_the_planet.quasar.domain.model.UploadFailure
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.editDraftOf
import es.aria_on_the_planet.quasar.domain.model.isEmpty
import es.aria_on_the_planet.quasar.domain.model.problems
import es.aria_on_the_planet.quasar.ui.component.composer.ComposerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

data class ComposerScreenState(
    val account: User? = null,
    /** 補完・強調に使うサーバのカスタム絵文字。取得前・失敗時は空 (補完が出ないだけ)。 */
    val emojis: List<CustomEmoji> = emptyList(),
    val limits: ComposeLimits = ComposeLimits(),
    val sending: Boolean = false,
    /** 送信失敗の内容。表示したら [ComposerSession.errorShown] で消す。 */
    val error: String? = null,
)

/** 編集する自分のポストと、その原文。 */
data class EditTarget(val post: Post, val source: StatusSource)

/**
 * 書きかけの投稿 1 つ分 (入力内容・添付のアップロード・送信)。画面からは独立していて、クイック投稿バーと
 * フルの投稿画面が同じものを共有する (バーから引き上げても、アップロード中の添付ごと続きを書ける)。
 *
 * [DraftStore] が作って持ち、投稿・破棄したら [close] して新しいものに替える。処理は [scope] 上で動くので、
 * 画面を切り替えても止まらない。返信先 ([ComposerState.inReplyTo]) もこの下書きの一部で、返信と通常の投稿で
 * 下書きを分けない。
 *
 * [editing] を渡すと既存のポストの編集になる (`PostViewModel` が作る。[DraftStore] の下書きとは別)。中身はポストの
 * 内容で始まり、送信は編集 API。公開範囲・返信先は変えられず、設定・アカウントの既定値も入れない。
 */
class ComposerSession(
    private val session: Session,
    private val scope: CoroutineScope,
    private val accountRepository: AccountRepository,
    private val instanceRepository: InstanceRepository,
    private val emojiRepository: EmojiRepository,
    private val statusRepository: StatusRepository,
    private val mediaRepository: MediaRepository,
    /** 設定の「新しい投稿の公開範囲」(`null` ならアカウントの既定)。読み込みを待ってから流れる。 */
    private val defaultVisibilitySetting: Flow<PostVisibility?>,
    /** 端末のファイルをアップロード元にする (`AppContainer.uploadSource`)。IO スレッドで呼ぶ。 */
    private val openUploadSource: (Uri) -> UploadSource,
    /** 編集するポスト。`null` なら新しい投稿。 */
    val editing: EditTarget? = null,
    /** 添付を送る前に中身を替える (設定でオンなら、上限を超える画像を作り直す)。`null` なら替えない。 */
    private val prepareUpload: (suspend (UploadSource, ComposeLimits) -> UploadSource)? = null,
    /** 投稿 (編集なら保存) できた。[DraftStore] がこの下書きを終えて空のものに替える。 */
    private val onPublished: (Post) -> Unit,
) {
    /** 入力内容。 */
    val composer = ComposerState()

    /** 編集前の内容 (編集のとき)。変更の有無 ([hasChanges]) の比較に使う。 */
    private val originalDraft = editing?.let { (post, source) ->
        // 投票の期間の選択肢はサーバの上限の取得前なので既定値で選ぶ (編集画面で選び直せる)
        editDraftOf(post, source, ComposeLimits().pollDurations, Instant.now()).also(composer::load)
    }

    val isEditing: Boolean get() = editing != null

    private val uploads = AttachmentUploads(
        scope = scope,
        attachments = { composer.attachments },
        setAttachments = { composer.attachments = it },
        limits = { _state.value.limits },
        upload = { source, description, onProgress, onProcessing ->
            mediaRepository.upload(session, source, description, onProgress, onProcessing)
        },
        // 編集では代替テキストを編集の送信 (`media_attributes`) でまとめて送る。投稿済みのメディアは
        // `PUT /api/v1/media/:id` で変えられない
        updateDescription = { mediaId, description ->
            if (editing == null) mediaRepository.updateDescription(session, mediaId, description)
        },
        onError = { Log.w(TAG, "Media upload failed", it) },
        prepare = prepareUpload,
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

    /** サーバの上限の取得を終えた (失敗しても既定値で終える)。 */
    private val limitsLoaded = CompletableDeferred<Unit>()

    /** 直前に送ろうとした内容と、その Idempotency-Key。同じ内容の再送では同じキーを使い、二重投稿を防ぐ。 */
    private var lastAttempt: Pair<PostDraft, String>? = null

    private var visibilitySetting: PostVisibility? = null
    private var accountDefaultVisibility: PostVisibility? = null

    /** 最後に自動で入れた公開範囲。今の値がこれと違えば、ユーザーが選び直した (または返信で変わった) ので触らない。 */
    private var appliedVisibility = composer.visibility

    init {
        scope.launch {
            // 取得できなければ既定値のまま (最終的な判定はサーバが行う)
            runCatchingLogged("instance") { instanceRepository.instance(session.serverUrl) }
                ?.let { info -> _state.update { it.copy(limits = info.composeLimits) } }
            limitsLoaded.complete(Unit)
        }
        scope.launch {
            runCatchingLogged("emojis") { emojiRepository.pickerEmojis(session.serverUrl) }
                ?.let { emojis -> _state.update { it.copy(emojis = emojis) } }
        }
        if (editing == null) {
            scope.launch {
                defaultVisibilitySetting.collect { setting ->
                    visibilitySetting = setting
                    applyDefaultVisibility()
                }
            }
        }
        scope.launch {
            val sensitiveBefore = composer.sensitive
            runCatchingLogged("account") { accountRepository.fetchMe(session) }?.let { me ->
                // 編集では既定値を入れない (公開範囲は変えられず、閲覧注意はポストのまま)
                if (editing != null) return@let
                accountDefaultVisibility = me.defaultVisibility
                applyDefaultVisibility()
                // 取得までの間にユーザーが選んでいたら上書きしない
                if (composer.sensitive == sensitiveBefore) composer.sensitive = me.defaultSensitive
            }
        }
        scope.launch {
            // プロフィールの編集でアイコン・名前が変わったら追従する
            accountRepository.myAccount(session).collect { me -> _state.update { it.copy(account = me.user) } }
        }
    }

    /** 公開範囲の初期値 (設定 → アカウントの既定) を、まだ誰も変えていなければ入れる。設定が変わったときも同様。 */
    private fun applyDefaultVisibility() {
        val value = visibilitySetting ?: accountDefaultVisibility ?: return
        if (composer.visibility != appliedVisibility) return
        composer.visibility = value
        appliedVisibility = value
    }

    /**
     * サーバの上限 (取得できなければ既定値) を待って返す。添付の枚数・サイズの検査は追加した時点の上限で行うので、
     * 開いた直後に添付するとき (共有で受けたメディア) は取得前の既定値で判定しないようにこれを待つ。
     */
    suspend fun awaitLimits(): ComposeLimits {
        limitsLoaded.await()
        return _state.value.limits
    }

    /** 閉じると失うものがあるか (新しい投稿なら何か書いたか、編集なら元の内容から変えたか)。スナップショットから読む。 */
    fun hasChanges(): Boolean {
        val draft = composer.toDraft()
        return if (originalDraft != null) draft != originalDraft else !draft.isEmpty
    }

    fun submit() {
        if (_state.value.sending) return
        val draft = composer.toDraft()
        if (draft.problems(_state.value.limits).isNotEmpty()) return
        val editing = editing
        if (editing != null) {
            send { statusRepository.edit(session, editing.post.statusId, draft) }
            return
        }
        val key = lastAttempt?.takeIf { it.first == draft }?.second ?: UUID.randomUUID().toString()
        lastAttempt = draft to key
        send { statusRepository.publish(session, draft, key) }
    }

    private fun send(request: suspend () -> Post) {
        _state.update { it.copy(sending = true, error = null) }
        scope.launch {
            try {
                val post = request()
                _state.update { it.copy(sending = false) }
                onPublished(post)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to post", e)
                _state.update { it.copy(sending = false, error = e.userMessage()) }
            }
        }
    }

    /**
     * [target] への返信にする (返信中なら返信先を付け替える)。本文・添付は残す。
     *
     * @param auto ポスト画面を開いたときの自動の返信か ([ComposerState.autoReply])。
     */
    fun startReply(target: Post, auto: Boolean = false) {
        composer.startReply(target, myAcct = _state.value.account?.screenName, auto = auto)
    }

    /** 返信をやめる (✕)。 */
    fun cancelReply() = composer.cancelReply()

    /**
     * ポスト画面を開いたとき: 何も書いていなければ [target] (主役のポスト) への返信にする (X と同じ)。
     * 書きかけ・返信ボタンで付けた返信先があれば何もしない。離れるときは [releaseAutoReply]。
     */
    fun startAutoReply(target: Post) {
        if (composer.canAutoReply(target)) startReply(target, auto = true)
    }

    /** ポスト画面を離れるとき: 自動で付けた返信を、何も書いていなければ外す。 */
    fun releaseAutoReply() = composer.releaseAutoReply()

    /** Photo Picker で選ばれたメディアを添付し、アップロードを始める。既に添付しているものは無視する。 */
    fun addMedia(uris: List<Uri>) {
        val attached = composer.attachments.mapNotNullTo(HashSet()) { it.previewUri }
        val added = uris.filter { it.toString() !in attached }
        if (added.isEmpty()) return
        scope.launch {
            val sources = runCatchingLogged("media") {
                withContext(Dispatchers.IO) { added.map(openUploadSource) }
            } ?: return@launch
            uploads.add(sources)
        }
    }

    fun removeAttachment(localId: String) = uploads.remove(localId)

    /** 埋め込みの Photo Picker で選択が外されたメディアを添付から外す。 */
    fun removeMedia(uris: List<Uri>) {
        val removed = uris.mapTo(HashSet()) { it.toString() }
        composer.attachments.filter { it.previewUri in removed }.forEach { uploads.remove(it.localId) }
    }

    fun retryAttachment(localId: String) = uploads.retry(localId)

    fun setAttachmentDescription(localId: String, description: String) = uploads.setDescription(localId, description)

    fun errorShown() {
        _state.update { it.copy(error = null) }
    }

    /** アップロード等を止める。以後は使わない。 */
    fun close() {
        scope.cancel()
    }

    private suspend fun <T> runCatchingLogged(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load $what", e)
        null
    }

    private companion object {
        const val TAG = "ComposerSession"
    }
}
