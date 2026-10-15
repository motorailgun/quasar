package es.aria_on_the_planet.quasar.ui.component.image

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.withSign

/**
 * BlurHash (https://blurha.sh) のデコーダ。
 * [width] x [height] の ARGB ピクセル配列を返す。不正な文字列なら `null`。
 */
internal fun decodeBlurHash(blurHash: String, width: Int, height: Int, punch: Float = 1f): IntArray? {
    if (blurHash.length < 6) return null
    val sizeFlag = decode83(blurHash, 0, 1) ?: return null
    val numY = sizeFlag / 9 + 1
    val numX = sizeFlag % 9 + 1
    if (blurHash.length != 4 + 2 * numX * numY) return null

    val quantMax = decode83(blurHash, 1, 2) ?: return null
    val maxValue = (quantMax + 1) / 166f * punch
    val colors = Array(numX * numY) { i ->
        if (i == 0) {
            decodeDc(decode83(blurHash, 2, 6) ?: return null)
        } else {
            decodeAc(decode83(blurHash, 4 + i * 2, 6 + i * 2) ?: return null, maxValue)
        }
    }

    val cosX = Array(numX) { i -> FloatArray(width) { x -> cos(PI * x * i / width).toFloat() } }
    val cosY = Array(numY) { j -> FloatArray(height) { y -> cos(PI * y * j / height).toFloat() } }
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var r = 0f
            var g = 0f
            var b = 0f
            for (j in 0 until numY) {
                for (i in 0 until numX) {
                    val basis = cosX[i][x] * cosY[j][y]
                    val color = colors[i + j * numX]
                    r += color[0] * basis
                    g += color[1] * basis
                    b += color[2] * basis
                }
            }
            pixels[x + width * y] = (0xFF shl 24) or
                (linearToSrgb(r) shl 16) or
                (linearToSrgb(g) shl 8) or
                linearToSrgb(b)
        }
    }
    return pixels
}

private const val Base83Chars =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

private fun decode83(str: String, from: Int, to: Int): Int? {
    var result = 0
    for (i in from until to) {
        val index = Base83Chars.indexOf(str[i])
        if (index == -1) return null
        result = result * 83 + index
    }
    return result
}

private fun decodeDc(value: Int) = floatArrayOf(
    srgbToLinear(value shr 16),
    srgbToLinear((value shr 8) and 255),
    srgbToLinear(value and 255),
)

private fun decodeAc(value: Int, maxValue: Float) = floatArrayOf(
    signedPow2((value / (19 * 19) - 9) / 9f) * maxValue,
    signedPow2((value / 19 % 19 - 9) / 9f) * maxValue,
    signedPow2((value % 19 - 9) / 9f) * maxValue,
)

private fun signedPow2(value: Float) = (value * value).withSign(value)

private fun srgbToLinear(value: Int): Float {
    val v = value / 255f
    return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
}

private fun linearToSrgb(value: Float): Int {
    val v = value.coerceIn(0f, 1f)
    val srgb = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1 / 2.4f) - 0.055f
    return (srgb * 255 + 0.5f).toInt().coerceIn(0, 255)
}
