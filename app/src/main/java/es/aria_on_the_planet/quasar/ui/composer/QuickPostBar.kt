package es.aria_on_the_planet.quasar.ui.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.characterCount
import es.aria_on_the_planet.quasar.domain.model.isEmpty
import es.aria_on_the_planet.quasar.domain.model.problems
import es.aria_on_the_planet.quasar.ui.component.composer.AttachMediaButton
import es.aria_on_the_planet.quasar.ui.component.composer.AttachmentStrip
import es.aria_on_the_planet.quasar.ui.component.composer.CharacterCounter
import es.aria_on_the_planet.quasar.ui.component.composer.ComposerAccountIcon
import es.aria_on_the_planet.quasar.ui.component.composer.ComposerTextField
import es.aria_on_the_planet.quasar.ui.component.composer.ComposerToolbar
import es.aria_on_the_planet.quasar.ui.component.composer.EmojiButton
import es.aria_on_the_planet.quasar.ui.component.composer.EmojiPickerSheet
import es.aria_on_the_planet.quasar.ui.component.composer.EmojiSuggestionStrip
import es.aria_on_the_planet.quasar.ui.component.composer.VisibilityButton
import es.aria_on_the_planet.quasar.ui.component.composer.insertEmoji
import es.aria_on_the_planet.quasar.ui.component.composer.rememberEmojiShortcodeHighlight
import es.aria_on_the_planet.quasar.ui.component.composer.rememberEmojiSuggestions
import kotlinx.coroutines.launch

/**
 * メイン画面の下に浮かぶクイック投稿バー。後ろの一覧 ([hazeState] の source) をぼかして重ねる。
 *
 * ```
 *          ───              ← ピル: 上へ引っ張るとフルの投稿画面 ([onExpand])
 * [絵文字の候補]
 * [添付 (小)]
 * (icon) 本文 ...   [送信]
 * (残り)  [公開範囲][絵文字][メディア]   ← フォーカス中か入力があるときだけ
 * ```
 *
 * 入力していない間はバー全体がタップ領域で、どこを押しても本文の入力を始める (ボタンはそれぞれの操作)。
 * 下書きは [session] (フル画面と共有)。CW と投票はフル画面で編集し、付いているときはチップで示す (タップで広げる)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickPostBar(
    session: ComposerSession,
    hazeState: HazeState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val composer = session.composer
    val limits = state.limits
    val enabled = !state.sending
    val canSubmit by remember(composer, limits) { derivedStateOf { composer.toDraft().problems(limits).isEmpty() } }
    val isEmpty by remember(composer) { derivedStateOf { composer.toDraft().isEmpty } }
    val characterCount by remember(composer, limits) { derivedStateOf { composer.toDraft().characterCount(limits) } }
    val suggestions by rememberEmojiSuggestions(composer.text, state.emojis)
    val highlight = rememberEmojiShortcodeHighlight(state.emojis)
    val openMediaPicker = rememberMediaPicker(session)

    var focused by remember { mutableStateOf(false) }
    var emojiPickerOpen by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val attachments = composer.attachments
    val canAddMedia = composer.poll == null &&
        attachments.size < limits.maxMediaAttachments &&
        attachments.none { it.isVideoLike }

    // 入力中の「戻る」はフォーカスを外すだけ (キーボードは先に閉じる)
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = focused,
        onBackCompleted = { focusManager.clearFocus() },
    )

    val shape = RoundedCornerShape(BarCornerRadius)
    val background = MaterialTheme.colorScheme.surface
    val tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = BarTintAlpha)
    val blurStyle = remember(background, tint) {
        HazeBlurStyle {
            blurRadius(BarBlurRadius)
            // 一覧そのものには背景が無い (Scaffold が塗っている) ので、下地を敷かないとぼかした結果が透けて
            // 後ろの実物がくっきり見えてしまう
            backgroundColor(background)
            colorEffects(listOf(HazeColorEffect.tint(tint)))
        }
    }
    val startTyping = stringResource(R.string.quick_post_start_typing)
    Column(
        modifier
            .clip(shape)
            .hazeBlur(input = HazeInput.Sources(hazeState), style = blurStyle)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            // 入力していない間はどこを押しても入力を始める (本文欄だけでは当たり判定が小さい)。
            // 子のボタン等が受けたタップはそちらが優先され、押せないもの (空の送信ボタン等) はここに回る
            .clickable(
                enabled = enabled && !focused,
                onClickLabel = startTyping,
                interactionSource = null,
                indication = null,
            ) {
                focusRequester.requestFocus()
                keyboard?.show()
            }
            .animateContentSize(),
    ) {
        ExpandHandle(onExpand = onExpand)
        EmojiSuggestionStrip(suggestions)
        AttachmentStrip(
            attachments = attachments,
            onRemove = { session.removeAttachment(it.localId) },
            // 代替テキストはフル画面で編集する
            onEditDescription = { onExpand() },
            onRetry = { session.retryAttachment(it.localId) },
            canAddMore = enabled && canAddMedia,
            onAdd = openMediaPicker,
            height = AttachmentHeight,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        DraftExtras(
            hasPoll = composer.poll != null,
            hasContentWarning = composer.contentWarningEnabled,
            onClick = onExpand,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ComposerAccountIcon(state.account, size = 32.dp, modifier = Modifier.align(Alignment.Top).padding(top = 4.dp))
            Spacer(Modifier.width(10.dp))
            ComposerTextField(
                state = composer.text,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge,
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = MaxLines),
                outputTransformation = highlight,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
            )
            Spacer(Modifier.width(8.dp))
            SendButton(enabled = canSubmit && enabled, sending = state.sending, onClick = session::submit)
        }
        AnimatedVisibility(focused || !isEmpty) {
            ComposerToolbar(leading = { CharacterCounter(used = characterCount, max = limits.maxCharacters) }) {
                VisibilityButton(
                    visibility = composer.visibility,
                    onChange = { composer.visibility = it },
                    compact = true,
                    enabled = enabled,
                )
                EmojiButton(
                    onClick = {
                        keyboard?.hide()
                        emojiPickerOpen = true
                    },
                    active = emojiPickerOpen,
                    enabled = enabled && state.emojis.isNotEmpty(),
                )
                AttachMediaButton(
                    onClick = openMediaPicker,
                    active = attachments.isNotEmpty(),
                    enabled = enabled && canAddMedia,
                )
            }
        }
    }

    if (emojiPickerOpen) {
        EmojiPickerSheet(
            emojis = state.emojis,
            onSelect = { composer.text.insertEmoji(it) },
            onDismiss = {
                emojiPickerOpen = false
                focusRequester.requestFocus()
                keyboard?.show()
            },
        )
    }
}

/**
 * 上端のピル。上へ引っ張ると指に合わせてバーが伸び、[ExpandDistance] を超えるか上へ素早く払うと [onExpand]。
 * 足りなければ元の高さへ戻る。タップは受けない (バー全体のタップ = 入力開始に回る)。
 */
