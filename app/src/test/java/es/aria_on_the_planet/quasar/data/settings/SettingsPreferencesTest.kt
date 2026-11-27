package es.aria_on_the_planet.quasar.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.settings.AccountSettings
import es.aria_on_the_planet.quasar.domain.model.settings.AvatarShape
import es.aria_on_the_planet.quasar.domain.model.settings.ColumnLayout
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.domain.model.settings.DefaultConfirmations
import es.aria_on_the_planet.quasar.domain.model.settings.DeviceSettings
import es.aria_on_the_planet.quasar.domain.model.settings.DisplaySettings
import es.aria_on_the_planet.quasar.domain.model.settings.NotificationApi
import es.aria_on_the_planet.quasar.domain.model.settings.PostMenuItem
import es.aria_on_the_planet.quasar.domain.model.settings.PredictiveBackSettings
import es.aria_on_the_planet.quasar.domain.model.settings.StreamingSettings
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationMode
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPreferencesTest {
    /** 全項目を既定値から変えた設定 (端末共通)。 */
    private val changed = DeviceSettings(
        display = DisplaySettings(
            longPostMaxLines = null,
            surfaceBlur = false,
            menuBlur = false,
            surfaceBlurRadiusDp = 10f,
            surfaceBlurTintAlpha = 0.5f,
            animateImages = false,
            animatePostEmojis = false,
            animateOtherEmojis = false,
            animateAvatars = false,
            emojiAvatarAnimationMaxFps = null,
            uiScale = 1.25f,
            fontScale = 0.9f,
            avatarShape = AvatarShape.RoundedSquare,
            columnLayout = ColumnLayout.Double,
        ),
        confirmations = setOf(ConfirmTarget.Post, ConfirmTarget.Unboost),
        hiddenPostMenuItems = setOf(PostMenuItem.OpenInBrowser, PostMenuItem.HideBoosts),
        predictiveBack = PredictiveBackSettings(maxDragFraction = 0.5f, dragLogSteepness = 5f, transitionMillis = 200),
        translation = TranslationSettings(
            enabled = true,
            defaultMode = TranslationMode.Hidden,
            languageModes = mapOf("es" to TranslationMode.Automatic, "zh-hans" to TranslationMode.Offer),
            targetLanguage = "en",
        ),
    )

    /** 全項目を既定値から変えた設定 (アカウントごと)。 */
    private val changedAccount = AccountSettings(
        notificationApi = NotificationApi.Ungrouped,
        streaming = StreamingSettings(home = false, notifications = false, local = false, global = true),
        defaultVisibility = PostVisibility.Private,
        defaultLanguage = "ja",
        compressOversizedImages = true,
        stripImageMetadata = true,
        mentionSuggestionAvatars = false,
    )

    private fun written(before: DeviceSettings, after: DeviceSettings) =
        mutablePreferencesOf().also { DeviceSettingsSchema.writeChanges(it, before, after) }

    private fun written(before: AccountSettings, after: AccountSettings) =
        mutablePreferencesOf().also { AccountSettingsSchema.writeChanges(it, before, after) }

    private fun Preferences.device() = DeviceSettingsSchema.read(this)

    private fun Preferences.account() = AccountSettingsSchema.read(this)

    private fun Preferences.keyNames() = asMap().keys.map { it.name }.toSet()

    @Test
    fun emptyPreferencesAreDefaults() {
        assertEquals(DeviceSettings(), emptyPreferences().device())
        assertEquals(AccountSettings(), emptyPreferences().account())
    }

    @Test
    fun everyFieldRoundTrips() {
        assertEquals(changed, written(DeviceSettings(), changed).device())
        assertEquals(changedAccount, written(AccountSettings(), changedAccount).account())
        // 既定値に戻した (null を含む) 値も読み戻せる
        val back = mutablePreferencesOf().also {
            DeviceSettingsSchema.writeChanges(it, DeviceSettings(), changed)
            DeviceSettingsSchema.writeChanges(it, changed, DeviceSettings())
        }
        assertEquals(DeviceSettings(), back.device())
        val accountBack = mutablePreferencesOf().also {
            AccountSettingsSchema.writeChanges(it, AccountSettings(), changedAccount)
            AccountSettingsSchema.writeChanges(it, changedAccount, AccountSettings())
        }
        assertEquals(AccountSettings(), accountBack.account())
    }

    @Test
    fun onlyChangedFieldsAreWritten() {
        val prefs = written(AccountSettings(), AccountSettings(streaming = StreamingSettings(global = true)))
        assertEquals(setOf("stream_global"), prefs.keyNames())
        assertTrue(written(AccountSettings(), AccountSettings()).asMap().isEmpty())
        assertTrue(written(DeviceSettings(), DeviceSettings()).asMap().isEmpty())
    }

    @Test
    fun deviceAndAccountKeysAreSeparateAndUnchanged() {
        val device = written(DeviceSettings(), changed).keyNames()
        val account = written(AccountSettings(), changedAccount).keyNames()
        assertTrue((device intersect account).isEmpty())
        // 分ける前と同じ名前 (分ける前のファイルを端末共通のファイルとしてそのまま読み、アカウントごとの分を移す)
        assertEquals(
            setOf(
                "notification_api", "stream_home", "stream_notifications", "stream_local", "stream_global",
                "default_visibility", "default_language", "compress_oversized_images", "strip_image_metadata",
                // ここから下は分けた後に足したもの
                "mention_suggestion_avatars",
            ),
            account,
        )
    }

    @Test
    fun legacyAccountSettingsMoveToTheAccountFile() {
        // 分ける前のファイル: 端末共通とアカウントごとの項目が混ざっている
        val legacy = mutablePreferencesOf().also {
            DeviceSettingsSchema.writeChanges(it, DeviceSettings(), changed)
            AccountSettingsSchema.writeChanges(it, AccountSettings(), changedAccount)
        }
        assertTrue(legacy.hasAccountSettings())
        // 端末共通の設定としてはそのまま読める (アカウントごとの項目は無視される)
        assertEquals(changed, legacy.device())

        val account = emptyPreferences().withLegacyAccountSettings(legacy)
        assertEquals(changedAccount, account.account())
        // 端末共通の項目は写さない
        assertEquals(DeviceSettings(), account.device())

        val device = legacy.withoutAccountSettings()
        assertFalse(device.hasAccountSettings())
        assertEquals(changed, device.device())
        assertEquals(AccountSettings(), device.account())
    }

    @Test
    fun legacyMigrationCanBeRepeated() {
        val legacy = written(AccountSettings(), AccountSettings(notificationApi = NotificationApi.Ungrouped, stripImageMetadata = true))
        // 写した後、元から消す前に終わり、その間に変えた値はやり直しで戻さない
        val first = emptyPreferences().withLegacyAccountSettings(legacy)
        val changedAfter = first.toMutablePreferences().also {
            AccountSettingsSchema.writeChanges(it, first.account(), first.account().copy(stripImageMetadata = false))
        }
        val again = changedAfter.withLegacyAccountSettings(legacy)
        assertEquals(NotificationApi.Ungrouped, again.account().notificationApi)
        assertEquals(false, again.account().stripImageMetadata)
        // 移すものが無ければ何も変えない
        assertFalse(emptyPreferences().hasAccountSettings())
        assertEquals(first, first.withLegacyAccountSettings(emptyPreferences()))
    }

    @Test
    fun nullValuesAreDistinguishedFromMissingKeys() {
        // 既定値が null でない項目を null (無効・上限なし) にしたら、キーが無いとき (既定値) と区別して読める
        val settings = written(
            DeviceSettings(),
            DeviceSettings(display = DisplaySettings(longPostMaxLines = null, emojiAvatarAnimationMaxFps = null)),
        ).device()
        assertEquals(null, settings.display.longPostMaxLines)
        assertEquals(null, settings.display.emojiAvatarAnimationMaxFps)
        // 既定値が null の項目 (アカウントの既定) を明示的に選び直した場合も null
        val visibility = written(AccountSettings(defaultVisibility = PostVisibility.Public), AccountSettings()).account()
        assertEquals(null, visibility.defaultVisibility)
        // 翻訳先の「端末の言語」も同じ
        val target = written(DeviceSettings(translation = TranslationSettings(targetLanguage = "en")), DeviceSettings()).device()
        assertEquals(null, target.translation.targetLanguage)
        // 翻訳の言語ごとの扱いは、空にした一覧と「まだ変えていない」(null) を分ける
        val emptied = written(DeviceSettings(), DeviceSettings(translation = TranslationSettings(languageModes = emptyMap())))
        assertEquals(emptyMap<String, TranslationMode>(), emptied.device().translation.languageModes)
        val untouched = mutablePreferencesOf().also {
            DeviceSettingsSchema.writeChanges(it, DeviceSettings(), DeviceSettings(translation = TranslationSettings(languageModes = emptyMap())))
            DeviceSettingsSchema.writeChanges(it, DeviceSettings(translation = TranslationSettings(languageModes = emptyMap())), DeviceSettings())
        }
        assertEquals(null, untouched.device().translation.languageModes)
    }

    @Test
    fun defaultOnConfirmationsAreStoredPerTarget() {
        // 既定でオンのものを切ったら、まとめたキーではなく個別のキーに書く
        val confirmations = DefaultConfirmations - ConfirmTarget.DeletePost
        val prefs = written(DeviceSettings(), DeviceSettings(confirmations = confirmations))
        assertEquals(setOf("confirm_delete_post"), prefs.keyNames())
        assertEquals(confirmations, prefs.device().confirmations)
        // まとめたキーだけを保存していた (追加より前の) ユーザーにも既定値でオンになる
        val legacy = mutablePreferencesOf(stringSetPreferencesKey("confirm_targets") to setOf("Post"))
        assertEquals(setOf(ConfirmTarget.Post) + DefaultConfirmations, legacy.device().confirmations)
    }

    @Test
    fun unreadableValuesFallBackToDefaultsPerField() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("notification_api") to "Something",
            stringPreferencesKey("avatar_shape") to "Hexagon",
            stringPreferencesKey("default_visibility") to "Direct",
            stringPreferencesKey("default_language") to "xx-unknown",
            intPreferencesKey("long_post_max_lines") to -3,
            floatPreferencesKey("surface_blur_tint_alpha") to 3f,
            floatPreferencesKey("surface_blur_radius_dp") to 100f,
            floatPreferencesKey("ui_scale") to 3f,
            floatPreferencesKey("font_scale") to 0f,
            stringSetPreferencesKey("confirm_targets") to setOf("Boost", "Removed"),
            stringSetPreferencesKey("hidden_post_menu_items") to setOf("Block", "Removed"),
            booleanPreferencesKey("stream_local") to false,
            floatPreferencesKey("predictive_back_max_drag_fraction") to 0f,
            intPreferencesKey("predictive_back_transition_millis") to 0,
        )
        val settings = prefs.device()
        val account = prefs.account()
        assertEquals(NotificationApi.Grouped, account.notificationApi)
        // ダイレクトは既定にできない
        assertEquals(null, account.defaultVisibility)
        // 知らない言語は Auto
        assertEquals(null, account.defaultLanguage)
        assertEquals(DisplaySettings(), settings.display)
        assertEquals(PredictiveBackSettings(), settings.predictiveBack)
        // 個別のキーで持つもの (既定でオン) はキーが無いので既定値
        assertEquals(setOf(ConfirmTarget.Boost) + DefaultConfirmations, settings.confirmations)
        // 知らない項目は捨てる
        assertEquals(setOf(PostMenuItem.Block), settings.hiddenPostMenuItems)
        // 読める項目はそのまま
        assertEquals(false, account.streaming.local)
    }
}
