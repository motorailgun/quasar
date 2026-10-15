package es.aria_on_the_planet.quasar.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.MaterialColors

/**
 * Material 3 の Custom colors の 1 色分の役割 (color / onColor / colorContainer / onColorContainer)。
 * https://m3.material.io/styles/color/advanced/define-new-colors
 */
@Immutable
data class CustomColorRoles(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
)

/** アプリ独自の意味を持つ色。テーマの [ColorScheme] とは別に [MaterialTheme.extendedColors] で参照する。 */
@Immutable
data class ExtendedColors(
    /** お気に入り (黄)。 */
    val favourite: CustomColorRoles,
    /** ブースト (緑)。 */
    val reblog: CustomColorRoles,
    /** フォロー (青)。 */
    val follow: CustomColorRoles,
)

/** 元にする色。dynamic color の primary に向けて harmonize してから使う。 */
private val FavouriteSeed = Color(0xFFFFC107)
private val ReblogSeed = Color(0xFF4CAF50)
private val FollowSeed = Color(0xFF2196F3)

/**
 * [seed] を [scheme] の primary に馴染むよう色相を寄せ (harmonize)、ライト/ダークに応じた
 * トーンの役割色を作る (MDC の MaterialColors.harmonize / getColorRoles)。
 */
private fun customColorRoles(seed: Color, scheme: ColorScheme, isLight: Boolean): CustomColorRoles {
    val harmonized = MaterialColors.harmonize(seed.toArgb(), scheme.primary.toArgb())
    val roles = MaterialColors.getColorRoles(harmonized, isLight)
    return CustomColorRoles(
        color = Color(roles.accent),
        onColor = Color(roles.onAccent),
        container = Color(roles.accentContainer),
        onContainer = Color(roles.onAccentContainer),
    )
}

internal fun extendedColorsFor(scheme: ColorScheme, isLight: Boolean) = ExtendedColors(
    favourite = customColorRoles(FavouriteSeed, scheme, isLight),
    reblog = customColorRoles(ReblogSeed, scheme, isLight),
    follow = customColorRoles(FollowSeed, scheme, isLight),
)

internal val LocalExtendedColors = staticCompositionLocalOf<ExtendedColors> {
    error("ExtendedColors is not provided. Wrap content in QuasarTheme.")
}

/** アプリ独自の色 (お気に入り・ブースト等)。 */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
