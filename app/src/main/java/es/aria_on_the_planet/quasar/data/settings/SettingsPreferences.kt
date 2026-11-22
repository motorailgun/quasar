package es.aria_on_the_planet.quasar.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import es.aria_on_the_planet.quasar.domain.model.PostVisibility
import es.aria_on_the_planet.quasar.domain.model.settings.AccountSettings
import es.aria_on_the_planet.quasar.domain.model.settings.AvatarShape
import es.aria_on_the_planet.quasar.domain.model.settings.ColumnLayout
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.domain.model.settings.DefaultConfirmations
import es.aria_on_the_planet.quasar.domain.model.settings.DefaultVisibilityChoices
import es.aria_on_the_planet.quasar.domain.model.settings.DeviceSettings
import es.aria_on_the_planet.quasar.domain.model.settings.DisplaySettings
import es.aria_on_the_planet.quasar.domain.model.settings.NotificationApi
import es.aria_on_the_planet.quasar.domain.model.settings.PostMenuItem
import es.aria_on_the_planet.quasar.domain.model.settings.PredictiveBackSettings
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationMode

/**
 * 設定 ([DeviceSettings] / [AccountSettings]) と DataStore (Preferences) の対応 ([DeviceSettingsSchema] /
 * [AccountSettingsSchema])。項目ごとに 1 つのキーを持つ。
 *
 * * キーが無ければ既定値 (コードの既定値を変えれば、変えていないユーザーにはそれが効く)。
 * * 書き込みは変わった項目だけ ([SettingsSchema.writeChanges])。ユーザーが選んだ値だけが保存される。
 * * `null` を値として持つ項目は、キーの削除 (= 既定値) と区別するために番兵値で保存する。
 * * 読めない値 (知らない列挙値・範囲外) はその項目だけ既定値にする。
 * * 端末共通とアカウントごとはファイルが別で、キーの名前は分ける前 (1 つのファイルだった頃) のまま。分ける前のファイルは
 *   端末共通のファイルとして読み、アカウントごとの項目は移す ([withLegacyAccountSettings] / [withoutAccountSettings])。
 */
class SettingsSchema<S> internal constructor(
    private val defaults: S,
    private val fields: List<SettingField<S, *, *>>,
) {
    /** 保存した値。無い・読めない項目は既定値。 */
    fun read(prefs: Preferences): S = fields.fold(defaults) { settings, field -> field.read(prefs, settings) }

    /** [before] から [after] で変わった項目を [prefs] に書き込む。 */
    fun writeChanges(prefs: MutablePreferences, before: S, after: S) {
        fields.forEach { it.writeIfChanged(prefs, before, after) }
    }

    /** この設定のキー。 */
    internal val keys: List<Preferences.Key<*>> get() = fields.map { it.key }
}

/** 端末共通の設定。今の `settings` のファイル。 */
val DeviceSettingsSchema = SettingsSchema(DeviceSettings(), DeviceSettingFields())

/** アカウントごとの設定。アカウントごとのファイル (`settings_<AccountKey>`)。 */
val AccountSettingsSchema = SettingsSchema(AccountSettings(), AccountSettingFields())

/** アカウントごとの項目があるか (分ける前のファイル = 今の端末共通のファイルに残っているか)。 */
fun Preferences.hasAccountSettings(): Boolean = AccountSettingsSchema.keys.any { contains(it) }

/**
 * アカウントごとの設定 (このファイル) に、分ける前のファイル ([device]) に残っているアカウントごとの項目を写したもの。
 * 既にこちらにある項目は上書きしない (移す途中で終わって、やり直したときに後から変えた値を戻さない)。
 */
fun Preferences.withLegacyAccountSettings(device: Preferences): Preferences {
    val result = toMutablePreferences()
    AccountSettingsSchema.keys.forEach { key -> copyIfMissing(key, device, result) }
    return result.toPreferences()
}

/** アカウントごとの項目を除いたもの (移し終えた端末共通のファイル)。 */
fun Preferences.withoutAccountSettings(): Preferences =
    toMutablePreferences().apply { AccountSettingsSchema.keys.forEach { remove(it) } }.toPreferences()

