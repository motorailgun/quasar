package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
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
import es.aria_on_the_planet.quasar.domain.model.PostDraft
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import kotlin.time.Duration.Companion.seconds

/**
 * 投稿欄の入力状態。各部品はこれを読み書きし、送信時に [toDraft] で [PostDraft] にする。
 *
 * 投稿画面では ViewModel が持ち (画面回転で消えないように)、クイック投稿バーなどでは
 * [rememberComposerState] で作る。
 */
@Stable
class ComposerState(
    initialText: String = "",
    visibility: PostVisibility = PostVisibility.Public,
    sensitive: Boolean = false,
    val inReplyToId: String? = null,
) {
    val text = TextFieldState(initialText)
    val contentWarning = TextFieldState()

    /** CW 欄を出しているか。閉じると警告文は送らない (入力は残す)。 */
    var contentWarningEnabled by mutableStateOf(false)
    var visibility by mutableStateOf(visibility)
    var sensitive by mutableStateOf(sensitive)
    var poll by mutableStateOf<PollDraft?>(null)
    var attachments by mutableStateOf<List<DraftAttachment>>(emptyList())

    /** スナップショットから読むので、`derivedStateOf` 等の中で使えば入力に追従する。 */
    fun toDraft(): PostDraft = PostDraft(
        text = text.text.toString(),
        contentWarning = contentWarning.text.toString().takeIf { contentWarningEnabled },
        visibility = visibility,
        sensitive = sensitive,
        attachments = attachments,
        poll = poll,
        inReplyToId = inReplyToId,
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
    }

    companion object {
        /**
         * 本文・CW・公開範囲・投票を保存する。添付はアップロードの途中状態を戻せないので保存しない。
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
                    state.inReplyToId,
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
                    inReplyToId = saved[5] as String?,
                ).apply {
                    contentWarning.edit { replace(0, length, saved[1] as String) }
                    contentWarningEnabled = saved[2] as Boolean
                    val options = saved[6] as List<String>?
                    if (options != null) {
                        poll = PollDraft(options, (saved[7] as Long).seconds, saved[8] as Boolean)
                    }
                }
            },
        )
    }
}

/** 画面に閉じた投稿欄の状態 (クイック投稿バー等)。プロセスの再生成でも本文を保つ。 */
@Composable
fun rememberComposerState(
    visibility: PostVisibility = PostVisibility.Public,
    inReplyToId: String? = null,
): ComposerState = rememberSaveable(saver = ComposerState.Saver) {
    ComposerState(visibility = visibility, inReplyToId = inReplyToId)
}