@Composable
private fun ExpandHandle(onExpand: () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var drag by remember { mutableFloatStateOf(0f) }
    val expandDistance = with(density) { ExpandDistance.toPx() }
    val description = stringResource(R.string.quick_post_expand)
    val draggableState = rememberDraggableState { delta ->
        // 上方向 (負の delta) で伸びる
        drag = (drag - delta).coerceIn(0f, expandDistance * 2)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HandleHeight)
            .semantics { contentDescription = description }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (drag > expandDistance || velocity < -FlingVelocity) onExpand()
                    scope.launch { animate(drag, 0f) { value, _ -> drag = value } }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
        )
    }
    // 引っ張った分だけバーを伸ばす
    Spacer(Modifier.height(with(density) { drag.toDp() }))
}

/**
 * 送信ボタン。押せない間は同じ見た目のただのアイコンにする: Material のボタンは無効でもタップを受け止めるので、
 * そのままだとバー全体のタップ (入力開始) に回らない。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SendButton(enabled: Boolean, sending: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.composer_post)
    if (enabled || sending) {
        FilledIconButton(onClick = onClick, enabled = enabled) {
            if (sending) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = description)
            }
        }
    } else {
        val colors = IconButtonDefaults.filledIconButtonColors()
        // IconButton と同じ大きさ (48dp の枠に 40dp の円)
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(40.dp).background(colors.disabledContainerColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = description, tint = colors.disabledContentColor)
            }
        }
    }
}

/** フル画面でしか編集できないもの (投票・CW) が付いていることを示すチップ。タップでフル画面へ。 */
@Composable
private fun DraftExtras(hasPoll: Boolean, hasContentWarning: Boolean, onClick: () -> Unit) {
    if (!hasPoll && !hasContentWarning) return
    Row(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasContentWarning) {
            AssistChip(
                onClick = onClick,
                label = { Text(stringResource(R.string.quick_post_has_cw)) },
                leadingIcon = { Icon(Icons.Outlined.WarningAmber, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
        if (hasPoll) {
            AssistChip(
                onClick = onClick,
                label = { Text(stringResource(R.string.quick_post_has_poll)) },
                leadingIcon = { Icon(Icons.Outlined.Poll, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
}

private val BarCornerRadius = 28.dp
private val BarBlurRadius = 24.dp
private const val BarTintAlpha = 0.72f
private val HandleHeight = 20.dp
private val AttachmentHeight = 64.dp
private const val MaxLines = 5
private val ExpandDistance = 96.dp
private const val FlingVelocity = 1500f