private fun <T> copyIfMissing(key: Preferences.Key<T>, from: Preferences, to: MutablePreferences) {
    if (key in to) return
    from[key]?.let { to[key] = it }
}

/** 読み取った値。`null` 自体も値になりうる項目があるので、「読めない」(= `null` の [Decoded]) と区別するために包む。 */
internal class Decoded<T>(val value: T)

/** 設定 ([S]) の 1 項目。[decode] が `null` を返したら (読めない値) 既定値のまま。 */
internal class SettingField<S, T, P : Any>(
    val key: Preferences.Key<P>,
    val get: (S) -> T,
    val set: (S, T) -> S,
    val encode: (T) -> P,
    val decode: (P) -> Decoded<T>?,
) {
    fun read(prefs: Preferences, settings: S): S {
        val stored = prefs[key] ?: return settings
        val decoded = decode(stored) ?: return settings
        return set(settings, decoded.value)
    }

    fun writeIfChanged(prefs: MutablePreferences, before: S, after: S) {
        val value = get(after)
        if (value != get(before)) prefs[key] = encode(value)
    }
}

private fun <S> boolean(name: String, get: (S) -> Boolean, set: (S, Boolean) -> S) =
    SettingField(booleanPreferencesKey(name), get, set, encode = { it }, decode = { Decoded(it) })

private fun display(name: String, get: (DisplaySettings) -> Boolean, set: DisplaySettings.(Boolean) -> DisplaySettings) =
    boolean<DeviceSettings>(name, { get(it.display) }, { s, v -> s.copy(display = s.display.set(v)) })

/** `null` = 無効 / 上限なし を 0 で保存する正の整数。 */
private fun <S> optionalPositiveInt(name: String, get: (S) -> Int?, set: (S, Int?) -> S) =
    SettingField(
        intPreferencesKey(name), get, set,
        encode = { it ?: 0 },
        decode = { stored -> if (stored == 0) Decoded(null) else stored.takeIf { it > 0 }?.let(::Decoded) },
    )

/** 確認する操作のうち 1 つ ([target]) を、するか (`true`) で保存する。 */
private fun confirm(name: String, target: ConfirmTarget) =
    boolean<DeviceSettings>(name, { target in it.confirmations }) { s, v ->
        s.copy(confirmations = if (v) s.confirmations + target else s.confirmations - target)
    }

/** [range] の中だけ読める小数。 */
private fun <S> float(
    name: String,
    range: ClosedFloatingPointRange<Float>,
    get: (S) -> Float,
    set: (S, Float) -> S,
) = SettingField(
    floatPreferencesKey(name), get, set,
    encode = { it },
    decode = { stored -> stored.takeIf { it in range }?.let(::Decoded) },
)

private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }

private const val AccountDefaultVisibility = "account"

/** 翻訳先の「端末の言語」(`null`)。 */
private const val DeviceTranslationTarget = "device"

/** 翻訳の言語ごとの扱いの「まだ変えていない」(`null`。端末の言語を出さない一覧)。空の一覧とは別。 */
private const val DeviceLanguageModes = "device"

