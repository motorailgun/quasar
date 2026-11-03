package es.aria_on_the_planet.quasar.ui.component.scaffold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols

/**
 * 「戻る」で詳細のパネル (`DetailPanel`) が閉じる画面か (パネルの最初の画面)。そうなら戻るボタンを ← ではなく ✕ にする。
 */
val LocalBackClosesPanel = compositionLocalOf { false }

/** 画面のトップバーの戻るボタンのアイコン。 */
@Composable
fun backIcon(): ImageVector = if (LocalBackClosesPanel.current) Symbols.Outlined.Close else Symbols.Outlined.ArrowBack

/** 同じボタンの説明。 */
@Composable
fun backLabel(): String = stringResource(if (LocalBackClosesPanel.current) R.string.close else R.string.back)
