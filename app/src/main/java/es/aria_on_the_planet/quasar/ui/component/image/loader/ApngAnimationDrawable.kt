package es.aria_on_the_planet.quasar.ui.component.image.loader

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import com.linecorp.apng.ApngDrawable

/**
 * APNG を再生する Drawable。描き直しはコマが変わる時刻にだけ予約する (`scheduleSelf`)。
 *
 * apng-drawable の [ApngDrawable] は再生 (`start()`) 中ずっと毎フレーム描き直し、そのたびにネイティブでコマを
 * ビットマップへ書き出すため、14fps の APNG でも画面の更新頻度 (120Hz 等) で CPU を使い続ける。そこで [frames] は
 * 再生させず (コールバックも付けない)、表示するコマの開始時刻へ `seekTo` してから描かせるだけにする
 * (`seekToFrame` は繰り返し回数が無限 (`loopCount = 0`) だと例外を投げるので使わない)。
 *
 * [frames] はこの Drawable 専用のもの (`ConstantState.newDrawable()`) を渡す。描画用のビットマップを 1 枚持つので、
 * 複数の Drawable で共有すると互いのコマで上書きされる。
 */
internal class ApngAnimationDrawable(private val frames: ApngDrawable) : Drawable(), Animatable {
    private val timeline = ApngTimeline(frames.frameDurations.toIntArray(), frames.loopCount)

    /** [frames] の時間軸での各コマの開始時刻。表示時間の補正 ([ApngTimeline]) をする前の値で求める。 */
    private val frameStarts = frames.frameDurations.runningFold(0L) { start, duration -> start + duration }
    private val nextFrame = Runnable { invalidateSelf() }

    private var running = false

    /** 経過時間 0 に当たる時刻 (`SystemClock.uptimeMillis`)。[running] の間だけ意味がある。 */
    private var startedAt = 0L

    /** 止めたときの経過時間。再開するとここから続ける。 */
    private var pausedElapsed = 0L

    private fun elapsed(): Long = if (running) SystemClock.uptimeMillis() - startedAt else pausedElapsed

    override fun draw(canvas: Canvas) {
        val elapsed = elapsed()
        val frame = timeline.frameAt(elapsed)
        if (frames.currentFrameIndex != frame.index) frames.seekTo(frameStarts[frame.index])
        frames.draw(canvas)
        if (!running) return
        val wait = frame.untilNextMillis
        unscheduleSelf(nextFrame)
        if (wait == null) {
            // 繰り返し回数を再生し終えた
            running = false
            pausedElapsed = elapsed
        } else {
            scheduleSelf(nextFrame, startedAt + elapsed + wait)
        }
    }

    override fun start() {
        if (running) return
        if (timeline.isFinished(pausedElapsed)) pausedElapsed = 0
        running = true
        startedAt = SystemClock.uptimeMillis() - pausedElapsed
        invalidateSelf()
    }

    override fun stop() {
        if (!running) return
        pausedElapsed = elapsed()
        running = false
        unscheduleSelf(nextFrame)
    }

    override fun isRunning(): Boolean = running

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (!visible) stop()
        return changed
    }

    override fun onBoundsChange(bounds: Rect) {
        frames.bounds = bounds
    }

    override fun getIntrinsicWidth(): Int = frames.intrinsicWidth

    override fun getIntrinsicHeight(): Int = frames.intrinsicHeight

    override fun setAlpha(alpha: Int) {
        frames.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        frames.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun setFilterBitmap(filter: Boolean) {
        frames.isFilterBitmap = filter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    /** インスタンスごとに専用の [frames] を持っているので、複製しなくてよい (Coil の Compose 側が描く前に呼ぶ)。 */
    override fun mutate(): Drawable = this
}
