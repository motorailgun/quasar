package es.aria_on_the_planet.quasar.ui.settings

import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.settings.AccountSettings
import es.aria_on_the_planet.quasar.domain.model.settings.AvatarShape
import es.aria_on_the_planet.quasar.domain.model.settings.ColumnLayout
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.domain.model.settings.DefaultVisibilityChoices
import es.aria_on_the_planet.quasar.domain.model.settings.DeviceSettings
import es.aria_on_the_planet.quasar.domain.model.settings.DisplaySettings
import es.aria_on_the_planet.quasar.domain.model.settings.NotificationApi
import es.aria_on_the_planet.quasar.domain.model.settings.PostMenuItem
import es.aria_on_the_planet.quasar.domain.model.settings.StreamingSettings
import es.aria_on_the_planet.quasar.ui.component.post.descriptionRes
import es.aria_on_the_planet.quasar.ui.component.post.labelRes
import es.aria_on_the_planet.quasar.ui.component.dialog.ConfirmDialog
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsActionItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsChoiceItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsFooter
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsGroup
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsNavigationItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsScaffold
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsSliderItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsSplitSwitchItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsSwitchItem
import es.aria_on_the_planet.quasar.ui.navigation.Route
import java.util.Locale
import kotlin.math.roundToInt

/** 端末共通の設定を変える (`DeviceSettingsStore.launchUpdate`)。 */
typealias UpdateDeviceSettings = ((DeviceSettings) -> DeviceSettings) -> Unit

/** 使っているアカウントの設定を変える (`AccountContext.settings` の `launchUpdate`)。 */
typealias UpdateAccountSettings = ((AccountSettings) -> AccountSettings) -> Unit

/**
 * トップ: 「アカウントの設定」(使っているアカウントのもの) と「アプリの設定」(端末共通) の各ページへの入口 + ログインし直す・
 * ログアウト。
 * ログアウトは設定に関わらず必ず確認する。
 *
 * @param account 使っているアカウント (`@acct@host`、まだ分からなければサーバのホスト)。見出しとログアウトの補足に出す。
 *   `null` (ログアウト中) なら「アカウントの設定」とログアウトを出さない。
 * @param translationAvailable 翻訳の入ったビルドか (翻訳のページを出す)。
 */
