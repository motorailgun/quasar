package es.aria_on_the_planet.quasar.ui.settings

import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.settings.AppSettings
import es.aria_on_the_planet.quasar.domain.model.settings.AvatarShape
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.domain.model.settings.DefaultVisibilityChoices
import es.aria_on_the_planet.quasar.domain.model.settings.DisplaySettings
import es.aria_on_the_planet.quasar.domain.model.settings.NotificationApi
import es.aria_on_the_planet.quasar.domain.model.settings.StreamingSettings
import es.aria_on_the_planet.quasar.ui.component.post.descriptionRes
import es.aria_on_the_planet.quasar.ui.component.post.labelRes
import es.aria_on_the_planet.quasar.ui.component.dialog.ConfirmDialog
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsActionItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsChoiceItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsGroup
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsNavigationItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsScaffold
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsSliderItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsSplitSwitchItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsSwitchItem
import es.aria_on_the_planet.quasar.ui.navigation.Route
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import kotlin.math.roundToInt

/** 設定を変える (`SettingsStore.launchUpdate`)。 */
typealias UpdateSettings = ((AppSettings) -> AppSettings) -> Unit

/**
 * トップ: 各ページへの入口 + ログアウト。ログアウトは設定に関わらず必ず確認する。
 *
 * @param serverUrl ログイン中のサーバ (ログアウトの項目の補足に出す)。
 */
