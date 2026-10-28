package es.aria_on_the_planet.quasar.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.ProfileLimits
import es.aria_on_the_planet.quasar.domain.model.countFieldCharacters
import es.aria_on_the_planet.quasar.ui.component.composer.EmojiSuggestionStrip
import es.aria_on_the_planet.quasar.ui.component.composer.rememberEmojiShortcodeHighlight
import es.aria_on_the_planet.quasar.ui.component.composer.rememberEmojiSuggestions
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.player.MediaOverlayColors

/**
 * 表示と編集欄の切り替え。表示 ([display]) と編集 ([edit]) を重ねてフェードし、高さは motionScheme の spring で
 * 伸び縮みさせる (下の項目は押し下げられる)。
 */
@Composable
internal fun EditSwitch(
    editing: Boolean,
    modifier: Modifier = Modifier,
    display: @Composable () -> Unit,
    edit: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = editing,
        modifier = modifier,
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using
                SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
        },
        contentAlignment = Alignment.TopStart,
        label = "profileEdit",
    ) { if (it) edit() else display() }
}

/** 画像 (ヘッダ・アイコン) を替えられることを示す膜。暗い面 + 写真の追加のアイコンで、タップで [onClick] (画像を選ぶ)。 */
@Composable
internal fun ImageEditOverlay(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .drawBehind { drawRect(MediaOverlayColors.Scrim) }
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Symbols.Outlined.AddPhotoAlternate,
            contentDescription = contentDescription,
            tint = MediaOverlayColors.Content,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * プロフィールの入力欄 1 つ。文字数 ([count]) が [max] を超えたらエラー表示。[counterFrom] 文字を超えるまでは数を出さない
 * (フィールドのように上限が十分大きいもの)。カスタム絵文字の `:shortcode:` を色付きにし、フォーカス中は `:sho` の
 * 補完の候補を欄の下に出す。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun ProfileTextField(
    state: TextFieldState,
    label: String,
    max: Int,
    count: (String) -> Int,
    emojis: List<CustomEmoji>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    counterFrom: Int = 0,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val length by remember(state) { derivedStateOf { count(state.text.toString()) } }
    val suggestions by rememberEmojiSuggestions(state, emojis)
    val showSuggestions = focused && suggestions != null
    // 入力欄が自分で見せるのはカーソルの行だけなので、欄の下端・文字数・絵文字の候補までキーボードの上に出す
    val bringIntoView = remember { BringIntoViewRequester() }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(focused, showSuggestions, imeVisible) {
        if (focused) {
            // 候補の帯が開き切る・キーボードが出切るのを待つ
            delay(BringIntoViewDelayMillis)
            bringIntoView.bringIntoView()
        }
    }
    Column(modifier.bringIntoViewRequester(bringIntoView)) {
        OutlinedTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused },
            enabled = enabled,
            label = { Text(label) },
            isError = length > max,
            // 表示中のフィールドの面 (16dp の角丸) に合わせる
            shape = MaterialTheme.shapes.large,
            outputTransformation = rememberEmojiShortcodeHighlight(emojis),
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = 3),
            supportingText = if (length >= counterFrom) {
                { Text("$length / $max", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) }
            } else {
                null
            },
        )
        EmojiSuggestionStrip(if (showSuggestions) suggestions else null)
    }
}

/**
 * フィールド (名前: 値) の編集。プロフィールの表示と同じ見た目の行を、項目ごとに独立した角丸の面にして並べ、
 * 右端の編集ボタンでその行だけを入力欄に替える (開けるのは 1 つだけ。ボタンは完了に替わる)。その右に削除ボタン。
 * 上限 ([ProfileLimits.maxFields]) まで「追加」ボタン (足した行はそのまま入力欄を開く)。行の出入りで高さを滑らかに変える。
 */
