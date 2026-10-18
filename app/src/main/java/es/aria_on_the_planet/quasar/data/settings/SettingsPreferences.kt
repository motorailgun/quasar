package es.aria_on_the_planet.quasar.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.settings.AppSettings
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.domain.model.settings.DefaultConfirmations
import es.aria_on_the_planet.quasar.domain.model.settings.DefaultVisibilityChoices
import es.aria_on_the_planet.quasar.domain.model.settings.DisplaySettings
import es.aria_on_the_planet.quasar.domain.model.settings.NotificationApi
import es.aria_on_the_planet.quasar.domain.model.settings.PredictiveBackSettings

/**
 * [AppSettings] と DataStore (Preferences) の対応。項目ごとに 1 つのキーを持つ。
 *
 * * キーが無ければ既定値 (コードの既定値を変えれば、変えていないユーザーにはそれが効く)。
 * * 書き込みは変わった項目だけ ([writeSettingsChanges])。ユーザーが選んだ値だけが保存される。
 * * `null` を値として持つ項目は、キーの削除 (= 既定値) と区別するために番兵値で保存する。
 * * 読めない値 (知らない列挙値・範囲外) はその項目だけ既定値にする。
 */
fun Preferences.toAppSettings(): AppSettings =
    SettingFields.fold(AppSettings()) { settings, field -> field.read(this, settings) }

/** [before] から [after] で変わった項目を [prefs] に書き込む。 */
fun writeSettingsChanges(prefs: MutablePreferences, before: AppSettings, after: AppSettings) {
    SettingFields.forEach { it.writeIfChanged(prefs, before, after) }
}

/** 読み取った値。`null` 自体も値になりうる項目があるので、「読めない」(= `null` の [Decoded]) と区別するために包む。 */
private class Decoded<T>(val value: T)

/** 設定の 1 項目。[decode] が `null` を返したら (読めない値) 既定値のまま。 */
private class SettingField<T, P : Any>(
    val key: Preferences.Key<P>,
    val get: (AppSettings) -> T,
    val set: (AppSettings, T) -> AppSettings,
    val encode: (T) -> P,
    val decode: (P) -> Decoded<T>?,
) {
    fun read(prefs: Preferences, settings: AppSettings): AppSettings {
        val stored = prefs[key] ?: return settings
        val decoded = decode(stored) ?: return settings
        return set(settings, decoded.value)
    }

    fun writeIfChanged(prefs: MutablePreferences, before: AppSettings, after: AppSettings) {
        val value = get(after)
        if (value != get(before)) prefs[key] = encode(value)
    }
}

private fun boolean(name: String, get: (AppSettings) -> Boolean, set: (AppSettings, Boolean) -> AppSettings) =
    SettingField(booleanPreferencesKey(name), get, set, encode = { it }, decode = { Decoded(it) })

private fun display(name: String, get: (DisplaySettings) -> Boolean, set: DisplaySettings.(Boolean) -> DisplaySettings) =
    boolean(name, { get(it.display) }, { s, v -> s.copy(display = s.display.set(v)) })

/** `null` = 無効 / 上限なし を 0 で保存する正の整数。 */
private fun optionalPositiveInt(name: String, get: (AppSettings) -> Int?, set: (AppSettings, Int?) -> AppSettings) =
    SettingField(
        intPreferencesKey(name), get, set,
        encode = { it ?: 0 },
        decode = { stored -> if (stored == 0) Decoded(null) else stored.takeIf { it > 0 }?.let(::Decoded) },
    )

/** 確認する操作のうち 1 つ ([target]) を、するか (`true`) で保存する。 */
private fun confirm(name: String, target: ConfirmTarget) =
    boolean(name, { target in it.confirmations }) { s, v ->
        s.copy(confirmations = if (v) s.confirmations + target else s.confirmations - target)
    }

/** [range] の中だけ読める小数。 */
private fun float(
    name: String,
    range: ClosedFloatingPointRange<Float>,
    get: (AppSettings) -> Float,
    set: (AppSettings, Float) -> AppSettings,
) = SettingField(
    floatPreferencesKey(name), get, set,
    encode = { it },
    decode = { stored -> stored.takeIf { it in range }?.let(::Decoded) },
)

private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }

private const val AccountDefaultVisibility = "account"