@Composable
fun SettingsTopPage(serverUrl: String?, onNavigate: (Route) -> Unit, onLogout: () -> Unit, onBack: () -> Unit) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    SettingsScaffold(title = stringResource(R.string.settings), onBack = onBack) {
        SettingsGroup(title = null) {
            SettingsNavigationItem(
                title = stringResource(R.string.settings_display),
                summary = stringResource(R.string.settings_display_summary),
                icon = Symbols.Outlined.Palette,
                onClick = { onNavigate(Route.SettingsDisplay) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.settings_timelines),
                summary = stringResource(R.string.settings_timelines_summary),
                icon = Symbols.Outlined.Sync,
                onClick = { onNavigate(Route.SettingsTimelines) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.settings_posting),
                summary = stringResource(R.string.settings_posting_summary),
                icon = Symbols.Outlined.Edit,
                onClick = { onNavigate(Route.SettingsPosting) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.settings_confirmations),
                summary = stringResource(R.string.settings_confirmations_summary),
                icon = Symbols.Outlined.TaskAlt,
                onClick = { onNavigate(Route.SettingsConfirmations) },
            )
        }
        SettingsGroup(title = null) {
            SettingsActionItem(
                title = stringResource(R.string.logout),
                summary = serverUrl?.toHttpUrlOrNull()?.host,
                icon = Symbols.Outlined.Logout,
                destructive = true,
                onClick = { confirmLogout = true },
            )
        }
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = stringResource(R.string.logout_confirm_title),
            message = stringResource(R.string.logout_confirm_message),
            confirmLabel = stringResource(R.string.logout),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

private val LongPostMaxLinesChoices = listOf(null, 4, 6, 8, 12, 16)
private val MaxFpsChoices = listOf(15, 30, 60, null)
private val AvatarShapeChoices = AvatarShape.entries

/** 表示: UI・文字の倍率・長文の折りたたみ・アイコンの形・ぼかし・アニメーション。 */
@Composable
fun DisplaySettingsPage(
    display: DisplaySettings,
    update: UpdateSettings,
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    fun updateDisplay(transform: DisplaySettings.() -> DisplaySettings) = update { it.copy(display = it.display.transform()) }
    SettingsScaffold(title = stringResource(R.string.settings_display), onBack = onBack) {
        SettingsGroup(title = stringResource(R.string.settings_scale)) {
            ScaleSliderItem(
                title = stringResource(R.string.settings_ui_scale),
                value = display.uiScale,
                onValueChange = { value -> updateDisplay { copy(uiScale = value) } },
            )
            ScaleSliderItem(
                title = stringResource(R.string.settings_font_scale),
                value = display.fontScale,
                onValueChange = { value -> updateDisplay { copy(fontScale = value) } },
            )
        }
        SettingsGroup(title = stringResource(R.string.settings_posts)) {
            SettingsChoiceItem(
                title = stringResource(R.string.settings_long_post_max_lines),
                options = LongPostMaxLinesChoices,
                selected = display.longPostMaxLines,
                label = { lines ->
                    if (lines == null) stringResource(R.string.settings_off)
                    else pluralStringResource(R.plurals.settings_lines, lines, lines)
                },
                onSelect = { updateDisplay { copy(longPostMaxLines = it) } },
            )
        }
        SettingsGroup(title = stringResource(R.string.settings_avatars)) {
            SettingsChoiceItem(
                title = stringResource(R.string.settings_avatar_shape),
                options = AvatarShapeChoices,
                selected = display.avatarShape,
                label = { shape ->
                    stringResource(
                        when (shape) {
                            AvatarShape.Circle -> R.string.settings_avatar_shape_circle
                            AvatarShape.RoundedSquare -> R.string.settings_avatar_shape_rounded_square
                        },
                    )
                },
                onSelect = { updateDisplay { copy(avatarShape = it) } },
            )
        }
        SettingsGroup(title = stringResource(R.string.settings_blur)) {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_blur_enabled),
                summary = stringResource(R.string.settings_blur_enabled_summary),
                checked = display.surfaceBlur,
                onCheckedChange = { checked -> updateDisplay { copy(surfaceBlur = checked) } },
            )
            SettingsSliderItem(
                title = stringResource(R.string.settings_blur_radius),
                value = display.surfaceBlurRadiusDp,
                valueRange = 0f..DisplaySettings.MaxSurfaceBlurRadiusDp,
                steps = DisplaySettings.MaxSurfaceBlurRadiusDp.toInt() - 1,
                valueLabel = { stringResource(R.string.settings_blur_radius_value, it.roundToInt()) },
                onValueChange = { value -> updateDisplay { copy(surfaceBlurRadiusDp = value.roundToInt().toFloat()) } },
                enabled = display.surfaceBlur,
            )
            SettingsSliderItem(
                title = stringResource(R.string.settings_blur_tint),
                value = display.surfaceBlurTintAlpha,
                valueRange = 0f..1f,
                // 5% 刻み
                steps = 19,
                valueLabel = { stringResource(R.string.settings_percent, (it * 100).roundToInt()) },
                onValueChange = { value -> updateDisplay { copy(surfaceBlurTintAlpha = (value * 20).roundToInt() / 20f) } },
                enabled = display.surfaceBlur,
            )
        }
        SettingsGroup(title = stringResource(R.string.settings_animation)) {
            SettingsSplitSwitchItem(
                title = stringResource(R.string.settings_animate_images),
                summary = stringResource(
                    if (display.animateImages) R.string.settings_animate_images_summary else R.string.settings_animate_images_off,
                ),
                checked = display.animateImages,
                onCheckedChange = { checked -> updateDisplay { copy(animateImages = checked) } },
                onClick = { onNavigate(Route.SettingsAnimation) },
            )
            SettingsChoiceItem(
                title = stringResource(R.string.settings_animation_max_fps),
                options = MaxFpsChoices,
                selected = display.emojiAvatarAnimationMaxFps,
                label = { fps ->
                    if (fps == null) stringResource(R.string.settings_no_limit) else stringResource(R.string.settings_fps, fps)
                },
                onSelect = { updateDisplay { copy(emojiAvatarAnimationMaxFps = it) } },
                enabled = display.animateImages,
            )
        }
    }
}

/** UI・文字の倍率 ([DisplaySettings.ScaleRange] を [DisplaySettings.ScaleStep] 刻み)。 */
@Composable
private fun ScaleSliderItem(title: String, value: Float, onValueChange: (Float) -> Unit) {
    val range = DisplaySettings.ScaleRange
    val stepsPerUnit = 1 / DisplaySettings.ScaleStep
    SettingsSliderItem(
        title = title,
        value = value,
        valueRange = range,
        steps = ((range.endInclusive - range.start) * stepsPerUnit).roundToInt() - 1,
        valueLabel = { stringResource(R.string.settings_scale_value, formatScale(it)) },
        onValueChange = { onValueChange((it * stepsPerUnit).roundToInt() / stepsPerUnit) },
    )
}