@Composable
fun SettingsTopPage(
    account: String?,
    translationAvailable: Boolean,
    onNavigate: (Route) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onRelogin: () -> Unit = {},
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    SettingsScaffold(title = stringResource(R.string.settings), onBack = onBack) {
        if (account != null) {
            SettingsGroup(title = stringResource(R.string.settings_account_section, account)) {
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
            }
        }
        SettingsGroup(title = stringResource(R.string.settings_app_section)) {
            SettingsNavigationItem(
                title = stringResource(R.string.settings_display),
                summary = stringResource(R.string.settings_display_summary),
                icon = Symbols.Outlined.Palette,
                onClick = { onNavigate(Route.SettingsDisplay) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.settings_post_menu),
                summary = stringResource(R.string.settings_post_menu_summary),
                icon = Symbols.Outlined.MoreVert,
                onClick = { onNavigate(Route.SettingsPostMenu) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.settings_confirmations),
                summary = stringResource(R.string.settings_confirmations_summary),
                icon = Symbols.Outlined.TaskAlt,
                onClick = { onNavigate(Route.SettingsConfirmations) },
            )
            // 今の実験的機能は翻訳だけなので、翻訳の無いビルドではページごと出さない
            if (translationAvailable) {
                SettingsNavigationItem(
                    title = stringResource(R.string.settings_experimental),
                    summary = stringResource(R.string.settings_experimental_summary),
                    icon = Symbols.Outlined.Science,
                    onClick = { onNavigate(Route.SettingsExperimental) },
                )
            }
        }
        SettingsGroup(title = null) {
            SettingsNavigationItem(
                title = stringResource(R.string.settings_licenses),
                summary = stringResource(R.string.settings_licenses_summary),
                icon = Symbols.Outlined.License,
                onClick = { onNavigate(Route.SettingsLicenses) },
            )
        }
        if (account != null) {
            SettingsGroup(title = null) {
                // 設定・キャッシュはそのままでトークンだけ取り直す (無効になったとき等。下書きは依存の作り直しで消える)。除くのはログアウトだけ
                SettingsActionItem(
                    title = stringResource(R.string.relogin),
                    summary = stringResource(R.string.relogin_summary),
                    icon = Symbols.Outlined.Refresh,
                    onClick = onRelogin,
                )
                SettingsActionItem(
                    title = stringResource(R.string.logout),
                    summary = account,
                    icon = Symbols.Outlined.Logout,
                    destructive = true,
                    onClick = { confirmLogout = true },
                )
            }
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
private val ColumnLayoutChoices = ColumnLayout.entries

/** 表示: UI・文字の倍率・カラムの並べ方・長文の折りたたみ・アイコンの形・ぼかし・アニメーション。 */
@Composable
fun DisplaySettingsPage(
    display: DisplaySettings,
    update: UpdateDeviceSettings,
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
        SettingsGroup(title = stringResource(R.string.settings_layout)) {
            SettingsChoiceItem(
                title = stringResource(R.string.settings_column_layout),
                options = ColumnLayoutChoices,
                selected = display.columnLayout,
                label = { layout ->
                    stringResource(
                        when (layout) {
                            ColumnLayout.Auto -> R.string.settings_column_layout_auto
                            ColumnLayout.Single -> R.string.settings_column_layout_single
                            ColumnLayout.Double -> R.string.settings_column_layout_double
                        },
                    )
                },
                onSelect = { updateDisplay { copy(columnLayout = it) } },
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
            SettingsSwitchItem(
                title = stringResource(R.string.settings_menu_blur),
                summary = stringResource(R.string.settings_menu_blur_summary),
                checked = display.menuBlur,
                onCheckedChange = { checked -> updateDisplay { copy(menuBlur = checked) } },
            )
            SettingsSliderItem(
                title = stringResource(R.string.settings_blur_radius),
                value = display.surfaceBlurRadiusDp,
                valueRange = 0f..DisplaySettings.MaxSurfaceBlurRadiusDp,
                steps = DisplaySettings.MaxSurfaceBlurRadiusDp.toInt() - 1,
                valueLabel = { stringResource(R.string.settings_blur_radius_value, it.roundToInt()) },
                onValueChange = { value -> updateDisplay { copy(surfaceBlurRadiusDp = value.roundToInt().toFloat()) } },
                // 半径・色味はバーとメニューで共通
                enabled = display.surfaceBlur || display.menuBlur,
            )
            SettingsSliderItem(
                title = stringResource(R.string.settings_blur_tint),
                value = display.surfaceBlurTintAlpha,
                valueRange = 0f..1f,
                // 5% 刻み
                steps = 19,
                valueLabel = { stringResource(R.string.settings_percent, (it * 100).roundToInt()) },
                onValueChange = { value -> updateDisplay { copy(surfaceBlurTintAlpha = (value * 20).roundToInt() / 20f) } },
                // 半径・色味はバーとメニューで共通
                enabled = display.surfaceBlur || display.menuBlur,
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
fun AnimationSettingsPage(display: DisplaySettings, update: UpdateDeviceSettings, onBack: () -> Unit) {
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

/** タイムライン (アカウントごと): カラムごとの自動更新・通知の取得方法。 */
@Composable
fun TimelineSettingsPage(settings: AccountSettings, update: UpdateAccountSettings, onBack: () -> Unit) {
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

/**
 * ポストのメニュー (︙) に出す項目。共通 (見出しなし。ページの題名で分かる) / 自分のポスト / 他人のポストに分けて、項目ごとにオン / オフ。
 */
@Composable
fun PostMenuSettingsPage(hidden: Set<PostMenuItem>, update: UpdateDeviceSettings, onBack: () -> Unit) {
    @Composable
    fun Item(item: PostMenuItem, title: String, icon: ImageVector) = SettingsSwitchItem(
        title = title,
        summary = null,
        icon = icon,
        checked = item !in hidden,
        onCheckedChange = { checked ->
            update { it.copy(hiddenPostMenuItems = if (checked) it.hiddenPostMenuItems - item else it.hiddenPostMenuItems + item) }
        },
    )
    SettingsScaffold(title = stringResource(R.string.settings_post_menu), onBack = onBack) {
        SettingsGroup(title = null) {
            Item(PostMenuItem.OpenInBrowser, stringResource(R.string.menu_open_in_browser), Symbols.Outlined.OpenInBrowser)
            Item(PostMenuItem.OpenOnServer, stringResource(R.string.settings_post_menu_open_on_server), Symbols.Outlined.Language)
            Item(PostMenuItem.CopyText, stringResource(R.string.post_copy_text), Symbols.Outlined.ContentCopy)
        }
        SettingsGroup(title = stringResource(R.string.settings_post_menu_own)) {
            Item(PostMenuItem.Edit, stringResource(R.string.post_edit), Symbols.Outlined.Edit)
            Item(PostMenuItem.Delete, stringResource(R.string.post_delete), Symbols.Outlined.Delete)
        }
        SettingsGroup(title = stringResource(R.string.settings_post_menu_others)) {
            Item(PostMenuItem.Mute, stringResource(R.string.account_mute), Symbols.Outlined.VolumeOff)
            Item(PostMenuItem.Block, stringResource(R.string.account_block), Symbols.Outlined.Block)
            Item(PostMenuItem.HideBoosts, stringResource(R.string.account_hide_boosts), Symbols.Outlined.Repeat)
        }
    }
}

/** 投稿 (アカウントごと): 新しい投稿の公開範囲・添付の画像の扱い。 */
@Composable
fun PostingSettingsPage(settings: AccountSettings, update: UpdateAccountSettings, onBack: () -> Unit) {
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

/**
 * 確認: 投稿・ブースト・お気に入り・ブックマーク (とそれぞれの取り消し)・自分のポストの編集・削除・フォローリクエストの拒否の
 * 前に確認するか。
 */
@Composable
fun ConfirmationSettingsPage(confirmations: Set<ConfirmTarget>, update: UpdateDeviceSettings, onBack: () -> Unit) {
    val summary = stringResource(R.string.settings_confirm_summary)
    @Composable
    fun Item(title: Int, target: ConfirmTarget) =
        ConfirmSwitch(stringResource(title), summary, target, confirmations, update)
    SettingsScaffold(title = stringResource(R.string.settings_confirmations), onBack = onBack) {
        SettingsGroup(title = stringResource(R.string.settings_posting)) {
            Item(R.string.settings_confirm_post, ConfirmTarget.Post)
        }
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

/** 実験的機能: 試作の機能 (今は端末内の翻訳だけ) の入口 + 末尾の注意書き。 */
@Composable
fun ExperimentalSettingsPage(onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.settings_experimental), onBack = onBack) {
        SettingsGroup(title = null) {
            SettingsNavigationItem(
                title = stringResource(R.string.settings_translation),
                summary = stringResource(R.string.settings_translation_summary),
                icon = Symbols.Outlined.Translate,
                onClick = { onNavigate(Route.SettingsTranslation) },
            )
        }
        SettingsFooter(stringResource(R.string.settings_experimental_note))
    }
}

@Composable
private fun ConfirmSwitch(
    title: String,
    summary: String?,
    target: ConfirmTarget,
    confirmations: Set<ConfirmTarget>,
    update: UpdateDeviceSettings,
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
