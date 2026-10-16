package es.aria_on_the_planet.quasar.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * 投稿の上限 (サーバ設定)。`GET /api/v2/instance` の `configuration` から取る。
 * 既定値は Mastodon の標準設定で、取得前・取得失敗時に使う (最終的な判定はサーバが行う)。
 */
data class ComposeLimits(
    val maxCharacters: Int = 500,
    /** URL は長さに関わらずこの文字数として数える。 */
    val charactersPerUrl: Int = 23,
    val maxMediaAttachments: Int = 4,
    val maxPollOptions: Int = 4,
    val maxPollOptionCharacters: Int = 50,
    val minPollExpiration: Duration = 5.minutes,
    val maxPollExpiration: Duration = 30.days,
) {
    /** 投票期間の選択肢 (Mastodon の Web UI と同じ) のうち、上限の範囲内のもの。 */
    val pollDurations: List<Duration>
        get() = PollDurations.filter { it in minPollExpiration..maxPollExpiration }
            .ifEmpty { listOf(minPollExpiration) }

    private companion object {
        val PollDurations = listOf(5.minutes, 30.minutes, 60.minutes, 360.minutes, 1.days, 3.days, 7.days)
    }
}
