package es.aria_on_the_planet.quasar.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.settings.AppSettings
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.domain.model.settings.DisplaySettings
import es.aria_on_the_planet.quasar.domain.model.settings.NotificationApi
import es.aria_on_the_planet.quasar.domain.model.settings.StreamingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPreferencesTest {
    /** 全項目を既定値から変えた設定。 */
    private val changed = AppSettings(
        display = DisplaySettings(
            longPostMaxLines = null,
            surfaceBlurRadiusDp = 10f,
            surfaceBlurTintAlpha = 0.5f,
            animateImages = false,
            animatePostEmojis = false,
            animateOtherEmojis = false,
            animateAvatars = false,
            emojiAvatarAnimationMaxFps = null,
        ),
        notificationApi = NotificationApi.Ungrouped,
        streaming = StreamingSettings(home = false, notifications = false, local = false, global = true),
        confirmations = setOf(ConfirmTarget.Post, ConfirmTarget.Unboost),
        defaultVisibility = PostVisibility.Private,
    )

    private fun written(before: AppSettings, after: AppSettings) =
        mutablePreferencesOf().also { writeSettingsChanges(it, before, after) }

    @Test
    fun emptyPreferencesAreDefaults() {
        assertEquals(AppSettings(), emptyPreferences().toAppSettings())
    }

    @Test
    fun everyFieldRoundTrips() {
        assertEquals(changed, written(AppSettings(), changed).toAppSettings())
        // 既定値に戻した (null を含む) 値も読み戻せる
        val back = mutablePreferencesOf().also {
            writeSettingsChanges(it, AppSettings(), changed)
            writeSettingsChanges(it, changed, AppSettings())
        }
        assertEquals(AppSettings(), back.toAppSettings())
    }

    @Test
    fun onlyChangedFieldsAreWritten() {
        val prefs = written(AppSettings(), AppSettings(streaming = StreamingSettings(global = true)))
        assertEquals(setOf("stream_global"), prefs.asMap().keys.map { it.name }.toSet())
        assertTrue(written(AppSettings(), AppSettings()).asMap().isEmpty())
    }

    @Test
    fun nullValuesAreDistinguishedFromMissingKeys() {
        // 既定値が null でない項目を null (無効・上限なし) にしたら、キーが無いとき (既定値) と区別して読める
        val settings = written(
            AppSettings(),
            AppSettings(display = DisplaySettings(longPostMaxLines = null, emojiAvatarAnimationMaxFps = null)),
        ).toAppSettings()
        assertEquals(null, settings.display.longPostMaxLines)
        assertEquals(null, settings.display.emojiAvatarAnimationMaxFps)
        // 既定値が null の項目 (アカウントの既定) を明示的に選び直した場合も null
        val visibility = written(AppSettings(defaultVisibility = PostVisibility.Public), AppSettings()).toAppSettings()
        assertEquals(null, visibility.defaultVisibility)
    }

    @Test
    fun unreadableValuesFallBackToDefaultsPerField() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("notification_api") to "Something",
            stringPreferencesKey("default_visibility") to "Direct",
            intPreferencesKey("long_post_max_lines") to -3,
            floatPreferencesKey("surface_blur_tint_alpha") to 3f,
            floatPreferencesKey("surface_blur_radius_dp") to 100f,
            stringSetPreferencesKey("confirm_targets") to setOf("Boost", "Removed"),
            booleanPreferencesKey("stream_local") to false,
        )
        val settings = prefs.toAppSettings()
        assertEquals(NotificationApi.Grouped, settings.notificationApi)
        // ダイレクトは既定にできない
        assertEquals(null, settings.defaultVisibility)
        assertEquals(DisplaySettings(), settings.display)
        assertEquals(setOf(ConfirmTarget.Boost), settings.confirmations)
        // 読める項目はそのまま
        assertEquals(false, settings.streaming.local)
    }
}
