package es.aria_on_the_planet.quasar.data.cache

/**
 * キャッシュの書き込みの版。リクエストを出す直前に [next] で取り、その結果を書くときに渡す。
 * 版は Room に残るので、プロセスをまたいでも大小が保たれるよう実時刻 (ミリ秒) を基にし、プロセスの中では必ず増やす
 * (同じミリ秒・時計の巻き戻りでも前より大きい)。
 */
class VersionClock(private val now: () -> Long = System::currentTimeMillis) {
    private var last = 0L

    @Synchronized
    fun next(): Long = maxOf(now(), last + 1).also { last = it }
}