@Composable
internal fun ProfileFieldsEditor(
    editor: ProfileEditor,
    limits: ProfileLimits,
    emojis: List<CustomEmoji>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Column(modifier.animateContentSize(motion.defaultSpatialSpec())) {
        Text(
            stringResource(R.string.profile_edit_fields),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(FieldGap)) {
            editor.fields.forEach { field ->
                key(field.key) {
                    FieldEditorRow(
                        field = field,
                        editing = editor.editingFieldKey == field.key,
                        limits = limits,
                        emojis = emojis,
                        enabled = enabled,
                        onEdit = { editor.editField(field) },
                        onDone = editor::finishFieldEdit,
                        onDelete = { editor.removeField(field) },
                    )
                }
            }
        }
        if (editor.fields.size < limits.maxFields) {
            TextButton(
                onClick = editor::addField,
                enabled = enabled,
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(Symbols.Filled.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.profile_edit_add_field), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
    }
}

/**
 * フィールド 1 行。閉じている間は表示と同じ (名前 35% : 値 65%、絵文字は画像)、開くと名前・値の入力欄。
 * 右端に編集 (開いている間は完了) と削除。開いたら名前の欄にフォーカスする。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun FieldEditorRow(
    field: ProfileFieldEditor,
    editing: Boolean,
    limits: ProfileLimits,
    emojis: List<CustomEmoji>,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
) {
    // 開いたら行ごと (名前・値の両方とボタン) キーボードの上に出す。名前の欄が自分で見せる分より後に動かす
    val bringIntoView = remember { BringIntoViewRequester() }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(editing, imeVisible) {
        if (editing) {
            delay(RowBringIntoViewDelayMillis)
            bringIntoView.bringIntoView()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.extraLarge)
            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditSwitch(
            editing = editing,
            modifier = Modifier.weight(1f),
            display = {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    EmojiText(
                        text = field.name.text.toString(),
                        emojis = emojis,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.35f),
                    )
                    Spacer(Modifier.width(12.dp))
                    EmojiText(
                        text = field.value.text.toString(),
                        emojis = emojis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(0.65f),
                    )
                }
            },
            edit = {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Column(Modifier.padding(vertical = 4.dp)) {
                    ProfileTextField(
                        state = field.name,
                        label = stringResource(R.string.profile_edit_field_name),
                        max = limits.maxFieldNameCharacters,
                        count = ::countFieldCharacters,
                        emojis = emojis,
                        enabled = enabled,
                        counterFrom = limits.maxFieldNameCharacters - FieldCounterMargin,
                        focusRequester = focus,
                    )
                    ProfileTextField(
                        state = field.value,
                        label = stringResource(R.string.profile_edit_field_value),
                        max = limits.maxFieldValueCharacters,
                        count = ::countFieldCharacters,
                        emojis = emojis,
                        enabled = enabled,
                        counterFrom = limits.maxFieldValueCharacters - FieldCounterMargin,
                    )
                }
            },
        )
        if (editing) {
            IconButton(onClick = onDone, enabled = enabled) {
                Icon(Symbols.Filled.Check, contentDescription = stringResource(R.string.profile_edit_field_done))
            }
        } else {
            IconButton(onClick = onEdit, enabled = enabled) {
                Icon(Symbols.Outlined.Edit, contentDescription = stringResource(R.string.profile_edit_edit_field))
            }
        }
        IconButton(
            onClick = onDelete,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Symbols.Outlined.Delete, contentDescription = stringResource(R.string.profile_edit_remove_field))
        }
    }
}

private val FieldGap = 8.dp

/** フォーカス・候補の表示・キーボードの変化から、欄を見える所へ動かすまでの待ち。 */
private const val BringIntoViewDelayMillis = 300L

/** フィールドの行を開いてから、行ごと見える所へ動かすまでの待ち (入力欄の分 [BringIntoViewDelayMillis] より後)。 */
private const val RowBringIntoViewDelayMillis = 450L

/** フィールドの文字数を出し始める、上限までの残り。 */
private const val FieldCounterMargin = 20
