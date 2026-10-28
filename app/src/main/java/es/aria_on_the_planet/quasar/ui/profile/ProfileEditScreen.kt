package es.aria_on_the_planet.quasar.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.countDisplayNameCharacters
import es.aria_on_the_planet.quasar.domain.model.countNoteCharacters
import es.aria_on_the_planet.quasar.domain.model.problems
import es.aria_on_the_planet.quasar.ui.component.dialog.ConfirmDialog
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.image.ImageKind
import es.aria_on_the_planet.quasar.ui.component.image.LocalImage
import es.aria_on_the_planet.quasar.ui.component.image.RemoteImage
import es.aria_on_the_planet.quasar.ui.component.user.UserIcon
import es.aria_on_the_planet.quasar.ui.component.user.avatarShape

/**
 * プロフィールの編集画面。プロフィール画面の上に下から重ねる ([ProfileContent])。
 *
 * 上からトップバー (✕ /「プロフィール編集」/ 保存) / ヘッダ画像 + アイコン (タップで Photo Picker、選んだものは送る前に
 * 差し替えて見せる) / 表示名 / 自己紹介 / カスタムフィールド ([ProfileFieldsEditor]) / 保存の失敗。
 * プロフィール画面のようにヘッダ画像をステータスバーの裏まで描かず、普通のトップバーの下に置く (読みやすさのため)。
 * 変更があれば ✕ /「戻る」で破棄の確認。保存の送信中は入力も閉じるのもできない。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileEditScreen(
    profile: Profile,
    edit: ProfileEdit.Editing,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editor = edit.editor
    val enabled = !edit.isSaving
    val canSave by remember(editor, edit.limits) {
        derivedStateOf { editor.toSource().problems(edit.limits).isEmpty() }
    }
    var confirmingDiscard by remember { mutableStateOf(false) }
    val requestClose = { if (editor.hasChanges) confirmingDiscard = true else onCancel() }
    // 変更が無くても「戻る」はここで受ける (後ろのプロフィール画面ごと戻らないように)
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = enabled,
        onBackCompleted = requestClose,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_edit)) },
                navigationIcon = {
                    IconButton(onClick = requestClose, enabled = enabled) {
                        Icon(Symbols.Filled.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    Button(
                        onClick = onSave,
                        enabled = canSave && enabled,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (edit.isSaving) {
                            LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current)
                        } else {
                            Text(stringResource(R.string.profile_edit_save))
                        }
                    }
                },
            )
        },
        // キーボードを含めて避ける (入力欄がキーボードの裏に入らないように)
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ProfileImagesEditor(profile, editor, enabled)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileTextField(
                    state = editor.displayName,
                    label = stringResource(R.string.profile_edit_display_name),
                    max = edit.limits.maxDisplayNameCharacters,
                    count = ::countDisplayNameCharacters,
                    emojis = edit.emojis,
                    enabled = enabled,
                )
                ProfileTextField(
                    state = editor.note,
                    label = stringResource(R.string.profile_edit_note),
                    max = edit.limits.maxNoteCharacters,
                    count = ::countNoteCharacters,
                    emojis = edit.emojis,
                    enabled = enabled,
                    singleLine = false,
                )
                ProfileFieldsEditor(
                    editor = editor,
                    limits = edit.limits,
                    emojis = edit.emojis,
                    enabled = enabled,
                )
                AnimatedVisibility(visible = edit.error != null) {
                    Text(
                        text = edit.error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmingDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.profile_edit_discard_title),
            message = stringResource(R.string.profile_edit_discard_message),
            confirmLabel = stringResource(R.string.composer_discard),
            dismissLabel = stringResource(R.string.profile_edit_keep_editing),
            onConfirm = {
                confirmingDiscard = false
                onCancel()
            },
            onDismiss = { confirmingDiscard = false },
            destructive = true,
        )
    }
}

/**
 * ヘッダ画像 (Mastodon と同じ 3:1) と、下端に半分重なるアイコン。プロフィールの表示と同じ並びで、どちらもタップで
 * Photo Picker (画像のみ)。選んだ画像は送る前に差し替えて見せる。
 */
@Composable
private fun ProfileImagesEditor(profile: Profile, editor: ProfileEditor, enabled: Boolean) {
    val user = profile.user
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editor.avatar = uri
    }
    val pickHeader = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editor.header = uri
    }
    val imageRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    // アイコンがヘッダから下へはみ出す分 (縁取りを含めた半分) をあける
    Box(Modifier.padding(bottom = AvatarSize / 2 + AvatarRing)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(HeaderAspectRatio)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            val newHeader = editor.header
            val headerUrl = profile.headerUrl
            if (newHeader != null) {
                LocalImage(newHeader.toString(), null, Modifier.fillMaxSize(), ContentScale.Crop)
            } else if (headerUrl != null) {
                RemoteImage(
                    url = headerUrl,
                    staticUrl = profile.headerStaticUrl,
                    kind = ImageKind.Other,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ImageEditOverlay(
                contentDescription = stringResource(R.string.profile_edit_change_header),
                onClick = { pickHeader.launch(imageRequest) },
                enabled = enabled,
                modifier = Modifier.matchParentSize(),
            )
        }
        // ヘッダに半分重なるアイコン。背景色のリングで縁取る (プロフィールの表示と同じ)
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp)
                .offset(y = AvatarSize / 2 + AvatarRing)
                .background(MaterialTheme.colorScheme.surface, avatarShape(ring = AvatarRing))
                .padding(AvatarRing),
        ) {
            val newAvatar = editor.avatar
            if (newAvatar != null) {
                LocalImage(
                    uri = newAvatar.toString(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AvatarSize)
                        .clip(avatarShape()),
                )
            } else {
                UserIcon(user = user, size = AvatarSize)
            }
            ImageEditOverlay(
                contentDescription = stringResource(R.string.profile_edit_change_avatar),
                onClick = { pickAvatar.launch(imageRequest) },
                enabled = enabled,
                modifier = Modifier
                    .matchParentSize()
                    .clip(avatarShape()),
            )
        }
    }
}

/** Mastodon のヘッダ画像の縦横比 (1500x500)。 */
private const val HeaderAspectRatio = 3f
private val AvatarSize = 88.dp
private val AvatarRing = 4.dp