private val SettingFields: List<SettingField<*, *>> = listOf(
    optionalPositiveInt(
        "long_post_max_lines",
        { it.display.longPostMaxLines },
        { s, v -> s.copy(display = s.display.copy(longPostMaxLines = v)) },
    ),
    display("surface_blur", { it.surfaceBlur }) { copy(surfaceBlur = it) },
    SettingField(
        floatPreferencesKey("surface_blur_radius_dp"),
        { it.display.surfaceBlurRadiusDp },
        { s, v -> s.copy(display = s.display.copy(surfaceBlurRadiusDp = v)) },
        encode = { it },
        decode = { stored -> stored.takeIf { it in 0f..DisplaySettings.MaxSurfaceBlurRadiusDp }?.let(::Decoded) },
    ),
    SettingField(
        floatPreferencesKey("surface_blur_tint_alpha"),
        { it.display.surfaceBlurTintAlpha },
        { s, v -> s.copy(display = s.display.copy(surfaceBlurTintAlpha = v)) },
        encode = { it },
        decode = { stored -> stored.takeIf { it in 0f..1f }?.let(::Decoded) },
    ),
    display("animate_images", { it.animateImages }) { copy(animateImages = it) },
    display("animate_post_emojis", { it.animatePostEmojis }) { copy(animatePostEmojis = it) },
    display("animate_other_emojis", { it.animateOtherEmojis }) { copy(animateOtherEmojis = it) },
    display("animate_avatars", { it.animateAvatars }) { copy(animateAvatars = it) },
    optionalPositiveInt(
        "emoji_avatar_animation_max_fps",
        { it.display.emojiAvatarAnimationMaxFps },
        { s, v -> s.copy(display = s.display.copy(emojiAvatarAnimationMaxFps = v)) },
    ),
    SettingField(
        stringPreferencesKey("notification_api"),
        { it.notificationApi },
        { s, v -> s.copy(notificationApi = v) },
        encode = { it.name },
        decode = { stored -> enumOrNull<NotificationApi>(stored)?.let(::Decoded) },
    ),
    boolean("stream_home", { it.streaming.home }) { s, v -> s.copy(streaming = s.streaming.copy(home = v)) },
    boolean("stream_notifications", { it.streaming.notifications }) { s, v ->
        s.copy(streaming = s.streaming.copy(notifications = v))
    },
    boolean("stream_local", { it.streaming.local }) { s, v -> s.copy(streaming = s.streaming.copy(local = v)) },
    boolean("stream_global", { it.streaming.global }) { s, v -> s.copy(streaming = s.streaming.copy(global = v)) },
    // 確認する操作のうち、既定でオンのもの ([DefaultConfirmations]) は 1 つずつのキー、残りはまとめて 1 つのキー。
    // まとめたキーに入れると、追加より前に保存したユーザーにはオフで読めてしまうため
    SettingField(
        stringSetPreferencesKey("confirm_targets"),
        { it.confirmations - DefaultConfirmations },
        { s, v -> s.copy(confirmations = v + (s.confirmations intersect DefaultConfirmations)) },
        encode = { targets -> targets.mapTo(HashSet()) { it.name } },
        // 知らない名前 (将来消した項目等) と個別のキーで持つものは捨てる
        decode = { names ->
            Decoded(names.mapNotNullTo(HashSet()) { enumOrNull<ConfirmTarget>(it) } - DefaultConfirmations)
        },
    ),
    confirm("confirm_edit_post", ConfirmTarget.EditPost),
    confirm("confirm_delete_post", ConfirmTarget.DeletePost),
    SettingField(
        stringPreferencesKey("default_visibility"),
        { it.defaultVisibility },
        { s, v -> s.copy(defaultVisibility = v) },
        encode = { it?.name ?: AccountDefaultVisibility },
        decode = { stored ->
            if (stored == AccountDefaultVisibility) {
                Decoded(null)
            } else {
                // ダイレクト等、既定にできない値は読めない扱い
                enumOrNull<PostVisibility>(stored)?.takeIf { it in DefaultVisibilityChoices }?.let(::Decoded)
            }
        },
    ),
    float(
        "predictive_back_max_drag_fraction",
        PredictiveBackSettings.MaxDragFractionRange,
        { it.predictiveBack.maxDragFraction },
    ) { s, v ->
        s.copy(predictiveBack = s.predictiveBack.copy(maxDragFraction = v))
    },
    float(
        "predictive_back_drag_log_steepness",
        PredictiveBackSettings.DragLogSteepnessRange,
        { it.predictiveBack.dragLogSteepness },
    ) { s, v ->
        s.copy(predictiveBack = s.predictiveBack.copy(dragLogSteepness = v))
    },
    SettingField(
        intPreferencesKey("predictive_back_transition_millis"),
        { it.predictiveBack.transitionMillis },
        { s, v -> s.copy(predictiveBack = s.predictiveBack.copy(transitionMillis = v)) },
        encode = { it },
        decode = { stored -> stored.takeIf { it in PredictiveBackSettings.TransitionMillisRange }?.let(::Decoded) },
    ),
)
