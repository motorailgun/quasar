package es.aria_on_the_planet.quasar.ui.component.layout

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 画面の右側を覆っている浮かぶパネル (詳細のパネル、`DetailPanel`) の幅。画面の下端に浮かべる部品 (クイック投稿バー) が
 * 覆われていない所に寄せるのに使う。パネルの出入りに合わせて動く。パネルの中では 0。
 */
val LocalFloatingPanelInset = compositionLocalOf { 0.dp }
