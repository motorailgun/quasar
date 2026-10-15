package es.aria_on_the_planet.quasar.ui.component.gesture

import kotlin.math.abs
import kotlin.math.sign

/**
 * ゴムのような引っかかり。指の距離 [offset] に対して、動き始めは [coefficient] (既定で半分ほど) 動き、
 * 遠くなるほど動かなくなり、どれだけ動かしても [maxOffset] を超えない。
 */
fun rubberBand(offset: Float, maxOffset: Float, coefficient: Float = RubberBandCoefficient): Float {
    if (maxOffset <= 0f) return 0f
    val distance = abs(offset)
    return sign(offset) * maxOffset * (1f - 1f / (distance * coefficient / maxOffset + 1f))
}

/** [rubberBand] の逆。見た目の移動量 [visual] になる指の距離。[maxOffset] 以上は届かないので手前で打ち切る。 */
fun rubberBandInverse(visual: Float, maxOffset: Float, coefficient: Float = RubberBandCoefficient): Float {
    if (maxOffset <= 0f) return 0f
    val distance = abs(visual).coerceAtMost(maxOffset * MaxInverseFraction)
    return sign(visual) * maxOffset / coefficient * distance / (maxOffset - distance)
}

/** 指の距離 [offset] での [rubberBand] の傾き (指の速さを見た目の速さに直す)。 */
fun rubberBandSlope(offset: Float, maxOffset: Float, coefficient: Float = RubberBandCoefficient): Float {
    if (maxOffset <= 0f) return 0f
    val x = abs(offset) * coefficient / maxOffset + 1f
    return coefficient / (x * x)
}

/** 指の距離に対する、動き始めの見た目の移動量の割合。小さいほど強く引っかかる。 */
private const val RubberBandCoefficient = 0.55f
private const val MaxInverseFraction = 0.99f
