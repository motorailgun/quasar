package es.aria_on_the_planet.quasar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuasarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // minSdk 33 なので dynamic color は常に利用可能
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