/** 倍率の表記 (「x1.0」「x1.05」「x1.025」)。末尾の 0 は落とすが小数点以下は 1 桁残す。 */
private fun formatScale(scale: Float): String =
    "%.3f".format(Locale.ROOT, scale).trimEnd('0').let { if (it.endsWith('.')) it + "0" else it }

/** アニメーション: 画像のアニメーション全体と、種類ごとのオン / オフ。 */
@Composable
fun AnimationSettingsPage(display: DisplaySettings, update: UpdateSettings, onBack: () -> Unit) {
    fun updateDisplay(transform: DisplaySettings.() -> DisplaySettings) = update { it.copy(display = it.display.transform()) }
    SettingsScaffold(title = stringResource(R.string.settings_animation), onBack = onBack) {
        SettingsGroup(title = null) {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_animate_images),
                summary = stringResource(R.string.settings_animate_images_summary),
                checked = display.animateImages,
                onCheckedChange = { checked -> updateDisplay { copy(animateImages = checked) } },
            )
        }
        // 全体がオフなら個別の設定は効かないので押せなくする (値は残す)
        SettingsGroup(title = stringResource(R.string.settings_animate_images)) {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_animate_post_emojis),
                summary = stringResource(R.string.settings_animate_post_emojis_summary),
                checked = display.animatePostEmojis,
                onCheckedChange = { checked -> updateDisplay { copy(animatePostEmojis = checked) } },
                enabled = display.animateImages,
            )
            SettingsSwitchItem(
                title = stringResource(R.string.settings_animate_other_emojis),
                summary = stringResource(R.string.settings_animate_other_emojis_summary),
                checked = display.animateOtherEmojis,
                onCheckedChange = { checked -> updateDisplay { copy(animateOtherEmojis = checked) } },
                enabled = display.animateImages,
            )
            SettingsSwitchItem(
                title = stringResource(R.string.settings_animate_avatars),
                summary = null,
                checked = display.animateAvatars,
                onCheckedChange = { checked -> updateDisplay { copy(animateAvatars = checked) } },
                enabled = display.animateImages,
            )
        }
    }
}

/** タイムライン: カラムごとの自動更新・通知の取得方法。 */
@Composable
fun TimelineSettingsPage(settings: AppSettings, update: UpdateSettings, onBack: () -> Unit) {
    val streaming = settings.streaming
    fun updateStreaming(transform: StreamingSettings.() -> StreamingSettings) =
        update { it.copy(streaming = it.streaming.transform()) }
    val streamSummary = stringResource(R.string.settings_stream_summary)
    SettingsScaffold(title = stringResource(R.string.settings_timelines), onBack = onBack) {
        SettingsGroup(title = stringResource(R.string.settings_auto_update)) {
            SettingsSwitchItem(
                title = stringResource(R.string.timeline_home),
                summary = streamSummary,
                checked = streaming.home,
                onCheckedChange = { checked -> updateStreaming { copy(home = checked) } },
            )
            SettingsSwitchItem(
                title = stringResource(R.string.column_notifications),
                summary = streamSummary,
                checked = streaming.notifications,
                onCheckedChange = { checked -> updateStreaming { copy(notifications = checked) } },
            )
            SettingsSwitchItem(
                title = stringResource(R.string.timeline_local),
                summary = streamSummary,
                checked = streaming.local,
                onCheckedChange = { checked -> updateStreaming { copy(local = checked) } },
            )
            SettingsSwitchItem(
                title = stringResource(R.string.timeline_global),
                summary = stringResource(R.string.settings_stream_global_summary),
                checked = streaming.global,
                onCheckedChange = { checked -> updateStreaming { copy(global = checked) } },
            )
        }
        SettingsGroup(title = stringResource(R.string.settings_notifications)) {
            SettingsChoiceItem(
                title = stringResource(R.string.settings_notification_api),
                options = NotificationApi.entries,
                selected = settings.notificationApi,
                label = { api ->
                    stringResource(
                        when (api) {
                            NotificationApi.Grouped -> R.string.settings_notification_api_grouped
                            NotificationApi.Ungrouped -> R.string.settings_notification_api_ungrouped
                        },
                    )
                },
                description = { api ->
                    stringResource(
                        when (api) {
                            NotificationApi.Grouped -> R.string.settings_notification_api_grouped_description
                            NotificationApi.Ungrouped -> R.string.settings_notification_api_ungrouped_description
                        },
                    )
                },
                onSelect = { api -> update { it.copy(notificationApi = api) } },
            )
        }
    }
}