/** 端末共通の項目。 */
private fun DeviceSettingFields(): List<SettingField<DeviceSettings, *, *>> = listOf(
    optionalPositiveInt(
        "long_post_max_lines",
        { it.display.longPostMaxLines },
        { s, v -> s.copy(display = s.display.copy(longPostMaxLines = v)) },
    ),
    display("surface_blur", { it.surfaceBlur }) { copy(surfaceBlur = it) },
    display("menu_blur", { it.menuBlur }) { copy(menuBlur = it) },
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
    float(
        "ui_scale",
        DisplaySettings.ScaleRange,
        { it.display.uiScale },
        { s, v -> s.copy(display = s.display.copy(uiScale = v)) },
    ),
    float(
        "font_scale",
        DisplaySettings.ScaleRange,
        { it.display.fontScale },
        { s, v -> s.copy(display = s.display.copy(fontScale = v)) },
    ),
    SettingField(
        stringPreferencesKey("avatar_shape"),
        { it.display.avatarShape },
        { s, v -> s.copy(display = s.display.copy(avatarShape = v)) },
        encode = { it.name },
        decode = { stored -> enumOrNull<AvatarShape>(stored)?.let(::Decoded) },
    ),
    SettingField(
        stringPreferencesKey("column_layout"),
        { it.display.columnLayout },
        { s, v -> s.copy(display = s.display.copy(columnLayout = v)) },
        encode = { it.name },
        decode = { stored -> enumOrNull<ColumnLayout>(stored)?.let(::Decoded) },
    ),
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
    confirm("confirm_reject_follow_request", ConfirmTarget.RejectFollowRequest),
    SettingField(
        stringSetPreferencesKey("hidden_post_menu_items"),
        { it.hiddenPostMenuItems },
        { s, v -> s.copy(hiddenPostMenuItems = v) },
        encode = { items -> items.mapTo(HashSet()) { it.name } },
        // 知らない名前 (将来消した項目等) は捨てる
        decode = { names -> Decoded(names.mapNotNullTo(HashSet()) { enumOrNull<PostMenuItem>(it) }) },
    ),
    boolean<DeviceSettings>("translation_enabled", { it.translation.enabled }) { s, v -> s.copy(translation = s.translation.copy(enabled = v)) },
    SettingField(
        stringPreferencesKey("translation_default_mode"),
        { it.translation.defaultMode },
        { s, v -> s.copy(translation = s.translation.copy(defaultMode = v)) },
        encode = { it.name },
        decode = { stored -> enumOrNull<TranslationMode>(stored)?.let(::Decoded) },
    ),
    SettingField(
        stringSetPreferencesKey("translation_language_modes"),
        { it.translation.languageModes },
        { s, v -> s.copy(translation = s.translation.copy(languageModes = v)) },
        encode = { modes -> modes?.mapTo(HashSet()) { (code, mode) -> "$code=${mode.name}" } ?: setOf(DeviceLanguageModes) },
        decode = { stored ->
            if (stored == setOf(DeviceLanguageModes)) {
                Decoded(null)
            } else {
                // 読めない項目だけ飛ばす
                Decoded(
                    stored.mapNotNull { entry ->
                        val code = entry.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        enumOrNull<TranslationMode>(entry.substringAfter('='))?.let { code to it }
                    }.toMap(),
                )
            }
        },
    ),
    SettingField(
        stringPreferencesKey("translation_target"),
        { it.translation.targetLanguage },
        { s, v -> s.copy(translation = s.translation.copy(targetLanguage = v)) },
        encode = { it ?: DeviceTranslationTarget },
        decode = { stored -> Decoded(stored.takeIf { it != DeviceTranslationTarget && it.isNotBlank() }) },
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

/** アカウントごとの項目。 */
private fun AccountSettingFields(): List<SettingField<AccountSettings, *, *>> = listOf(
    SettingField(
        stringPreferencesKey("notification_api"),
        { it.notificationApi },
        { s, v -> s.copy(notificationApi = v) },
        encode = { it.name },
        decode = { stored -> enumOrNull<NotificationApi>(stored)?.let(::Decoded) },
    ),
    boolean<AccountSettings>("stream_home", { it.streaming.home }) { s, v -> s.copy(streaming = s.streaming.copy(home = v)) },
    boolean<AccountSettings>("stream_notifications", { it.streaming.notifications }) { s, v ->
        s.copy(streaming = s.streaming.copy(notifications = v))
    },
    boolean<AccountSettings>("stream_local", { it.streaming.local }) { s, v -> s.copy(streaming = s.streaming.copy(local = v)) },
    boolean<AccountSettings>("stream_global", { it.streaming.global }) { s, v -> s.copy(streaming = s.streaming.copy(global = v)) },
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
    boolean<AccountSettings>("compress_oversized_images", { it.compressOversizedImages }) { s, v -> s.copy(compressOversizedImages = v) },
    boolean<AccountSettings>("strip_image_metadata", { it.stripImageMetadata }) { s, v -> s.copy(stripImageMetadata = v) },
)
