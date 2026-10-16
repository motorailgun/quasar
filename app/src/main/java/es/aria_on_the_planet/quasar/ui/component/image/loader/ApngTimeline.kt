package es.aria_on_the_planet.quasar.ui.component.image.loader

/**
 * APNG の再生位置の計算 (純粋関数)。経過時間から、表示するコマと次にコマが変わるまでの時間を求める。
 *
 * @param frameDurations 各コマの表示時間 (ms)。[MinFrameDelayMillis] 以下は [DefaultFrameDelayMillis] として扱う
 *   (GIF と同じ慣習。0 や 1ms のコマで描き直し続けないため)。
 * @param loopCount 繰り返す回数。0 なら無限 (APNG の `num_plays`)。
 */
internal class ApngTimeline(frameDurations: IntArray, private val loopCount: Int) {
    private val durations = frameDurations.map { if (it <= MinFrameDelayMillis) DefaultFrameDelayMillis else it }
    private val loopDuration = durations.sumOf { it.toLong() }

    /** 表示するコマ。[untilNextMillis] は次にコマが変わるまでの時間で、再生が終わっていれば `null`。 */
    data class Frame(val index: Int, val untilNextMillis: Long?)

    fun frameAt(elapsedMillis: Long): Frame {
        if (durations.size < 2 || loopDuration <= 0) return Frame(0, null)
        val elapsed = elapsedMillis.coerceAtLeast(0)
        if (isFinished(elapsed)) return Frame(durations.lastIndex, null)
        var position = elapsed % loopDuration
        durations.forEachIndexed { index, duration ->
            if (position < duration) return Frame(index, duration - position)
            position -= duration
        }
        return Frame(durations.lastIndex, null)
    }

    /** [loopCount] 回の再生が終わったか。無限なら常に `false`。 */
    fun isFinished(elapsedMillis: Long): Boolean = loopCount > 0 && elapsedMillis >= loopDuration * loopCount
}

internal const val MinFrameDelayMillis = 10
internal const val DefaultFrameDelayMillis = 100
