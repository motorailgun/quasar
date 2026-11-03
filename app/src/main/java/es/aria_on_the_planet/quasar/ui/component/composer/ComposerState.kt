package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import es.aria_on_the_planet.quasar.domain.model.DraftAttachment
import es.aria_on_the_planet.quasar.domain.model.PollDraft
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostDraft
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.ReplyFields
import es.aria_on_the_planet.quasar.domain.model.ReplyMemory
import es.aria_on_the_planet.quasar.domain.model.isUntouched
import es.aria_on_the_planet.quasar.domain.model.cancelReply as cancelReplyFields
import es.aria_on_the_planet.quasar.domain.model.startReply as startReplyFields
import kotlin.time.Duration.Companion.seconds

/**
 * 投稿欄の入力状態。各部品はこれを読み書きし、送信時に [toDraft] で [PostDraft] にする。
 *
 * 通常は [es.aria_on_the_planet.quasar.ui.composer.ComposerSession] が持ち、単独で使うときは
 * [rememberComposerState] で作る。
 */
@Stable
class ComposerState(
    initialText: String = "",
    visibility: PostVisibility = PostVisibility.Public,
    sensitive: Boolean = false,
) {
    val text = TextFieldState(initialText)
    val contentWarning = TextFieldState()

    /** CW 欄を出しているか。閉じると警告文は送らない (入力は残す)。 */
    var contentWarningEnabled by mutableStateOf(false)
    var visibility by mutableStateOf(visibility)
    var sensitive by mutableStateOf(sensitive)
    var poll by mutableStateOf<PollDraft?>(null)
    var attachments by mutableStateOf<List<DraftAttachment>>(emptyList())

    /** 返信先。返信でなければ `null`。 */
    var inReplyTo by mutableStateOf<Post?>(null)
        private set

    /** 返信のために自動で変えた内容 (宛先・公開範囲・CW)。返信の付け替え・取り消しで戻すのに使う。 */
    private var replyMemory by mutableStateOf<ReplyMemory?>(null)

    /**
     * [target] への返信にする (返信中なら返信先を付け替える)。宛先を本文に入れ、公開範囲を狭め、CW を引き継ぐ。
     * ユーザーが書いた本文・選んだ値は変えない (詳細は [es.aria_on_the_planet.quasar.domain.model.startReply])。
     *
     * @param myAcct ログイン中のアカウントの acct (宛先から除く)。取得前は `null`。
     */
    fun startReply(target: Post, myAcct: String?) {
        val (fields, memory) = startReplyFields(replyFields(), replyMemory, target, myAcct)
        applyReplyFields(fields)
        replyMemory = memory
        inReplyTo = target
    }

    /** 返信をやめる (✕)。自動で変えた内容のうち、ユーザーが変えていないものを返信前に戻す。 */
    fun cancelReply() {
        val memory = replyMemory ?: return
        applyReplyFields(cancelReplyFields(replyFields(), memory))
        replyMemory = null
        inReplyTo = null
    }

    /** 何も書いていないか (自動で入れた宛先・CW だけか)。スナップショットから読む。 */
    fun isUntouched(): Boolean =
        replyFields().isUntouched(replyMemory, hasAttachments = attachments.isNotEmpty(), hasPoll = poll != null)

    private fun replyFields() = ReplyFields(
        text = text.text.toString(),
        visibility = visibility,
        contentWarningEnabled = contentWarningEnabled,
        contentWarning = contentWarning.text.toString(),
    )

    private fun applyReplyFields(fields: ReplyFields) {
        if (text.text.toString() != fields.text) text.setTextAndPlaceCursorAtEnd(fields.text)
        if (contentWarning.text.toString() != fields.contentWarning) {
            contentWarning.setTextAndPlaceCursorAtEnd(fields.contentWarning)
        }
        visibility = fields.visibility
        contentWarningEnabled = fields.contentWarningEnabled
    }

    /** スナップショットから読むので、`derivedStateOf` 等の中で使えば入力に追従する。 */
    fun toDraft(): PostDraft = PostDraft(
        text = text.text.toString(),
        contentWarning = contentWarning.text.toString().takeIf { contentWarningEnabled },
        visibility = visibility,
        sensitive = sensitive,
        attachments = attachments,
        poll = poll,
        inReplyToId = inReplyTo?.statusId,
    )

    /** 投票の有無を切り替える (メディアがある間は付けられない)。 */
    fun togglePoll() {
        poll = if (poll == null && attachments.isEmpty()) PollDraft() else null
    }

    fun removeAttachment(localId: String) {
        attachments = attachments.filterNot { it.localId == localId }
    }

    /** 送信後などに空へ戻す (公開範囲・閲覧注意の設定は保つ)。 */
    fun clear() {
        text.clearText()
        contentWarning.clearText()
        contentWarningEnabled = false
        poll = null
        attachments = emptyList()
        replyMemory = null
        inReplyTo = null
    }

    /** [draft] の内容を入れる (ポストの編集の最初)。返信先は持たない ([PostDraft.inReplyToId] は使わない)。 */
    fun load(draft: PostDraft) {
        clear()
        text.setTextAndPlaceCursorAtEnd(draft.text)
        contentWarning.setTextAndPlaceCursorAtEnd(draft.contentWarning.orEmpty())
        contentWarningEnabled = draft.contentWarning != null
        visibility = draft.visibility
        sensitive = draft.sensitive
        poll = draft.poll
        attachments = draft.attachments
    }

    companion object {
        /**
         * 本文・CW・公開範囲・閲覧注意・投票を保存する。添付はアップロードの途中状態を戻せないので、返信先はポストを
         * 持てないので保存しない。
         */
        val Saver: Saver<ComposerState, Any> = listSaver(
            save = { state ->
                val poll = state.poll
                listOf(
                    state.text.text.toString(),
                    state.contentWarning.text.toString(),
                    state.contentWarningEnabled,
                    state.visibility.name,
                    state.sensitive,
                    poll?.options,
                    poll?.expiresIn?.inWholeSeconds,
                    poll?.multiple,
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                ComposerState(
                    initialText = saved[0] as String,
                    visibility = PostVisibility.valueOf(saved[3] as String),
                    sensitive = saved[4] as Boolean,
                ).apply {
                    contentWarning.edit { replace(0, length, saved[1] as String) }
                    contentWarningEnabled = saved[2] as Boolean
                    val options = saved[5] as List<String>?
                    if (options != null) {
                        poll = PollDraft(options, (saved[6] as Long).seconds, saved[7] as Boolean)
                    }
                }
            },
        )
    }
}

/** 画面に閉じた投稿欄の状態。プロセスの再生成でも本文を保つ。今は呼び出し元が無い (どの投稿欄も `ComposerSession` の状態を使う)。 */
@Composable
fun rememberComposerState(
    visibility: PostVisibility = PostVisibility.Public,
): ComposerState = rememberSaveable(saver = ComposerState.Saver) {
    ComposerState(visibility = visibility)
}
