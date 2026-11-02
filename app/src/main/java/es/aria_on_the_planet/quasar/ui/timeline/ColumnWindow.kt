package es.aria_on_the_planet.quasar.ui.timeline

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.settings.ColumnLayout

/**
 * タイムラインのカラムを横に並べるときの計算 (`docs/tablet-layout.md`)。見えているカラムは「左端 + 数」の区間で表す。
 */

/** これ以上の幅なら 2 カラム (Material 3 の Window size class の Expanded)。 */
internal val DoubleColumnMinWidth: Dp = 840.dp

/** 同時に見せるカラムの数。[width] はタイムライン画面の幅。 */
internal fun visibleColumnCount(layout: ColumnLayout, width: Dp): Int = when (layout) {
    ColumnLayout.Single -> 1
    ColumnLayout.Double -> 2
    ColumnLayout.Auto -> if (width >= DoubleColumnMinWidth) 2 else 1
}

/**
 * タブ [tapped] を押したときの左端のカラム。見えていれば [start] のまま、見えていなければずらす量が最小になる位置
 * (左に外れていれば左端、右に外れていれば右端に来るように)。[count] はカラムの総数。
 */
internal fun startColumnFor(tapped: Int, start: Int, visible: Int, count: Int): Int {
    val next = when {
        tapped < start -> tapped
        tapped >= start + visible -> tapped - visible + 1
        else -> start
    }
    return next.coerceIn(0, (count - visible).coerceAtLeast(0))
}

/**
 * タブ [index] が見えている区間にどれだけ近いか (区間の中なら 1、1 カラム分以上離れたら 0)。[position] は左端の
 * ページ位置 (スワイプ中は小数)。タブの幅をスワイプに追従させるのに使う。
 */
internal fun columnCloseness(index: Int, position: Float, visible: Int): Float {
    val distance = maxOf(0f, position - index, index - (position + visible - 1))
    return (1f - distance).coerceIn(0f, 1f)
}