/** 投稿: 新しい投稿の公開範囲・投稿前の確認。 */
@Composable
fun PostingSettingsPage(settings: AppSettings, update: UpdateSettings, onBack: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.settings_posting), onBack = onBack) {
        SettingsGroup(title = null) {
            SettingsChoiceItem<PostVisibility?>(
                title = stringResource(R.string.settings_default_visibility),
                options = listOf(null) + DefaultVisibilityChoices,
                selected = settings.defaultVisibility,
                label = { visibility ->
                    stringResource(visibility?.labelRes ?: R.string.settings_visibility_account_default)
                },
                description = { visibility ->
                    stringResource(visibility?.descriptionRes ?: R.string.settings_visibility_account_default_description)
                },
                onSelect = { visibility -> update { it.copy(defaultVisibility = visibility) } },
            )
            ConfirmSwitch(
                title = stringResource(R.string.settings_confirm_post),
                summary = null,
                target = ConfirmTarget.Post,
                confirmations = settings.confirmations,
                update = update,
            )
            SettingsSwitchItem(
                title = stringResource(R.string.settings_compress_oversized_images),
                summary = stringResource(R.string.settings_compress_oversized_images_summary),
                checked = settings.compressOversizedImages,
                onCheckedChange = { checked -> update { it.copy(compressOversizedImages = checked) } },
            )
            SettingsSwitchItem(
                title = stringResource(R.string.settings_strip_image_metadata),
                summary = stringResource(R.string.settings_strip_image_metadata_summary),
                checked = settings.stripImageMetadata,
                onCheckedChange = { checked -> update { it.copy(stripImageMetadata = checked) } },
            )
        }
    }
}

/** 確認: ブースト・お気に入り・ブックマーク (とそれぞれの取り消し)・自分のポストの編集・削除・フォローリクエストの拒否の前に確認するか。 */
@Composable
fun ConfirmationSettingsPage(confirmations: Set<ConfirmTarget>, update: UpdateSettings, onBack: () -> Unit) {
    val summary = stringResource(R.string.settings_confirm_summary)
    @Composable
    fun Item(title: Int, target: ConfirmTarget) =
        ConfirmSwitch(stringResource(title), summary, target, confirmations, update)
    SettingsScaffold(title = stringResource(R.string.settings_confirmations), onBack = onBack) {
        SettingsGroup(title = stringResource(R.string.settings_boost)) {
            Item(R.string.settings_confirm_boost, ConfirmTarget.Boost)
            Item(R.string.settings_confirm_unboost, ConfirmTarget.Unboost)
        }
        SettingsGroup(title = stringResource(R.string.settings_favourite)) {
            Item(R.string.settings_confirm_favourite, ConfirmTarget.Favourite)
            Item(R.string.settings_confirm_unfavourite, ConfirmTarget.Unfavourite)
        }
        SettingsGroup(title = stringResource(R.string.settings_bookmark)) {
            Item(R.string.settings_confirm_bookmark, ConfirmTarget.Bookmark)
            Item(R.string.settings_confirm_unbookmark, ConfirmTarget.Unbookmark)
        }
        SettingsGroup(title = stringResource(R.string.settings_my_posts)) {
            Item(R.string.settings_confirm_edit_post, ConfirmTarget.EditPost)
            Item(R.string.settings_confirm_delete_post, ConfirmTarget.DeletePost)
        }
        SettingsGroup(title = stringResource(R.string.settings_follow_requests)) {
            Item(R.string.settings_confirm_reject_follow_request, ConfirmTarget.RejectFollowRequest)
        }
    }
}

@Composable
private fun ConfirmSwitch(
    title: String,
    summary: String?,
    target: ConfirmTarget,
    confirmations: Set<ConfirmTarget>,
    update: UpdateSettings,
) {
    SettingsSwitchItem(
        title = title,
        summary = summary,
        checked = target in confirmations,
        onCheckedChange = { checked ->
            update { it.copy(confirmations = if (checked) it.confirmations + target else it.confirmations - target) }
        },
    )
}
