package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.ComposeLimits
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.DraftAttachment
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.characterCount

/**
 * 投稿欄全体 (X の投稿画面の配置)。
 *
 * ```
 * (icon) [CW 欄]
 *        本文
 *        [添付] / [投票]                    ← ここまでスクロール
 * ─────────────────────────────────────
 * (残り)  [公開範囲][絵文字][メディア][投票][CW]  ← 下端に固定。ボタンは右寄せ
 * ```
 *
 * 状態は [state] に読み書きするだけで、送信は呼び出し側 (トップバーの Post ボタン等) が行う。
 * 高さいっぱいを使う前提なので、クイック投稿バーなど別の配置では各部品を直接組み合わせる。
 *
 * @param account 投稿するアカウント。取得前は `null` (アイコンはプレースホルダ)。
 * @param onAttachMedia メディア追加ボタン (端末のメディアを選ぶ画面を開く)。
 * @param onRemoveAttachment 添付の ✕。アップロードを止める等が要るなら差し替える。
 * @param onRetryAttachment 通信エラーで失敗した添付のタップ。
 * @param onAttachmentDescriptionChange 代替テキストのダイアログで保存したとき。
 * @param emojis サーバのカスタム絵文字。`:sho` の補完 (ツールバー直上の帯)、ピッカー、入力中の `:shortcode:` の強調に使う。
 * @param autoFocus 最初の表示で本文欄にフォーカスし、キーボードを出す。
 */
@Composable
fun Composer(
    state: ComposerState,
    account: User?,
    limits: ComposeLimits,
    modifier: Modifier = Modifier,
    emojis: List<CustomEmoji> = emptyList(),
    enabled: Boolean = true,
    autoFocus: Boolean = false,
    onAttachMedia: () -> Unit = {},
    onRemoveAttachment: (DraftAttachment) -> Unit = { state.removeAttachment(it.localId) },
    onRetryAttachment: (DraftAttachment) -> Unit = {},
    onAttachmentDescriptionChange: (DraftAttachment, String) -> Unit = { attachment, description ->
        state.attachments = state.attachments.map { if (it.localId == attachment.localId) it.copy(description = description) else it }
    },
) {
    val focusRequester = remember { FocusRequester() }
    val contentWarningFocusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    }
    val characterCount by remember(state, limits) { derivedStateOf { state.toDraft().characterCount(limits) } }
    val poll = state.poll
    val attachments = state.attachments
    // 補完はフォーカスのある方の欄 (本文 / CW) に対して出す
    var activeField by remember(state) { mutableStateOf(state.text) }
    // CW 欄を閉じた後は本文へ戻す
    val targetField = if (activeField === state.contentWarning && !state.contentWarningEnabled) state.text else activeField
    val suggestions by rememberEmojiSuggestions(targetField, emojis)
    val highlight = rememberEmojiShortcodeHighlight(emojis)
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var editingDescriptionOf by rememberSaveable { mutableStateOf<String?>(null) }
    // 動画 (GIF 含む) は単独でしか付けられないので、あればそれ以上足せない
    val canAddMedia = poll == null && attachments.size < limits.maxMediaAttachments && attachments.none { it.isVideoLike }
    val videoWithOthers = attachments.size > 1 && attachments.any { it.isVideoLike }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier) {
        Row(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        ) {
            ComposerAccountIcon(account)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).animateContentSize()) {
                ContentWarningField(
                    state = state.contentWarning,
                    visible = state.contentWarningEnabled,
                    outputTransformation = highlight,
                    modifier = Modifier
                        .focusRequester(contentWarningFocusRequester)
                        .onFocusChanged { if (it.hasFocus) activeField = state.contentWarning },
                )
                ComposerTextField(
                    state = state.text,
                    enabled = enabled,
                    minHeight = TextMinHeight,
                    outputTransformation = highlight,
                    // アイコン (40dp) の縦中央に 1 行目が来るように少し下げる
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused) activeField = state.text }
                        .padding(top = 8.dp),
                )
                AttachmentStrip(
                    attachments = attachments,
                    onRemove = onRemoveAttachment,
                    onEditDescription = { editingDescriptionOf = it.localId },
                    onRetry = onRetryAttachment,
                    canAddMore = enabled && canAddMedia,
                    onAdd = onAttachMedia,
                    modifier = Modifier.padding(top = 12.dp),
                )
                AnimatedVisibility(videoWithOthers) {
                    Text(
                        stringResource(R.string.composer_media_video_alone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                AnimatedVisibility(attachments.isNotEmpty()) {
                    SensitiveMediaToggle(
                        checked = state.sensitive,
                        onCheckedChange = { state.sensitive = it },
                    )
                }
                if (poll != null) {
                    PollEditor(
                        poll = poll,
                        onChange = { state.poll = it },
                        limits = limits,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        EmojiSuggestionStrip(suggestions)
        ComposerToolbar(
            modifier = Modifier.fillMaxWidth(),
            leading = { CharacterCounter(used = characterCount, max = limits.maxCharacters) },
        ) {
            VisibilityButton(
                visibility = state.visibility,
                onChange = { state.visibility = it },
                enabled = enabled,
            )
            EmojiButton(
                onClick = {
                    // シートの検索欄を使わない限りキーボードは要らないので閉じる
                    keyboard?.hide()
                    pickerOpen = true
                },
                active = pickerOpen,
                enabled = enabled && emojis.isNotEmpty(),
            )
            // メディアと投票は同時に付けられない (使えない方は無効の色)
            AttachMediaButton(
                onClick = onAttachMedia,
                active = attachments.isNotEmpty(),
                enabled = enabled && canAddMedia,
            )
            PollButton(
                checked = poll != null,
                onCheckedChange = { state.togglePoll() },
                enabled = enabled && attachments.isEmpty(),
            )
            ContentWarningButton(
                checked = state.contentWarningEnabled,
                onCheckedChange = { state.contentWarningEnabled = it },
                enabled = enabled,
            )
        }
    }

    val editing = editingDescriptionOf?.let { id -> attachments.firstOrNull { it.localId == id } }
    if (editing != null) {
        AttachmentDescriptionDialog(
            attachment = editing,
            maxLength = limits.maxDescriptionCharacters,
            onSave = { description ->
                onAttachmentDescriptionChange(editing, description)
                editingDescriptionOf = null
            },
            onDismiss = { editingDescriptionOf = null },
        )
    }

    if (pickerOpen) {
        EmojiPickerSheet(
            emojis = emojis,
            // 選んだ絵文字は直前に入力していた欄のカーソル位置へ入れる (シートは開いたままで、続けて選べる)
            onSelect = { emoji ->
                val field = targetField
                val (text, cursor) = insertEmojiAtCursor(field.text, field.selection, emoji.shortcode)
                field.edit {
                    replace(0, length, text)
                    selection = TextRange(cursor)
                }
            },
            onDismiss = {
                pickerOpen = false
                // 入力していた欄へ戻し、続けて打てるようにする
                val requester = if (targetField === state.contentWarning) contentWarningFocusRequester else focusRequester
                requester.requestFocus()
                keyboard?.show()
            },
        )
    }
}

private val TextMinHeight = 120.dp
